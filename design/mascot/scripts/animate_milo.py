# animate_milo.py - the MilO mascot's reusable clips. Run it inside Blender after build_milo.py.
#
# Each clip is one action, starting on frame 0 at 24 frames a second, and is also stashed on its
# own muted NLA track so a glTF export writes every clip under its own name:
#
#   Idle         4 s, loops    standing, gently moving
#   Wave         2 s           the app opens
#   ThumbsUp     2 s           a trip was saved
#   Walk         1 s, loops    a trip is being recorded (walks on the spot)
#   Celebrate    3 s           a mileage goal was reached
#   Flex         4.5 s         shows off: both arms, one arm to each side, both again
#   Peace        2.75 s        holds up the peace sign with its left hand
#   RockOn       2.75 s        holds up the horns with its right hand, and nods to the beat
#   WaveUp       0.5 s         the right arm goes up, and stays up: it ends where WaveLoop begins
#   WaveLoop     0.5 s, loops  the arm held up, one wave of the hand; the website loops it
#   Moonwalk     0.58 s, loops glides backward on the spot, in his hat: one foot up on its toe, the other sliding
#   MoonwalkStop 1.83 s        skids round to a stop, brake lights on; hand to the hat, three hip thrusts
#   KickSpin     1.08 s        a kick, one fast turn on the spot, and down onto his toes, brake lights off
#   MoonwalkOn   0.42 s        from his toes back into the moonwalk
#   OneYear      5.42 s        a year of use: in his party cap, brings a party popper out from behind his back and fires it
#   Gauge_Speed  2 s           the speed needle alone, 0 to 240: seek to speed / 240 of its length
#   Gauge_RPM    2 s           the RPM needle alone, 0 to 8: seek to rpm / 8 of its length
#
# Every clip but the loops, WaveUp, the moonwalk's four and OneYear starts and ends in the same
# standing pose (NEUTRAL), so they can follow one another. The moonwalk's four follow one another
# in the order above, each starting where the last one ended, and the last ends where Moonwalk
# begins. OneYear starts in the standing pose, in his cap, and ends with both arms up.
# The needles move in every clip: each clip keys the rig's two sliders
# ("speed_kmh", "rpm") and the needle bones follow through their drivers.
#
# The brake lights on his back have a slider too, "brake", from 0 (unlit) to 1. None of these
# clips but the moonwalk's lights them, and no other keys the slider: a clip that does calls
# set_brake at every frame and key_brake after key_pose. It is not part of key_pose, which would
# add a curve to every clip there is. The same goes for the hat (the slider "hat" and the bone
# of that name) and for turning the whole mascot round (the bone "root"): the moonwalk's clips
# set and key them (key_clip), no other clip does, and reset_pose takes the hat off again.
# And the same for the party: the sliders "party_hat", "popper" and "pull" and the party cap's
# bone are set by set_party and keyed by key_party, in OneYear and in no other clip.
#
# One clip is not the mascot's: OneYear_Confetti, on the skeleton of the confetti
# (MilO_Confetti_Rig, in a collection of its own). It is written last, from where OneYear has
# the popper's mouth in the frame it goes off on (write_confetti).
#
# A clip is a function of time that sets the whole pose; it is keyed on every frame.

import random
import bpy
from math import radians, sin, cos, pi, exp
from mathutils import Vector, Matrix, Euler, Quaternion

FPS = 24
rig = bpy.data.objects["MilO_Rig"]
pb = rig.pose.bones
REST = {b.name: b.matrix_local.to_3x3() for b in rig.data.bones}

FINGER_BONES = ["finger_a.01", "finger_a.02", "finger_b.01", "finger_b.02", "finger_c.01", "finger_c.02"]
ROTATED = ["body", "eye_speedo", "eye_rpm"] + [
    "%s.%s" % (name, side)
    for side in ("L", "R")
    for name in ["foot", "upper_arm", "forearm", "hand", "thumb.01", "thumb.02"] + FINGER_BONES
]
MOVED = ["body", "foot.L", "foot.R"]
SCALED = ["body", "eye_speedo", "eye_rpm"]
IDLE_RPM = 0.9


# ---------------------------------------------------------------- small maths
def V(x, y, z):
    return Vector((x, y, z))


def clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def bump(t, a, b):
    """0 outside a..b, a smooth hill up to 1 in the middle."""
    return sin(pi * clamp((t - a) / (b - a))) ** 2


def hop(t, a, b):
    """The height of a jump from a to b: 0 at both ends, 1 at the top."""
    u = clamp((t - a) / (b - a))
    return 4 * u * (1 - u)


def overshoot(t):
    """0 to 1 with a small overshoot before it settles."""
    t = clamp(t) - 1
    return t * t * (2.70158 * t + 1.70158) + 1


def spring(u):
    """0 to 1, ringing a little as it arrives."""
    return 0.0 if u <= 0 else 1 - exp(-6 * u) * cos(14 * u)


def mirror(v):
    return Vector((-v.x, v.y, v.z))


def turn(v, axis, degrees):
    return Matrix.Rotation(radians(degrees), 3, axis) @ v


def swing(a, b, w):
    """From direction a towards direction b by the shortest way; w may run a little past 1."""
    a, b = a.normalized(), b.normalized()
    axis, angle = a.rotation_difference(b).to_axis_angle()
    return Quaternion(axis, angle * w) @ a


def frame_of(along, palm):
    y = along.normalized()
    z = palm - y * palm.dot(y)
    if z.length < 1e-4:
        z = y.orthogonal()
    z.normalize()
    return Matrix((y.cross(z), y, z)).transposed()


# ---------------------------------------------------------------- arm poses
# All written for the LEFT arm (+X is outwards); the right arm is the mirror image.
# "curl" is two angles for each of the three fingers; "thumb" is thumb.01's x, y, z and thumb.02's x.
# "spread" is one angle for each finger: how far it is turned sideways in the palm's plane, towards
# the thumb. The hand signs need it; every other pose leaves the fingers side by side.
def arm_pose(upper, upper_palm, fore, fore_palm, hand, hand_palm, curl, thumb, spread=(0.0, 0.0, 0.0)):
    return dict(upper=upper, upper_palm=upper_palm, fore=fore, fore_palm=fore_palm,
                hand=hand, hand_palm=hand_palm, curl=curl, thumb=thumb, spread=spread)


RELAXED = arm_pose(V(0.80, -0.05, -0.60), V(0.0, 0.5, -0.8), V(0.30, -0.22, -0.93), V(0.0, 1.0, 0.0),
                   V(0.16, -0.12, -0.98), V(-0.15, 1.0, 0.0), (14, 20, 20, 26, 28, 34), (12, 0, 0, 14))

_fingers = V(-0.22, -0.85, 0.46).normalized()
_thumb_side = (V(0, 0, 1) - _fingers * _fingers.z).normalized()
THUMBS_UP = arm_pose(V(0.80, -0.25, -0.55), V(-0.3, 0.0, -0.9), V(0.15, -0.70, 0.70), V(-1.0, 0.0, 0.0),
                     _fingers, _fingers.cross(_thumb_side),
                     (88, 94, 88, 94, 88, 94), (-5.7, 0, 36.8, 0))

CHEER = arm_pose(V(0.55, -0.08, 0.83), V(0.0, -1.0, 0.0), V(0.38, -0.05, 0.92), V(0.0, -1.0, 0.0),
                 V(0.34, -0.05, 0.94), V(0.0, -1.0, 0.0), (2, 2, 0, 0, 2, 2), (0, 0, 0, 0))

# A fist, and the thumb laid across it.
FIST = (86, 92, 88, 94, 86, 92)
THUMB_ACROSS = (112, 0, 50, 62)
# Flexing: the upper arm out and up, the forearm up, the fist beside the top of the head and
# clear of it, its fingers to the viewer. The arms are short and the hands large: a fist turned
# in towards the head, as a real arm is flexed, would be inside it.
BICEPS = arm_pose(V(0.86, -0.08, 0.50), V(0.30, -0.80, -0.50), V(0.26, -0.14, 0.955), V(-0.45, -0.88, 0.0),
                  V(0.06, -0.12, 0.99), V(-0.50, -0.86, -0.07), FIST, THUMB_ACROSS)
# The other arm of a one-arm flex: the upper arm out, the forearm down.
LOW = arm_pose(V(0.96, -0.05, -0.10), V(-0.10, 0.20, -0.97), V(0.22, -0.16, -0.96), V(-0.95, 0.20, -0.25),
               V(0.12, -0.14, -0.98), V(-0.97, 0.25, -0.15), FIST, THUMB_ACROSS)
# Where an arm passes between those two: out in front, so the fist goes round the body, not through it.
REACH = arm_pose(V(0.93, -0.30, 0.10), V(-0.10, 0.30, -0.95), V(0.70, -0.70, 0.05), V(-0.70, -0.70, -0.20),
                 V(0.55, -0.82, 0.0), V(-0.82, -0.55, -0.20), FIST, THUMB_ACROSS)
# And where it passes on its way up from hanging and back: straight out to the side, so that
# the fists go round the head and not across the dials.
OUT = arm_pose(V(0.98, -0.15, 0.0), V(0.0, 0.0, -1.0), V(0.97, -0.20, 0.10), V(0.0, 0.0, -1.0),
               V(0.95, -0.20, 0.20), V(0.0, 0.0, -1.0), FIST, THUMB_ACROSS)

# The two hand signs: the hand beside the head, fingers up, the palm to the viewer. The mascot has
# three fingers: "a" is next to the thumb, "b" is the middle one, "c" the outer one.
# Peace: a and b up and apart, c curled, the thumb across it.
PEACE = arm_pose(V(0.93, -0.12, 0.30), V(0.0, -1.0, 0.0), V(0.56, -0.20, 0.80), V(0.0, -1.0, 0.0),
                 V(0.08, -0.10, 1.0), V(0.0, -1.0, 0.0), (0, 0, 0, 0, 84, 90), (115, 0, 62, 62),
                 spread=(14.0, -14.0, 0.0))
# The horns: a and c up, b curled under the thumb.
HORNS = arm_pose(V(0.93, -0.14, 0.32), V(0.0, -1.0, 0.0), V(0.54, -0.22, 0.81), V(0.0, -1.0, 0.0),
                 V(0.07, -0.12, 1.0), V(0.0, -1.0, 0.0), (0, 0, 86, 92, 0, 0), (112, 0, 66, 64),
                 spread=(8.0, 0.0, -8.0))


def blend_arms(a, b, w):
    out = {}
    for key in ("upper", "upper_palm", "fore", "fore_palm", "hand", "hand_palm"):
        out[key] = swing(a[key], b[key], w)
    out["curl"] = tuple(clamp(x + (y - x) * w, -10, 96) for x, y in zip(a["curl"], b["curl"]))
    out["thumb"] = tuple(x + (y - x) * w for x, y in zip(a["thumb"], b["thumb"]))
    out["spread"] = tuple(x + (y - x) * w for x, y in zip(a["spread"], b["spread"]))
    return out


def curved(a, by, b, w):
    """From arm a to arm b on a curve that is drawn towards the arm `by` on its way."""
    return blend_arms(blend_arms(a, by, w), blend_arms(by, b, w), w)


def with_fingers(arm, of):
    """One arm with the fingers and thumb of another, for a hand that closes before the arm is up."""
    out = dict(arm)
    for key in ("curl", "thumb", "spread"):
        out[key] = of[key]
    return out


def turned(arm, axis, upper, fore, hand):
    """The same arm with its three parts swung about a world axis by their own angles."""
    out = dict(arm)
    for key, degrees in (("upper", upper), ("fore", fore), ("hand", hand)):
        out[key] = turn(arm[key], axis, degrees)
        out[key + "_palm"] = turn(arm[key + "_palm"], axis, degrees)
    return out


# ---------------------------------------------------------------- setting the rig
def set_rotation(name, basis):
    pb[name].rotation_euler = basis.to_euler("XYZ", pb[name].rotation_euler)


def set_body(right=0.0, up=0.0, forward=0.0, lean=0.0, turn_left=0.0, tilt_left=0.0, stretch=0.0):
    """Moves, leans and stretches the body. Returns its orientation, for the arms that hang off it."""
    b = pb["body"]
    b.location = (-right, up, forward)
    e = Euler((radians(lean), radians(turn_left), radians(-tilt_left)), "XYZ")
    b.rotation_euler = e
    wide = (1.0 / (1.0 + stretch)) ** 0.5
    b.scale = (wide, 1.0 + stretch, wide)
    return REST["body"] @ e.to_matrix()


def set_arm(side, arm, body):
    to_side = (lambda v: v) if side == "L" else mirror
    parent = body
    for bone, key in (("upper_arm", "upper"), ("forearm", "fore"), ("hand", "hand")):
        name = "%s.%s" % (bone, side)
        target = frame_of(to_side(arm[key]), to_side(arm[key + "_palm"]))
        above = rig.data.bones[name].parent.name
        set_rotation(name, (parent @ REST[above].inverted() @ REST[name]).inverted() @ target)
        parent = target
    flip = 1.0 if side == "L" else -1.0
    for i, (bone, angle) in enumerate(zip(FINGER_BONES, arm["curl"])):
        # only a finger's first bone turns sideways; one that is not spread gets a plain 0
        spread = arm["spread"][i // 2] if i % 2 == 0 else 0.0
        pb["%s.%s" % (bone, side)].rotation_euler = (radians(angle), 0.0, radians(flip * spread) if spread else 0.0)
    x1, y1, z1, x2 = arm["thumb"]
    pb["thumb.01." + side].rotation_euler = (radians(x1), radians(flip * y1), radians(flip * z1))
    pb["thumb.02." + side].rotation_euler = (radians(x2), 0.0, 0.0)


def set_foot(side, out=0.04, forward=0.0, up=0.0, toe_out=18.0, toe_up=0.0):
    """The foot turns about the point on the ground under its ankle, so a tipped shoe is lifted
    by what its toe (or heel) would otherwise sink."""
    flip = 1.0 if side == "L" else -1.0
    lift = (0.20 if toe_up > 0 else 0.42) * abs(sin(radians(toe_up)))
    foot = pb["foot." + side]
    foot.location = (-flip * out, forward, up + lift)
    foot.rotation_euler = (radians(toe_up), 0.0, radians(flip * toe_out))


def set_eyes(twist_speedo=0.0, twist_rpm=0.0, pop_speedo=0.0, pop_rpm=0.0):
    for name, twist, pop in (("eye_speedo", twist_speedo, pop_speedo), ("eye_rpm", twist_rpm, pop_rpm)):
        pb[name].rotation_euler = (0.0, radians(twist), 0.0)
        pb[name].scale = (1.0 + pop,) * 3


def set_gauges(speed=0.0, rpm=IDLE_RPM):
    rig["speed_kmh"] = clamp(speed, 0.0, 240.0)
    rig["rpm"] = clamp(rpm, 0.0, 8.0)


def set_brake(on=0.0):
    """How far the brake lights are on: 0 is unlit, 1 is full on. Only for a clip that lights
    them, which also keys the slider with key_brake; the others leave it alone, at 0."""
    rig["brake"] = clamp(on)


def key_brake(frame):
    rig.keyframe_insert('["brake"]', frame=frame)


def set_hat(on=0.0, tip=0.0):
    """The hat: 0 is none, 1 is on; tipped further forward by `tip` degrees. Only for the
    clips of the moonwalk, as set_brake is."""
    rig["hat"] = clamp(on)
    pb["hat"].rotation_euler = (radians(tip), 0.0, 0.0)


def set_turn(left=0.0):
    """Turns the whole mascot, feet and all, about the point he stands on: degrees to his own
    left. Only for the clips of the moonwalk."""
    pb["root"].rotation_euler = (0.0, 0.0, radians(left))


def set_party(cap=0.0, popper=0.0, pull=0.0, cap_tip=0.0, cap_tilt=0.0):
    """The party cap (0 is none, 1 is on), tipped forward and tilted to his left by so many
    degrees; the popper in his fist (0 is none, 1 is there); and how far the bead on its string
    is in the other hand's fingers (0 hangs, 1 is there). Only for the clip of the party."""
    rig["party_hat"] = clamp(cap)
    rig["popper"] = clamp(popper)
    rig["pull"] = clamp(pull)
    pb["party_hat"].rotation_euler = (radians(cap_tip), 0.0, radians(-cap_tilt))


def key_party(frame):
    """What the party's clip keys and no other clip does."""
    for prop in ("party_hat", "popper", "pull"):
        rig.keyframe_insert('["%s"]' % prop, frame=frame)
    pb["party_hat"].keyframe_insert("rotation_euler", frame=frame)


def key_dance(frame):
    """What the moonwalk's clips key and no other clip does."""
    key_brake(frame)
    rig.keyframe_insert('["hat"]', frame=frame)
    pb["hat"].keyframe_insert("rotation_euler", frame=frame)
    pb["root"].keyframe_insert("rotation_euler", frame=frame)


def neutral(t=0.0):
    body = set_body()
    set_arm("L", RELAXED, body)
    set_arm("R", RELAXED, body)
    set_foot("L")
    set_foot("R")
    set_eyes()
    set_gauges()


# ---------------------------------------------------------------- the clips
def idle(t):
    w = 2 * pi * t / 4.0
    breath = sin(2 * w)
    body = set_body(up=0.010 * breath, stretch=0.008 * breath, lean=0.6 * sin(2 * w),
                    turn_left=1.5 * sin(w), tilt_left=1.2 * sin(w))
    for side, sway in (("L", sin(w)), ("R", -sin(w))):
        arm = turned(RELAXED, "X", 3 * sway, 5 * sway, 6 * sway)
        arm["curl"] = tuple(c + 3 * breath for c in RELAXED["curl"])
        set_arm(side, arm, body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=2.5 * sin(w), twist_rpm=-2.5 * sin(2 * w), pop_speedo=0.01 * breath, pop_rpm=-0.01 * breath)
    # the engine ticks over: the RPM needle trembles, the speed needle breathes off its stop
    set_gauges(speed=3 - 3 * cos(w), rpm=IDLE_RPM + 0.10 * sin(4 * w) + 0.05 * sin(10 * w))


def waving(e, beat, late):
    """The whole pose of a wave at one moment: e is how far the arm is up (0 hanging, 1 up),
    beat swings the forearm from side to side (-1 to 1), and late does the same for the hand,
    which follows a little behind. Wave, WaveUp and WaveLoop differ only in when they ask."""
    body = set_body(up=0.015 * e * beat * beat, tilt_left=-3.0 * e, lean=-1.0 * e)
    # held well out to the side, so the hand never swings in front of the speedometer
    out = radians(26 + 18 * beat)
    wrist = radians(26 + 18 * beat + 16 * late)
    high = arm_pose(V(0.85, -0.10, 0.50), V(0.0, -1.0, 0.0), V(sin(out), -0.12, cos(out)), V(0.0, -1.0, 0.0),
                    V(sin(wrist), -0.12, cos(wrist)), V(0.0, -1.0, 0.0), (3, 3, 0, 0, 3, 3), (0, 0, 0, 0))
    set_arm("R", blend_arms(RELAXED, high, e), body)
    set_arm("L", turned(RELAXED, "X", -4 * e, -6 * e, -6 * e), body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=5 * e + 2 * e * beat, twist_rpm=5 * e - 2 * e * beat, pop_speedo=0.03 * e, pop_rpm=0.03 * e)
    # a little rev with every wave
    set_gauges(speed=e * (34 + 12 * beat), rpm=IDLE_RPM + e * (2.4 + 1.3 * beat))


def wave(t):
    e = smooth(t / 0.40) * (1 - smooth((t - 1.50) / 0.50))
    beat = sin(2 * pi * 2.5 * (t - 0.40))
    late = sin(2 * pi * 2.5 * (t - 0.40) - 0.9)
    waving(e, beat, late)


# One wave of the hand in WaveUp and WaveLoop: half a second, 12 frames, so that the loop closes
# on a frame. (Wave's own is 0.4 seconds, 9.6 frames, and could not be looped frame by frame.)
WAVE_BEAT = 0.5


def wave_up(t):
    """From standing to WaveLoop's first pose in one beat. The arm stays up: it is the way into
    the loop, not a clip to play alone."""
    turn = 2 * pi * (t / WAVE_BEAT - 1)
    waving(smooth(t / WAVE_BEAT), sin(turn), sin(turn - 0.9))


def wave_loop(t):
    turn = 2 * pi * t / WAVE_BEAT
    waving(1.0, sin(turn), sin(turn - 0.9))


def thumbs_up(t):
    fall = 1 - smooth((t - 1.50) / 0.45)
    e = overshoot((t - 0.05) / 0.40) * fall
    stretch = -0.035 * bump(t, 0.0, 0.28) + 0.05 * bump(t, 0.22, 0.62)
    body = set_body(up=0.02 * bump(t, 0.25, 0.65), stretch=stretch, lean=-2.5 * e, tilt_left=-1.5 * e)
    set_arm("R", blend_arms(RELAXED, THUMBS_UP, e), body)
    set_arm("L", RELAXED, body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=-4 * e, twist_rpm=-4 * e, pop_speedo=0.05 * bump(t, 0.25, 0.70), pop_rpm=0.05 * bump(t, 0.30, 0.75))
    # both needles swing up to where the picture has them: 80 km/h and 3,000 RPM
    arrived = spring(t - 0.15) * fall
    set_gauges(speed=80 * arrived, rpm=IDLE_RPM + (3.0 - IDLE_RPM) * arrived)


def walk(t):
    p = t % 1.0

    def step(phase):
        phase %= 1.0
        if phase < 0.5:  # on the ground, sliding back
            s = phase / 0.5
            return 0.15 * (1 - 2 * s), 0.0, 18 * (1 - smooth(s / 0.25)) - 22 * smooth((s - 0.7) / 0.3)
        s = (phase - 0.5) / 0.5  # in the air, swinging forward
        return 0.15 * (2 * smooth(s) - 1), 0.12 * sin(pi * s), -22 * (1 - smooth(s / 0.4)) + 18 * smooth((s - 0.55) / 0.45)

    w = 2 * pi * p
    body = set_body(right=-0.02 * sin(w), up=-0.015 - 0.02 * cos(2 * w), lean=4 + 1.5 * cos(2 * w),
                    turn_left=-5 * cos(w), tilt_left=2.5 * sin(w))
    for side, phase, reach in (("L", p, -cos(w)), ("R", p + 0.5, cos(w))):
        forward, up, toe_up = step(phase)
        set_foot(side, out=0.03, forward=forward, up=up, toe_out=10.0, toe_up=toe_up)
        # each arm swings with the opposite leg, and bends more as it comes forward
        bend = 0.5 + 0.5 * reach
        arm = turned(RELAXED, "X", -26 * reach, -26 * reach - 22 * bend, -26 * reach - 30 * bend)
        arm["curl"] = (30, 35, 35, 40, 40, 45)
        set_arm(side, arm, body)
    set_eyes(twist_speedo=2 * sin(w), twist_rpm=2 * sin(w), pop_speedo=0.015 * cos(2 * w), pop_rpm=-0.015 * cos(2 * w))
    # cruising: both needles bob with every step
    set_gauges(speed=30 + 5 * sin(2 * w + 0.5), rpm=2.2 + 0.6 * sin(2 * w + 2.0) + 0.15 * sin(4 * w))


def celebrate(t):
    first, second = hop(t, 0.32, 0.88), hop(t, 1.10, 1.54)
    height = (0.42 * first + 0.24 * second
              - 0.11 * bump(t, 0.0, 0.34) - 0.07 * bump(t, 0.88, 1.12) - 0.06 * bump(t, 1.54, 1.80))
    stretch = (-0.10 * bump(t, 0.0, 0.34) + 0.07 * bump(t, 0.30, 0.55) - 0.09 * bump(t, 0.86, 1.12)
               + 0.05 * bump(t, 1.08, 1.30) - 0.07 * bump(t, 1.52, 1.80))
    arms_up = smooth((t - 0.22) / 0.25) * (1 - smooth((t - 2.25) / 0.60))
    party = smooth((t - 1.75) / 0.2) * (1 - smooth((t - 2.35) / 0.4))
    body = set_body(up=height, stretch=stretch, lean=6 * bump(t, 0.0, 0.34) - 4 * arms_up,
                    turn_left=6 * sin(2 * pi * 1.5 * (t - 1.8)) * party, tilt_left=3 * sin(2 * pi * 3 * t) * party)
    for side, phase in (("L", 0.0), ("R", pi)):
        shake = 9 * sin(2 * pi * 3 * t + phase) * arms_up
        set_arm(side, blend_arms(RELAXED, turned(CHEER, "Y", shake * 0.5, shake, shake * 1.4), arms_up), body)
        air = max(first, second)
        up = 0.9 * (0.42 * first + 0.24 * second)
        set_foot(side, out=0.04 + 0.05 * air, up=up, toe_up=-22 * air)
    set_eyes(twist_speedo=6 * sin(2 * pi * 2 * t) * arms_up, twist_rpm=-6 * sin(2 * pi * 2 * t) * arms_up,
             pop_speedo=0.07 * first + 0.04 * second, pop_rpm=0.07 * first + 0.04 * second)
    # flat out: both needles swing right round and tremble at the red line
    revs = smooth((t - 0.25) / 0.35) * (1 - smooth((t - 2.30) / 0.60))
    set_gauges(speed=revs * (225 + 15 * sin(2 * pi * 4 * t)), rpm=IDLE_RPM + revs * (6.6 + 0.5 * sin(2 * pi * 6 * t)))


def flex(t):
    """Four poses, each squeezed once or twice: both arms; the right arm up and the left down;
    the other way round; both arms again."""
    up = smooth((t - 0.08) / 0.44) * (1 - smooth((t - 3.98) / 0.48))
    # in the second pose the left arm is the low one, in the third the right; where they change
    # over the left arm leads, so the two are never straight out at the same moment
    left_low = smooth((t - 1.18) / 0.36) * (1 - smooth((t - 2.08) / 0.38))
    right_low = smooth((t - 2.20) / 0.38) * (1 - smooth((t - 3.12) / 0.36))
    squeeze = up * (bump(t, 0.50, 0.84) + bump(t, 0.84, 1.18) + bump(t, 1.56, 2.00) + bump(t, 2.60, 3.04)
                    + bump(t, 3.48, 3.74) + bump(t, 3.72, 3.98))
    crouch = bump(t, 0.0, 0.34)
    # It leans back to show off, but not while it is turned to its right: leaning back and
    # turned that way, the dials' glossy black faces mirror the studio's key light and go pale
    # grey. The pictures are drawn with those faces dulled (render_greeting.py); the model and
    # its .glb keep the gloss, so the clip keeps out of that angle.
    back = up * (1 - right_low)
    body = set_body(right=0.05 * (left_low - right_low), up=0.015 * squeeze - 0.035 * crouch,
                    stretch=-0.04 * crouch + 0.035 * squeeze, lean=-(3.5 + 1.5 * squeeze) * back + 2.0 * right_low,
                    turn_left=10 * left_low - 4 * right_low, tilt_left=7 * (left_low - right_low))
    high = turned(BICEPS, "Y", -4 * squeeze, -7 * squeeze, -12 * squeeze)
    for name, low in (("L", left_low), ("R", right_low)):
        set_arm(name, curved(RELAXED, OUT, curved(high, REACH, LOW, low), up), body)
    set_foot("L")
    set_foot("R")
    look = 5 * (right_low - left_low)
    set_eyes(twist_speedo=look, twist_rpm=look, pop_speedo=0.05 * squeeze, pop_rpm=0.05 * squeeze)
    # the engine is revved with every squeeze
    set_gauges(speed=up * 20 + 30 * squeeze, rpm=IDLE_RPM + up * 1.0 + 4.6 * squeeze)


def peace(t):
    fall = 1 - smooth((t - 2.15) / 0.52)
    # No overshoot on the way up, as ThumbsUp has: past its place the hand would be in the RPM
    # dial. It swings a little outwards as it arrives instead.
    e = smooth((t - 0.05) / 0.40) * fall
    arrive = bump(t, 0.30, 0.62)
    # the fingers make the sign while the arm is still on its way, and let go as it comes down
    sign = smooth((t - 0.10) / 0.30) * (1 - smooth((t - 2.18) / 0.34))
    held = smooth((t - 0.50) / 0.25) * (1 - smooth((t - 1.90) / 0.25))
    rock = sin(2 * pi * 1.25 * (t - 0.50)) * held
    stretch = -0.03 * bump(t, 0.0, 0.26) + 0.04 * bump(t, 0.20, 0.62)
    body = set_body(up=0.02 * bump(t, 0.24, 0.64), stretch=stretch, lean=-2.0 * e, tilt_left=3.5 * e + 1.0 * rock)
    shown = turned(PEACE, "Y", 0.0, 3 * rock + 4 * arrive, 6 * rock + 7 * arrive)
    set_arm("L", with_fingers(blend_arms(RELAXED, shown, e), blend_arms(RELAXED, PEACE, sign)), body)
    set_arm("R", turned(RELAXED, "X", -4 * e, -6 * e, -6 * e), body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=4 * e, twist_rpm=4 * e, pop_speedo=0.04 * bump(t, 0.25, 0.70), pop_rpm=0.04 * bump(t, 0.30, 0.75))
    # an easy cruise: 50 km/h at 2,000 RPM
    arrived = spring(t - 0.15) * fall
    set_gauges(speed=50 * arrived, rpm=IDLE_RPM + (2.0 - IDLE_RPM) * arrived)


def rock_on(t):
    fall = 1 - smooth((t - 2.15) / 0.52)
    e = smooth((t - 0.05) / 0.36) * fall
    arrive = bump(t, 0.26, 0.56)
    sign = smooth((t - 0.08) / 0.28) * (1 - smooth((t - 2.18) / 0.34))
    # three nods to a beat of two a second, the hand pumping with them
    held = smooth((t - 0.42) / 0.12) * (1 - smooth((t - 1.92) / 0.20))
    nod = held * (0.5 - 0.5 * cos(2 * pi * 2.0 * (t - 0.42)))
    stretch = -0.03 * bump(t, 0.0, 0.24) + 0.03 * bump(t, 0.18, 0.50) - 0.025 * nod
    body = set_body(up=-0.02 * nod, forward=0.02 * nod, stretch=stretch, lean=-2.0 * e + 8.0 * nod, tilt_left=-3.0 * e)
    shown = turned(turned(HORNS, "X", 0.0, 7 * nod, 13 * nod), "Y", 0.0, 4 * arrive, 7 * arrive)
    set_arm("R", with_fingers(blend_arms(RELAXED, shown, e), blend_arms(RELAXED, HORNS, sign)), body)
    set_arm("L", turned(RELAXED, "X", -4 * e - 5 * nod, -6 * e - 8 * nod, -6 * e - 10 * nod), body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=-4 * e, twist_rpm=-4 * e, pop_speedo=0.05 * nod, pop_rpm=0.05 * nod)
    # every nod takes the engine to the red line
    loud = smooth((t - 0.20) / 0.25) * fall
    set_gauges(speed=loud * (110 + 30 * nod), rpm=IDLE_RPM + loud * (3.2 + 3.0 * nod))


# ---------------------------------------------------------------- the moonwalk
# He glides backward: the foot that is up on its toe stays where it is on the floor, the other
# slides back flat, and then they change over. The clip is drawn on the spot, as Walk is: each
# foot goes MOON_REACH between its place in front and its place behind, and that is how far he
# glides while one foot slides, so one cycle carries him twice that (MOON_STRIDE).
MOON_HALF = 7  # frames one foot slides for
MOON_SECONDS = 2 * MOON_HALF / FPS
MOON_REACH = 0.56
MOON_STRIDE = 2 * MOON_REACH
MOON_BACK = -0.33  # where a flat foot's ankle is at the back; in front it is MOON_REACH further on
MOON_HEEL = 40.0  # degrees the heel comes up
# He does not glide straight back but this many degrees to his right of it, and his feet go
# that way under him. The pictures show him half turned to the viewer and move him level across
# the screen: gliding straight back he would be going away from the viewer as well, and a foot
# that should stay where it is would creep up and down the screen. This slant takes that out:
# it is 90 less the angle he is drawn at (GLIDE_ANGLE in render_moonwalk.py).
MOON_SLANT = 35.0
MOON_APART = 0.10  # how far each foot is out to its side in the middle of its way
# For the same pictures: the left foot is the one farther from the viewer and is drawn smaller,
# and its way with it. So it goes this part further and the right foot this part less, and in
# the picture both go equally far: otherwise neither would quite keep its place on the screen.
MOON_FAR = 0.056
TOE = 0.42  # from under the ankle to where a shoe stands on its toe, as in set_foot
# How far he is turned at the end of his skid, his back to the viewer, and in the pose that
# follows, in degrees to his own left of the way he faces while he glides. He is drawn gliding
# 55 degrees from the viewer (render_moonwalk.py), so in the pose he is a little more towards them.
SKID_TURN = 92.0
POSE_TURN = -12.0
STOP_FRAMES, THRUSTS, THRUST_FRAMES = 44, 3, 7
KICK_FRAMES, ON_FRAMES = 26, 10

# One hand to the brim of the hat, at its corner over the big dial, where it hides least: the
# arm nearly straight up, the palm under the brim's edge and the fingers over it. Written for
# the left arm like every pose; the right arm, which is the mirror image, is the one that does it.
HAT_HAND = arm_pose(V(0.22, -0.30, 0.93), V(-0.8, 0.55, 0.0), V(-0.16, -0.71, 0.68), V(-0.8, 0.55, 0.0),
                    V(-0.25, -0.25, 0.94), V(-0.8, 0.55, 0.1), (48, 55, 50, 58, 48, 55), (60, 0, 30, 30))
# The other hand at his belt: in front of the foot of the body, the thumb up.
BELT = arm_pose(V(0.38, -0.40, -0.84), V(0.2, 0.9, -0.3), V(-0.61, -0.71, -0.36), V(0.2, 0.95, -0.1),
                V(-0.92, -0.20, -0.34), V(0.2, 0.95, -0.1), (78, 84, 80, 86, 78, 84), (15, 0, 0, 10))
# Out to the side and low, for balance; and flung straight out, the hand open.
BALANCE = dict(blend_arms(RELAXED, OUT, 0.6), curl=RELAXED["curl"], thumb=RELAXED["thumb"])
FLING = dict(OUT, curl=CHEER["curl"], thumb=CHEER["thumb"])

# A stance is the body (set_body's seven numbers) and the two feet (set_foot's five each).
SKID = dict(body=(0.0, -0.07, -0.03, -7.0, 0.0, 0.0, -0.03), L=(0.10, -0.14, 0.0, 22.0, 0.0), R=(0.08, 0.14, 0.0, 14.0, 0.0))
# The pose: the weight on the left foot, the right knee popped in with the heel up.
POSE = dict(body=(0.0, -0.02, 0.02, -5.0, 0.0, -2.0, 0.0), L=(0.09, -0.04, 0.0, 26.0, 0.0), R=(0.0, 0.04, 0.0, -8.0, -36.0))
# A hip thrust: the foot of the body goes forward and the top of it back.
THRUST = dict(body=(0.0, -0.05, 0.24, -22.0, 0.0, -2.0, -0.03), L=(0.09, -0.04, 0.0, 26.0, 0.0), R=(0.0, 0.04, 0.0, -8.0, -48.0))
# The kick: the left leg, the one on the far side of the hand at the hat, straight out to its
# side and high, the body leaning away from it.
KICK_READY = dict(body=(-0.02, -0.07, 0.0, 2.0, 0.0, 3.0, -0.04), L=(-0.02, 0.0, 0.05, 10.0, -15.0), R=(0.06, 0.0, 0.0, 14.0, 0.0))
KICK = dict(body=(0.06, 0.02, 0.0, -3.0, 0.0, -11.0, 0.02), L=(0.62, -0.06, 0.40, 64.0, 24.0), R=(0.06, 0.0, 0.0, 14.0, 0.0))
SPIN = dict(body=(0.0, 0.05, 0.0, 0.0, 0.0, 0.0, 0.03), L=(0.0, -0.02, 0.0, 6.0, -30.0), R=(0.0, 0.05, 0.0, 6.0, -30.0))
# Down on the toes of both feet.
LAND = dict(body=(0.0, -0.06, 0.0, 4.0, 0.0, 0.0, -0.02), L=(0.06, -0.06, 0.0, 16.0, -42.0), R=(0.06, 0.06, 0.0, 10.0, -42.0))
# What the dials show while he glides.
MOON_SPEED, MOON_RPM = 30.0, 2.0


def ease(f, a, b):
    """0 up to frame a, 1 from frame b on, smoothly from the one to the other."""
    return smooth((f - a) / (b - a))


def blend(a, b, w):
    """The stance that lies the part w of the way from a to b."""
    return {part: tuple(x + (y - x) * w for x, y in zip(a[part], b[part])) for part in a}


def stand(stance):
    body = set_body(*stance["body"])
    for side in ("L", "R"):
        set_foot(side, *stance[side])
    return body


def carried(arm, body, side):
    """The same arm turned as the body is turned, so that a hand on the hat or at the belt stays
    there when the body leans. (The arm poses are directions in the room, not from the body.)"""
    moved = body @ REST["body"].inverted()
    if side == "R":  # set_arm mirrors a pose for the right arm: the turn is mirrored to match
        flip = Matrix.Scale(-1.0, 3, (1, 0, 0))
        moved = flip @ moved @ flip
    out = dict(arm)
    for key in ("upper", "upper_palm", "fore", "fore_palm", "hand", "hand_palm"):
        out[key] = moved @ arm[key]
    return out


def moon_foot(q):
    """One foot at the part q of its cycle: how far along its way it is, from MOON_BACK behind
    him to MOON_REACH further on, and how far its heel is up. In the first half it is on its
    toe, and the toe stays where it is on the floor, which goes by under him at an even pace;
    the heel is up at once and comes down as the foot gets ahead of him. In the second half it
    is flat and slides back. Over the floor it goes twice as far as he glides, starting and
    ending gently: that is the last two terms."""
    q %= 1.0
    if q < 0.5:
        s = q / 0.5
        return MOON_BACK + MOON_REACH * s, MOON_HEEL * smooth(s / 0.2) * (1 - smooth((s - 0.3) / 0.7))
    s = (q - 0.5) / 0.5
    return MOON_BACK + MOON_REACH * (1 - 2 * smooth(s) + s), 0.0


def moon_stance(p):
    w = 2 * pi * p
    feet = {}
    for side, q, left in (("L", p, 1.0), ("R", p + 0.5, -1.0)):
        along, heel = moon_foot(q)
        along *= 1 + left * MOON_FAR
        # A shoe that tips up on its toe carries its ankle forward over the toe: the last term.
        feet[side] = (MOON_APART + left * along * sin(radians(MOON_SLANT)),
                      along * cos(radians(MOON_SLANT)) + TOE * (1 - cos(radians(heel))), 0.0, 8.0, -heel)
    # leaning into it, the head a little ahead, and sinking a little on every slide
    return dict(body=(0.0, -0.035 + 0.012 * cos(2 * w), 0.015 * sin(2 * w), 7.0 + 1.5 * cos(2 * w),
                      3.0 * cos(w), 1.5 * sin(w), 0.0), **feet)


def moon_arm(p, side):
    """The arms are bent, the hands at his waist and clear of his feet, which are what a
    moonwalk is watched for; they swing a little against the feet."""
    reach = cos(2 * pi * p) * (1.0 if side == "L" else -1.0)
    arm = turned(RELAXED, "X", -9 * reach + 2, -9 * reach - 72, -9 * reach - 84)
    arm["curl"] = (40, 46, 44, 50, 48, 54)
    return arm


def moonwalk(t):
    p = (t / MOON_SECONDS) % 1.0
    w = 2 * pi * p
    body = stand(moon_stance(p))
    for side in ("L", "R"):
        set_arm(side, moon_arm(p, side), body)
    set_eyes(twist_speedo=2 * sin(w), twist_rpm=2 * sin(w))
    set_gauges(speed=MOON_SPEED + 3 * sin(2 * w), rpm=MOON_RPM + 0.3 * sin(2 * w))
    set_hat(1.0)
    set_brake(0.0)
    set_turn(0.0)


def moonwalk_stop(t):
    """From the moonwalk's first pose. He brakes, and his back end skids round towards the
    viewer with the brake lights on; then he comes back round into the pose, one hand to the
    brim of his hat and the other at his belt, and thrusts his hips THRUSTS times."""
    f = t * FPS
    skid, pose = ease(f, 0, 5), ease(f, 11, 19)
    first = STOP_FRAMES - THRUSTS * THRUST_FRAMES - 1
    thrust = 0.0
    for i in range(THRUSTS):  # out fast, back slower
        u = (f - first - i * THRUST_FRAMES) / THRUST_FRAMES
        if 0.0 < u < 1.0:
            thrust = smooth(u / 0.3) * (1 - smooth((u - 0.42) / 0.58))
    # he rocks on his feet as the skid ends, and his arms with him
    rock = exp(-0.35 * max(f - 5, 0)) * sin(0.95 * max(f - 5, 0)) * (1 - pose)
    stance = blend(blend(blend(moon_stance(0.0), SKID, skid), POSE, pose), THRUST, thrust)
    right, up, forward, lean, turn_left, tilt_left, stretch = stance["body"]
    stance["body"] = (right, up + 0.03 * rock, forward, lean + 5 * rock, turn_left, tilt_left, stretch + 0.02 * rock)
    body = stand(stance)
    for side, held in (("R", HAT_HAND), ("L", BELT)):
        out = turned(BALANCE, "Y", 8 * rock, 12 * rock, 16 * rock)
        set_arm(side, blend_arms(blend_arms(moon_arm(0.0, side), out, skid), carried(held, body, side), pose), body)
    set_eyes(pop_speedo=0.05 * thrust, pop_rpm=0.05 * thrust)
    # the needles fall as he stops, and the engine is revved with every thrust
    set_gauges(speed=MOON_SPEED * (1 - ease(f, 0, 8)), rpm=MOON_RPM + (IDLE_RPM - MOON_RPM) * ease(f, 0, 8) + 5.0 * thrust)
    set_hat(1.0, tip=5 * pose + 4 * thrust)
    set_brake(ease(f, 1, 3))
    # the skid is quick at first, swings a little too far and comes to rest; the way back is
    # one smooth turn
    swung = (1 - (1 - clamp(f / 6)) ** 3) + 0.07 * exp(-0.5 * max(f - 6, 0)) * sin(1.1 * max(f - 6, 0))
    set_turn(SKID_TURN * swung + (POSE_TURN - SKID_TURN) * ease(f, 11, 19))


def kick_spin(t):
    """From the pose. He winds up the other way, kicks his right foot out in front, and turns
    once round on the spot, fast, his back and its brake lights going past the viewer; then he
    comes down on the toes of both feet, and the brake lights go out."""
    f = t * FPS
    ready = ease(f, 0, 3) * (1 - ease(f, 3, 5))
    kick = ease(f, 3, 6) * (1 - ease(f, 7, 10))
    spin = ease(f, 7, 10) * (1 - ease(f, 19, 22))
    land = ease(f, 19, 22)
    stance = blend(blend(blend(blend(POSE, KICK_READY, ready), KICK, kick), SPIN, spin), LAND, land)
    body = stand(stance)
    set_arm("R", carried(HAT_HAND, body, "R"), body)
    flung = max(kick, land)
    set_arm("L", blend_arms(carried(BELT, body, "L"), FLING, flung), body)
    turning = clamp((f - 7) / 13)
    whirl = 40 * turning * (1 - turning)
    set_eyes(twist_speedo=-whirl, twist_rpm=whirl, pop_speedo=0.04 * kick, pop_rpm=0.04 * kick)
    # the engine screams through the turn
    revs = ease(f, 2, 6) * (1 - 0.6 * ease(f, 20, 26))
    set_gauges(speed=200 * ease(f, 7, 12) * (1 - 0.8 * ease(f, 19, 26)), rpm=IDLE_RPM + 5.8 * revs)
    set_hat(1.0, tip=5.0)
    set_brake(1 - ease(f, 21, 25))  # he lets go of the brake as he lands
    # wound up 16 degrees the other way first; then an even pace through the middle of the turn,
    # so that every frame of it is a clear step on from the last
    turned_by = 0.55 * smooth(turning) + 0.45 * turning
    set_turn(POSE_TURN - 16 * ease(f, 0, 4) * (1 - turned_by) + 360 * turned_by)


def moonwalk_on(t):
    """From his toes back into the moonwalk's first pose: the hand comes down from the hat, and
    he is turned back the way he glides."""
    f = t * FPS
    go = ease(f, 0, ON_FRAMES)
    body = stand(blend(LAND, moon_stance(0.0), go))
    set_arm("R", blend_arms(carried(HAT_HAND, body, "R"), moon_arm(0.0, "R"), ease(f, 1, ON_FRAMES - 1)), body)
    set_arm("L", blend_arms(FLING, moon_arm(0.0, "L"), ease(f, 0, ON_FRAMES - 2)), body)
    set_eyes()
    set_gauges(speed=40 + (MOON_SPEED - 40) * go, rpm=IDLE_RPM + 5.8 * 0.4 + (MOON_RPM - IDLE_RPM - 5.8 * 0.4) * go)
    set_hat(1.0, tip=5 * (1 - ease(f, 0, 6)))
    set_brake(0.0)
    set_turn(POSE_TURN * (1 - ease(f, 0, ON_FRAMES - 2)))


# ---------------------------------------------------------------- the party
# A year of use. He stands in his party cap; reaches behind his back with his left hand; brings
# out a party popper and holds it up; brings it in front of him, where his right hand takes the
# string, and pulls: confetti over his head, and he jumps and cheers while it comes down. One
# clip, OneYear, and beside it the confetti's own clip on the confetti's own skeleton
# (throw_confetti, below).
#
# The popper is fired in front of his chest, not above his head: his arms are short, and his
# two hands only meet in front of his middle. It is aimed up between the dials and a little to
# his left, away from the cap, so the confetti goes up past his face and over his head.
PARTY_HOLD = 12  # frames he stands still at the start, for a picture of him to fade in on
PARTY_POP = 73  # the frame the popper goes off on
PARTY_FRAMES = 130

SHOULDER = rig.data.bones["upper_arm.L"].head_local.copy()
UPPER_ARM, FOREARM = rig.data.bones["upper_arm.L"].length, rig.data.bones["forearm.L"].length


def held(bone):
    """Where a bone lies in the hand that carries it, as build_milo.py put it there: how far
    along the hand from the wrist, how far to the thumb's side, how far under the palm; and
    which hand it is."""
    hand = rig.data.bones[bone].parent
    x, y, z = hand.matrix_local.inverted() @ rig.data.bones[bone].head_local
    return y, (-x if hand.name.endswith(".L") else x), z, hand.name[-1]


GRIP_ALONG, _, GRIP_UNDER, POPPER_HAND = held("popper")
PINCH_ALONG, PINCH_THUMB, PINCH_UNDER, PULL_HAND = held(next(b.name for b in rig.data.bones if b.name.startswith("grip.")))
# From the middle of the fist along the popper: to its mouth, and back to the bead on its string.
POPPER_LENGTH = rig.data.bones["popper"].length
BEAD_BACK = (rig.data.bones["popper_pull"].head_local - rig.data.bones["popper"].head_local).length


def reaching(wrist, elbow, hand, palm, curl, thumb, spread=(0.0, 0.0, 0.0)):
    """An arm said by where its wrist is, not by which way its bones point: `wrist` is a point
    in the room while he stands as he was built, and the elbow bends towards `elbow`. Written
    for the left arm, like every pose. A wrist out of reach is brought as near as the arm goes."""
    reach = wrist - SHOULDER
    far = min(reach.length, 0.995 * (UPPER_ARM + FOREARM))
    way = reach.normalized()
    along = (UPPER_ARM ** 2 - FOREARM ** 2 + far ** 2) / (2 * far)
    off = max(UPPER_ARM ** 2 - along ** 2, 0.0) ** 0.5
    joint = way * along + (elbow - way * elbow.dot(way)).normalized() * off
    return arm_pose(joint, palm, way * far - joint, palm, hand, palm, curl, thumb, spread)


def holding(grip, mouth, fingers, elbow):
    """The arm that holds the popper: the middle of its fist at `grip`, the popper's mouth
    pointing `mouth`, the fingers pointing as nearly `fingers` as that leaves them free to."""
    mouth = mouth.normalized()
    along = (fingers - mouth * fingers.dot(mouth)).normalized()
    palm = along.cross(mouth)
    return reaching(grip - along * GRIP_ALONG - palm * GRIP_UNDER, elbow, along, palm, GRIPPED, THUMB_ROUND)


def pinching(at, fingers, palm, elbow, curl, thumb):
    """The arm whose fingers pinch the bead of the string: the pinch at `at`."""
    along = fingers.normalized()
    palm = (palm - along * palm.dot(along)).normalized()
    wrist = at - along * PINCH_ALONG - palm.cross(along) * PINCH_THUMB - palm * PINCH_UNDER
    return reaching(wrist, elbow, along, palm, curl, thumb)


# A fist round the popper's handle, and one that pinches the bead.
GRIPPED = (60, 76, 62, 78, 60, 76)
THUMB_ROUND = (100, 0, 44, 56)
PINCHED = (46, 62, 60, 74, 70, 82)
THUMB_PINCH = (96, 0, 36, 40)
# The left arm. Behind his back, the fist where the camera cannot see it, the popper upright;
# and where the arm passes on its way there, swung back and out, so that it goes round the body.
BEHIND = holding(V(0.09, 0.60, 1.08), V(-0.10, 0.0, 0.95), V(-1.0, 0.1, -0.1), V(0.8, 0.5, -0.3))
BACK = dict(reaching(V(0.76, 0.34, 0.90), V(0.7, 0.4, 0.3), V(-0.35, 0.75, -0.55), V(0.3, 0.6, 0.7), RELAXED["curl"], RELAXED["thumb"]))
# Out again: the forearm swings out round the elbow, the fist passing behind him and out at his
# side; then up, the popper held high beside his head, its mouth up and in over his head.
OUTBACK = holding(V(0.62, 0.66, 1.08), V(0.0, 0.0, 1.0), V(0.1, 1.0, 0.0), V(1.0, 0.3, -0.3))
SIDE = holding(V(0.99, 0.14, 1.08), V(0.15, -0.10, 0.98), V(1.0, 0.2, 0.0), V(0.3, 0.6, -0.7))
SHOWN = holding(V(0.84, -0.12, 1.78), V(-0.42, -0.10, 0.90), V(0.9, 0.0, 0.42), V(0.8, 0.2, -0.3))
# In front of his chest, to be fired: the mouth up between the dials.
AIM_GRIP, AIM_WAY = V(0.05, -0.52, 1.22), V(0.10, -0.15, 0.985).normalized()
AIM = holding(AIM_GRIP, AIM_WAY, V(-0.6, -0.8, 0.0), V(0.7, -0.2, -0.6))
# And up again once it has gone off.
HIGH = holding(V(0.90, -0.12, 1.76), V(-0.30, -0.10, 0.95), V(0.9, 0.0, 0.42), V(0.8, 0.2, -0.3))
# The right arm, written for the left like every pose and mirrored when it is set. Its fingers
# come to the bead from below, the palm up; take it; draw the string tight; and are flung away.
BEAD = mirror(AIM_GRIP - AIM_WAY * BEAD_BACK)
READY = pinching(BEAD + V(0.16, -0.02, -0.16), V(-0.8, -0.55, -0.2), V(0.0, 0.15, 1.0), V(0.6, 0.2, -0.6),
                 (20, 26, 24, 30, 30, 36), (40, 0, 10, 14))
TAKEN = pinching(BEAD, V(-0.8, -0.55, -0.2), V(0.0, 0.15, 1.0), V(0.6, 0.2, -0.6), PINCHED, THUMB_PINCH)
TAUT = pinching(BEAD - mirror(AIM_WAY) * 0.09, V(-0.8, -0.55, -0.2), V(0.0, 0.15, 1.0), V(0.6, 0.2, -0.6), PINCHED, THUMB_PINCH)
# Flung away down and to its side, the way it pulled; and where it passes on its way up to cheer.
FLUNG = reaching(V(0.80, -0.20, 0.90), V(0.3, 0.7, 0.6), V(0.55, -0.30, -0.78), V(-0.3, 0.9, -0.3), CHEER["curl"], CHEER["thumb"])
WIDE = arm_pose(V(0.95, -0.12, 0.28), V(0.0, -1.0, 0.0), V(0.92, -0.18, 0.34), V(0.0, -1.0, 0.0),
                V(0.90, -0.20, 0.38), V(0.0, -1.0, 0.0), CHEER["curl"], CHEER["thumb"])


def one_year(t):
    """In his party cap: stands still; bobs up, glad; reaches behind his back with his left
    hand and brings out the popper; holds it up; brings it in front of him, where his right
    hand takes the string, draws it tight and pulls; and when it goes off he jumps and cheers,
    both arms up, and comes to rest like that."""
    f = t * FPS
    pop = f - PARTY_POP  # frames since the popper went off
    # what he is doing, each from 0 to 1
    crouch, rise = bump(f, 12, 17), bump(f, 15, 23)
    behind = ease(f, 22, 32) * (1 - ease(f, 41, 47))
    wiggle = sin(2 * pi * (f - 33) / 4.0) * ease(f, 33, 34) * (1 - ease(f, 39, 41))
    shown = ease(f, 45, 53) * (1 - ease(f, 56, 63))
    aimed = ease(f, 56, 65) * (1 - ease(pop, 0, 3))
    tense = ease(f, 64, 72) * (1 - ease(pop, 0, 1))
    shiver = tense * sin(2 * pi * f / 2.0)
    recoil = clamp(pop / 1.5) * exp(-0.5 * max(pop - 1.5, 0.0))
    jump, land, skip = hop(f, PARTY_POP + 2, PARTY_POP + 11), bump(f, PARTY_POP + 10, PARTY_POP + 16), hop(f, PARTY_POP + 16, PARTY_POP + 22)
    cheer = ease(pop, 1, 8)
    still = 1 - ease(f, PARTY_FRAMES - 24, PARTY_FRAMES - 8)  # he comes to rest at the end
    sway = cheer * still * sin(2 * pi * (pop - 22) / 16.0) * ease(pop, 20, 26)

    body = set_body(
        right=0.009 * shiver,
        up=(-0.04 * crouch + 0.045 * rise - 0.012 * behind + 0.014 * wiggle * wiggle + 0.02 * shown - 0.05 * aimed - 0.04 * tense
            - 0.03 * recoil + 0.20 * jump - 0.05 * land + 0.05 * skip),
        stretch=(-0.05 * crouch + 0.05 * rise + 0.03 * shown - 0.045 * aimed - 0.045 * tense
                 + 0.07 * bump(f, PARTY_POP + 1, PARTY_POP + 7) - 0.08 * land + 0.035 * bump(f, PARTY_POP + 15, PARTY_POP + 21)),
        lean=3 * crouch - 2 * rise + 4 * behind - 3 * shown + 5 * aimed + 3 * tense - 9 * recoil - 3 * cheer,
        turn_left=13 * behind + 5 * wiggle - 3 * shown,
        tilt_left=-3 * behind + 2.5 * wiggle + 3 * shown + 3.5 * sway,
    )

    # the arm with the popper
    glad = blend_arms(RELAXED, BALANCE, 0.5 * rise)  # both arms out a little as he bobs up
    if f < 41:
        left = turned(curved(glad, BACK, BEHIND, ease(f, 22, 32)), "Z", 0.0, 5 * wiggle, 9 * wiggle)
    else:
        left = curved(BEHIND, OUTBACK, SIDE, ease(f, 41, 47))
    left = blend_arms(left, SHOWN, ease(f, 45, 53) + 0.05 * bump(f, 51, 57))
    left = blend_arms(left, AIM, ease(f, 56, 65))
    left = turned(left, "X", 0.0, -7 * recoil, -12 * recoil)
    shake = 7 * sin(2 * pi * pop / 8.0) * cheer * still
    left = blend_arms(left, turned(HIGH, "Y", 0.3 * shake, 0.6 * shake, 0.8 * shake), cheer)
    set_arm("L", left, body)

    # the other arm
    right = blend_arms(RELAXED, BALANCE, 0.5 * rise + 0.45 * behind + 0.3 * shown)
    right = blend_arms(right, READY, ease(f, 55, 62))
    right = blend_arms(right, TAKEN, ease(f, 60, 65))
    right = blend_arms(right, TAUT, ease(f, 65, 72))
    right = blend_arms(right, FLUNG, ease(pop, -1.5, 2.5))
    right = curved(right, WIDE, turned(CHEER, "Y", -0.5 * shake, -shake, -1.4 * shake), ease(pop, 2, 9))
    set_arm("R", right, body)

    air = max(jump, 0.4 * skip)
    for side in ("L", "R"):
        set_foot(side, out=0.04 + 0.05 * air, up=0.9 * (0.20 * jump + 0.05 * skip), toe_up=-22 * air)

    # The dials: they tip towards the hand behind his back, look up at the popper, lean
    # together over it, and fly open when it goes off.
    set_eyes(twist_speedo=-7 * behind - 4 * wiggle - 5 * shown - 9 * aimed + 8 * recoil + 3 * sway,
             twist_rpm=-7 * behind - 4 * wiggle - 5 * shown + 9 * aimed - 8 * recoil + 3 * sway,
             pop_speedo=0.05 * rise + 0.03 * shown - 0.03 * aimed - 0.02 * tense + 0.10 * recoil + 0.03 * cheer * still,
             pop_rpm=0.05 * rise + 0.03 * shown - 0.03 * aimed - 0.02 * tense + 0.12 * recoil + 0.03 * cheer * still)
    # The engine: a flick of both needles at the start, a tremble while he feels about behind
    # his back, up for the popper, and down to nothing while he aims; the revs climb as the
    # string comes tight, and both needles go right round when it goes off.
    after = clamp(pop / 1.0)
    calm = ease(pop, 22, 50)
    set_gauges(
        speed=(55 * rise + 6 * behind + 65 * shown * (1 - aimed)
               + after * ((238 - 110 * calm) * spring(pop / 9.0) + 14 * sin(2 * pi * pop / 5.0) * (1 - calm))),
        rpm=(IDLE_RPM + 2.6 * rise + 0.5 * behind + 0.5 * wiggle + 2.4 * shown * (1 - aimed)
             + 5.6 * tense + 0.25 * shiver
             + after * ((7.0 - 3.4 * calm) * spring(pop / 6.0) + 0.5 * sin(2 * pi * pop / 3.0) * (1 - calm))))

    # The cap is paper on a band: it is a moment behind whatever his head does.
    set_party(cap=1.0, popper=ease(f, 35, 38), pull=ease(f, 61, 65) * (1 - ease(pop, -0.5, 1.5)),
              cap_tip=(5 * bump(f, 13, 18) - 7 * bump(f, 17, 24) + 4 * aimed - 13 * recoil
                       + 9 * bump(f, PARTY_POP + 4, PARTY_POP + 11) - 8 * bump(f, PARTY_POP + 11, PARTY_POP + 18)),
              cap_tilt=-3 * behind + 4 * bump(f, 47, 56) - 3.5 * sway)


# ---------------------------------------------------------------- the confetti
# The pieces build_milo.py cut are thrown here: every piece is a bone of the confetti's own
# skeleton, and its flight is worked out, not simulated by Blender, from one seed, so that
# every run throws the same confetti. Blown out of the popper's mouth in a cone, the paper is
# slowed by the air within half a second, and then flutters down at the pace of falling paper;
# the streamers go further, unroll and hang as they fall. He stands in a slab of room that the
# pieces are pushed out of, to the front or to the back: they fall in front of him and behind
# him, and none goes through him.
CONFETTI_SEED = 365
# The slab he stands in: how far it reaches to each side and up, and its front and back.
SLAB_SIDE, SLAB_TOP, SLAB_FRONT, SLAB_BACK = 1.25, 3.1, -0.58, 0.46
FLASH = (0.7, 0.95, 0.5)  # the size of the flash at the mouth, a frame each from the pop on


def popper_mouth():
    """Where the popper's mouth is and which way it points, in the rig's own room, as the rig
    is posed in the frame the scene is at."""
    posed = rig.evaluated_get(bpy.context.evaluated_depsgraph_get()).pose.bones["popper"]
    return posed.tail.copy(), (posed.tail - posed.head).normalized()


def throw_confetti(pieces, mouth, way, frames):
    """Every piece's flight from the pop on: for each bone named in `pieces` a list with an
    entry a frame, the first for the frame of the pop, of where it is, how it is turned (three
    angles) and its size (three numbers)."""
    rnd = random.Random(CONFETTI_SEED)
    across = way.orthogonal().normalized()
    over = way.cross(across)
    steps = 4
    tick = 1.0 / (FPS * steps)
    flights = {}
    for name in pieces:
        if name == "flash":
            flights[name] = [(mouth + way * 0.12, (0.0, radians(25.0 * g), 0.0), (FLASH[g],) * 3 if g < len(FLASH) else (0.0,) * 3)
                             for g in range(frames + 1)]
            continue
        streamer = name.startswith("streamer")
        # when it leaves the mouth, which way, how far the blast carries it and how the air
        # holds it; then how fast it sinks, the wind it is in, and how it flutters and tumbles
        late = 0 if streamer else rnd.randrange(3)
        opened, round_ = radians(rnd.triangular(0.0, 24.0 if streamer else 46.0, 14.0)), rnd.uniform(0.0, 2 * pi)
        go = way * cos(opened) + (across * cos(round_) + over * sin(round_)) * sin(opened)
        carry = rnd.triangular(1.9, 2.9, 2.4) if streamer else rnd.triangular(0.9, 2.7, 1.7)
        air = 3.0 if streamer else rnd.uniform(3.6, 5.0)
        sink = rnd.uniform(0.5, 0.8) if streamer else rnd.uniform(0.55, 1.35)
        wind = V(rnd.uniform(-0.5, 0.4), rnd.uniform(-0.2, 0.6), 0.0)
        flutter = [(rnd.uniform(2.0, 5.0), rnd.uniform(6.0, 11.0), rnd.uniform(0.0, 2 * pi)) for _ in range(2)]
        spin = V(rnd.uniform(-10.0, 10.0), rnd.uniform(-10.0, 10.0), rnd.uniform(-5.0, 5.0))
        turn = V(rnd.uniform(0.0, 2 * pi), rnd.uniform(0.0, 2 * pi), rnd.uniform(0.0, 2 * pi))
        roll = rnd.uniform(-2.0, 2.0)
        floor = 0.012 + 0.006 * rnd.random()

        at, speed = mouth.copy(), go * carry * air
        flight, seconds, down, angles = [], -0.5 / FPS, False, None
        for g in range(frames + 1):
            if g < late:
                flight.append((mouth.copy(), tuple(turn), (0.0, 0.0, 0.0)))
                continue
            for _ in range(steps if g > late else steps // 2):
                seconds += tick
                if down:
                    continue
                free = smooth(seconds / 0.5)  # the blast lets go of it
                push = V(flutter[0][0] * sin(flutter[0][1] * seconds + flutter[0][2]),
                         0.5 * flutter[1][0] * sin(flutter[1][1] * seconds + flutter[1][2]), 0.0) * free
                if abs(at.x) < SLAB_SIDE and at.z < SLAB_TOP and SLAB_FRONT < at.y < SLAB_BACK:
                    push.y += 30.0 * ((SLAB_FRONT if at.y < -0.05 else SLAB_BACK) - at.y)
                speed += (-(speed - wind * free) * air + V(0.0, 0.0, -sink * air) + push) * tick
                at += speed * tick
                if at.z <= floor:
                    at.z, down = floor, True
            if streamer:
                # it hangs from the end that flew ahead: its length trails back to the mouth at
                # first, and hangs down, swaying, as it falls; and it unrolls as it goes
                hang = smooth(seconds / 0.9)
                along = (-go * (1 - hang) + V(0.25 * sin(2.2 * seconds + turn.x), 0.25 * sin(1.7 * seconds + turn.y), -1.0) * hang).normalized()
                turned_to = along.to_track_quat("Y", "Z") @ Quaternion((0.0, 1.0, 0.0), turn.z + roll * seconds)
                angles = turned_to.to_euler("XYZ", angles) if angles else turned_to.to_euler("XYZ")
                flight.append((at.copy(), tuple(angles), (1.0, 0.12 + 0.88 * smooth(seconds / 0.45), 1.0)))
            else:
                if down:  # it lies flat where it came down
                    lying = (round(angles[0] / pi) * pi, round(angles[1] / pi) * pi, angles[2])
                    angles = tuple(a + (b - a) * 0.6 for a, b in zip(angles, lying))
                else:
                    angles = tuple(turn + spin * seconds)
                flight.append((at.copy(), angles, (1.0, 1.0, 1.0)))
        flights[name] = flight
    return flights


def write_confetti(name="OneYear_Confetti"):
    """Writes the confetti's clip, on the confetti's skeleton, for the party's clip as the rig
    is showing it now (show, or a clip a drawing script wrote from one_year): the pieces leave
    the popper's mouth where it is in the frame of the pop. Until then they have no size.
    Their flight is keyed on every frame while it is fast and on every second frame after."""
    scene = bpy.context.scene
    scene.frame_set(PARTY_POP)
    mouth, way = popper_mouth()
    thrown = bpy.data.objects["MilO_Confetti_Rig"]
    old = bpy.data.actions.get(name)
    if old:
        bpy.data.actions.remove(old)
    action = bpy.data.actions.new(name)
    thrown.animation_data_create()
    thrown.animation_data.action = action
    action.use_fake_user = True
    action.use_frame_range = True
    action.frame_start = 0
    action.frame_end = PARTY_FRAMES
    after = PARTY_FRAMES - PARTY_POP
    keyed = [g for g in range(after + 1) if g <= 8 or g % 2 == 0 or g == after]
    flights = throw_confetti([b.name for b in thrown.pose.bones], mouth, way, after)
    for bone in thrown.pose.bones:
        flight = flights[bone.name]
        first = next(g for g, (_, _, size) in enumerate(flight) if size[0] > 0.0)
        bone.location, bone.rotation_euler, bone.scale = flight[first][0], flight[first][1], (0.0, 0.0, 0.0)
        for frame in sorted({0, PARTY_POP + first - 1}):
            bone.keyframe_insert("scale", frame=frame)
        bone.keyframe_insert("location", frame=PARTY_POP + first - 1)
        bone.keyframe_insert("rotation_euler", frame=PARTY_POP + first - 1)
        size = None
        for g in keyed:
            if g < first:
                continue
            bone.location, bone.rotation_euler = flight[g][0], flight[g][1]
            bone.keyframe_insert("location", frame=PARTY_POP + g)
            bone.keyframe_insert("rotation_euler", frame=PARTY_POP + g)
            if flight[g][2] != size:  # most pieces get their size once and keep it
                size = bone.scale = flight[g][2]
                bone.keyframe_insert("scale", frame=PARTY_POP + g)
    if getattr(thrown.animation_data, "action_slot", True) is None and action.slots:
        thrown.animation_data.action_slot = action.slots[0]
    return mouth, way


# The clips that also key the hat, the brake lights and the turn of the whole mascot.
DANCE = ("Moonwalk", "MoonwalkStop", "KickSpin", "MoonwalkOn")
# The clips that also key the party cap, the popper and its string.
PARTY = ("OneYear",)

CLIPS = [("Idle", 4.0, idle, True), ("Wave", 2.0, wave, False), ("ThumbsUp", 2.0, thumbs_up, False),
         ("Walk", 1.0, walk, True), ("Celebrate", 3.0, celebrate, False),
         ("Flex", 4.5, flex, False), ("Peace", 2.75, peace, False), ("RockOn", 2.75, rock_on, False),
         ("WaveUp", 0.5, wave_up, False), ("WaveLoop", 0.5, wave_loop, True),
         ("Moonwalk", MOON_SECONDS, moonwalk, True), ("MoonwalkStop", STOP_FRAMES / FPS, moonwalk_stop, False),
         ("KickSpin", KICK_FRAMES / FPS, kick_spin, False), ("MoonwalkOn", ON_FRAMES / FPS, moonwalk_on, False),
         ("OneYear", PARTY_FRAMES / FPS, one_year, False)]
NEEDLE_CLIPS = [("Gauge_Speed", "speed_kmh", 240.0), ("Gauge_RPM", "rpm", 8.0)]
NEEDLE_SECONDS = 2.0


# ---------------------------------------------------------------- writing the actions
def reset_pose():
    """Every bone back to rest, the hat off and the brake lights out: what a clip that does not
    key them is to be drawn with."""
    for b in pb:
        b.location = (0.0, 0.0, 0.0)
        b.rotation_euler = (0.0, 0.0, 0.0)
        b.scale = (1.0, 1.0, 1.0)
    rig["hat"] = 0.0
    rig["brake"] = 0.0
    for prop in ("party_hat", "popper", "pull"):
        rig[prop] = 0.0


def new_action(name, frames, loop):
    old = bpy.data.actions.get(name)
    if old:
        bpy.data.actions.remove(old)
    action = bpy.data.actions.new(name)
    rig.animation_data.action = action
    action.use_fake_user = True
    action.use_frame_range = True
    action.frame_start = 0
    action.frame_end = frames
    action.use_cyclic = loop
    return action


def key_pose(frame):
    for name in ROTATED:
        pb[name].keyframe_insert("rotation_euler", frame=frame)
    for name in MOVED:
        pb[name].keyframe_insert("location", frame=frame)
    for name in SCALED:
        pb[name].keyframe_insert("scale", frame=frame)
    rig.keyframe_insert('["speed_kmh"]', frame=frame)
    rig.keyframe_insert('["rpm"]', frame=frame)


def key_clip(name, frame):
    """Keys the pose as it stands as a frame of the clip `name`: what every clip keys, and for
    the moonwalk's clips the hat, the brake lights and the turn of the whole mascot as well."""
    key_pose(frame)
    if name in DANCE:
        key_dance(frame)
    if name in PARTY:
        key_party(frame)


def all_curves(action):
    try:
        return [fc for layer in action.layers for strip in layer.strips
                for bag in strip.channelbags for fc in bag.fcurves]
    except AttributeError:
        return list(action.fcurves)


def animate():
    if bpy.context.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    scene = bpy.context.scene
    scene.render.fps = FPS
    rig.animation_data_create()
    for track in list(rig.animation_data.nla_tracks):
        rig.animation_data.nla_tracks.remove(track)
    stale = bpy.data.actions.get("MilO_ThumbsUp")  # the single still pose the clips replace
    if stale:
        bpy.data.actions.remove(stale)

    for name, seconds, pose_at, loop in CLIPS:
        frames = round(seconds * FPS)
        reset_pose()
        new_action(name, frames, loop)
        for frame in range(frames + 1):
            pose_at(frame / FPS)
            key_clip(name, frame)

    # The two needle clips move one slider each and nothing else, so they can be laid over any
    # other clip and scrubbed to a live value.
    for name, prop, top in NEEDLE_CLIPS:
        frames = round(NEEDLE_SECONDS * FPS)
        action = new_action(name, frames, False)
        for frame, value in ((0, 0.0), (frames, top)):
            rig[prop] = value
            rig.keyframe_insert('["%s"]' % prop, frame=frame)
        for fc in all_curves(action):
            for point in fc.keyframe_points:
                point.interpolation = "LINEAR"

    for name in [c[0] for c in CLIPS] + [c[0] for c in NEEDLE_CLIPS]:
        track = rig.animation_data.nla_tracks.new()
        track.name = name
        strip = track.strips.new(name, 0, bpy.data.actions[name])
        if getattr(strip, "action_slot", True) is None and bpy.data.actions[name].slots:
            strip.action_slot = bpy.data.actions[name].slots[0]
        track.mute = True

    # The confetti's clip is thrown from where the party's clip has the popper's mouth.
    show("OneYear")
    write_confetti()
    show("Idle")
    print("clips:", {a.name: "%d frames%s" % (a.frame_end, ", loops" if a.use_cyclic else "")
                     for a in bpy.data.actions})


def show(name, frame=0):
    """Makes one clip the rig's live action and the scene's frame range."""
    action = bpy.data.actions[name]
    reset_pose()
    neutral()
    rig.animation_data.action = action
    if getattr(rig.animation_data, "action_slot", True) is None and action.slots:
        rig.animation_data.action_slot = action.slots[0]
    scene = bpy.context.scene
    scene.frame_start = 0
    scene.frame_end = int(action.frame_end)
    scene.frame_set(frame)


if __name__ != "__milo_lib__":
    animate()

# render_wave2.py - draws the app's first greeting, the wave, as pictures: the mascot walking in
# from the left, stopping in the middle with his brake lights coming on, turning to the viewer
# and waving, turning away to the right and walking off, his back and its brake lights to the
# viewer. (The wave as it has been since 2026-10-11, "walked through": the one before it is
# render_greeting.py's own.) Run it after the other scripts, with Blender WITHOUT its window,
# and name a folder for the frames:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_wave2.py -- <folder> [<pixels to the dp>] [--plain] [--measure] [--every <n>]
#
# It draws into <folder>. pack_wave2.py makes the two pictures the app carries out of that, and
# preview_wave2.py lays it over a picture of Home as the films Shawn was shown:
#
#   in/0000.png ...          one cycle of the clip "WalkIn", drawn on the spot. The frame after
#                            the last is the first again
#   middle/0000.png ...      the clips "WalkStop", "Wave" and "TurnAway" one after the other. Its
#                            first frame is the frame the walk in would show next, and the frame
#                            after its last is the walk off's first
#   out/0000.png ...         one cycle of the clip "WalkOff", drawn on the spot, the brake lights out
#   middle-lit/00NN.png ...  the frames of the middle again in which the brake lights go out,
#                            with the lights left lit
#   out-lit/0000.png ...     the walk off again with the brake lights lit
#   wave2.json               what the frames are: the picture's size, where he stands in it, how
#                            far a cycle of the walk carries him, and where each shoe stands on
#                            the floor in every frame. Both other scripts read it
#
# SHAWN CHOSE BETWEEN TWO WAYS WITH THE LIGHTS, and both are still drawn. In both they come on
# as he stops and stay on while he waves, where they are behind him. In A, which he chose on
# 2026-10-11, which the clips say and which the app plays, they go out as he lifts his foot for
# the first step off: middle/ and out/. In B they stay lit all the way off the screen: middle/
# with the frames of middle-lit/ in place of their twins, and out-lit/. pack_wave2.py reads A's
# folders only.
#
# He is drawn as the greeting draws him: MASCOT_DP high from the studio's camera, under its
# lights, on a see-through background, the dials' black faces dulled. The rig is not turned:
# these clips turn him themselves, with the bone "root" (animate_milo.py, "the walk in and the
# walk off"). With no pixels to the dp named there are 2.5 of them, which is what the app's
# pictures have and what pack_wave2.py asks for; the preview asks for what its picture of Home
# has, 2.625. --every 3 draws only every third frame of the middle, for a quick look.
#
# The pictures hold the halo round the brake lights where they are lit and in view
# (halo_see_through in render_greeting.py), so those frames are for a dark ground. With --plain
# only the walk off with the lights lit is drawn, without the halo, into out-lit-plain/: that
# is right on any ground, white too, and preview_wave2.py makes a still of it. With --measure
# nothing is drawn: it only says what it measures, the picture's size and how well a planted
# foot keeps its place.
#
# One thing is done to the studio's lights while he turns (sheen, below): the fill light's
# shine is taken off for the two or three frames in which the whole of his flat front mirrors
# it. In memory, like everything here.
#
# Nothing here writes into the app: pack_wave2.py does that. NOTHING IS SAVED.

import json
import os
import sys
import types
import bpy
from math import degrees

# Clear room round him in the picture, for the halo to fade in: this part of his height.
ROOM = 0.14
# A point of a shoe is on the floor when it is no higher than this, in the model's units.
ON_THE_FLOOR = 0.004
# The sheen. His front is flat and glossy, and the studio's fill light is a large soft one to
# the viewer's right: turned about SHEEN_AT degrees towards it, the whole of his front mirrors
# it into the camera, and for two frames of each turn the green went pale and the M grey, like
# a flicker (the frames either side are as ever). Within SHEEN_FULL degrees of that angle the
# fill light's shine is taken off altogether, and from SHEEN_NONE degrees away it is as it
# always is: so walking, standing and waving he is drawn under the lights as they are.
SHEEN_AT, SHEEN_FULL, SHEEN_NONE = 27.0, 12.0, 25.0


def here():
    """This script's folder: from its own path, or beside the .blend when it is run from
    Blender's text editor."""
    path = globals().get("__file__", "")
    return os.path.dirname(os.path.abspath(path)) if os.path.isfile(path) else bpy.path.abspath("//scripts")


def library(name):
    """Another of the mascot's scripts, read for its functions without being run."""
    path = os.path.join(here(), name)
    scope = {"__name__": "__milo_lib__", "__file__": path}
    with open(path) as f:
        exec(compile(f.read(), path, "exec"), scope)
    return types.SimpleNamespace(**scope)


drawing = library("render_clips.py")
greeting = drawing.greeting
clips = drawing.clips
rig = drawing.rig
scene = bpy.context.scene

CYCLE = clips.PACE_FRAMES
WAVE_FRAMES = round(2.0 * clips.FPS) + 1  # Wave's first and last frame are both drawn
# The middle: each clip, the frames of it that are drawn, and the frame of the middle its first
# drawn frame is. WalkStop's last frame is Wave's first, and Wave's last is TurnAway's first:
# each is drawn once, as Wave's.
MIDDLE = (("WalkStop", 0, clips.HALT_FRAMES), ("Wave", 0, WAVE_FRAMES), ("TurnAway", 1, clips.AWAY_FRAMES - 1))
MIDDLE_FRAMES = sum(count for _, _, count in MIDDLE)
# The frames of TurnAway in which the lights are no longer full on, in A.
GOING_OUT = [f for f in range(1, clips.AWAY_FRAMES) if f > clips.STEP_OFF]


def key(frame):
    """Keys the pose as it stands. The rig is not turned: the clips turn him themselves."""
    greeting.key(frame)
    clips.key_run(frame)


def write_cycle(name, pose_at):
    """One cycle of a walk. The frame after the last is the first again."""
    clips.reset_pose()
    clips.new_action(drawing.DRAWN + name, CYCLE - 1, True)
    for frame in range(CYCLE):
        pose_at(frame / clips.FPS)
        key(frame)
    return CYCLE


def middle_pose(frame, lights_stay):
    """Sets the pose of a frame of the middle, and returns the clip it is of and that clip's frame."""
    at = 0
    for name, first, count in MIDDLE:
        if frame < at + count:
            f = first + frame - at
            if name == "WalkStop":
                clips.walk_stop(f / clips.FPS)
            elif name == "Wave":
                # Wave says nothing of the lights or of the way he faces: lit, and to the viewer
                clips.wave(f / clips.FPS)
                clips.set_brake(1.0)
                clips.set_turn(0.0)
            else:
                clips.turn_away(f / clips.FPS, lights_stay)
            return name, f
        at += count
    raise ValueError(frame)


def write_middle(name, lights_stay):
    clips.reset_pose()
    clips.new_action(drawing.DRAWN + name, MIDDLE_FRAMES - 1, False)
    for frame in range(MIDDLE_FRAMES):
        middle_pose(frame, lights_stay)
        key(frame)
    return MIDDLE_FRAMES


def across(point):
    """Where the camera draws a point of the scene: across and up, in pixels from the picture's
    lower left corner."""
    at = greeting.seen(point)
    return at[0] * scene.render.resolution_x, at[1] * scene.render.resolution_y


def shoes():
    """Where each shoe stands on the floor in the frame that is set. For each side, its heel's
    edge and its toe's (the two lines a shoe rolls over as he walks): where the camera draws it,
    or None while it is off the floor."""
    posed = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
    found = {}
    for side in ("L", "R"):
        bone = posed.pose.bones["foot." + side].matrix
        for edge, along in (("heel", -clips.HEEL), ("toe", clips.TOE)):
            point = posed.matrix_world @ bone @ clips.V(0.0, along, 0.0)
            found[side + " " + edge] = [round(v, 2) for v in across(point)] if point.z <= ON_THE_FLOOR else None
    return found


def shoes_through(frames):
    """shoes() for every frame of the clip that is live."""
    through = []
    for frame in range(frames):
        scene.frame_set(frame)
        through.append(shoes())
    return through


def measure_walk(steps):
    """What a walk's cycle is in the picture, from where its shoes stand (shoes_through): how
    far a frame carries each foot's edges back while they are on the floor, the left foot's and
    the right foot's; and, once every frame is moved `a_frame` on from the last, how far an edge
    that is on the floor strays from its place, across and up and down, at most."""
    pace = {"L": [], "R": []}
    for name in steps[0]:
        for frame in range(len(steps)):
            before, now = steps[frame - 1][name], steps[frame][name]
            if before and now:
                pace[name[0]].append(before[0] - now[0])
    pace = {side: sum(moves) / len(moves) for side, moves in pace.items()}

    def strays(a_frame):
        slip = drift = 0.0
        for name in steps[0]:
            # an edge's time on the floor is one run of frames, which may go round the cycle's end
            down = [frame for frame in range(len(steps)) if steps[frame][name]]
            start = next(frame for frame in down if not steps[frame - 1][name])
            run = [(start + k) % len(steps) for k in range(len(down))]
            x0, y0 = steps[start][name]
            for k, frame in enumerate(run):
                x, y = steps[frame][name]
                slip, drift = max(slip, abs(x - x0 + a_frame * k)), max(drift, abs(y - y0))
        return slip, drift

    return pace, strays


def halo_ends(folder, names):
    """Stops if anything is drawn on the border of a picture: the halo's edge, or the mascot's,
    would show there."""
    for name in names:
        alpha = greeting.alpha_of(os.path.join(folder, name))
        if max(alpha[0].max(), alpha[-1].max(), alpha[:, 0].max(), alpha[:, -1].max()) > 0:
            raise SystemExit("%s is not see-through at its edge: make ROOM larger, or HALO_ENDS in render_greeting.py." % name)


def facing():
    """How far he is turned in the frame that is set: degrees to his left of facing the viewer."""
    posed = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
    return degrees(posed.pose.bones["root"].rotation_euler.z)


def sheen(turned):
    """How much of its shine the fill light keeps while he is turned that far: see SHEEN_AT."""
    return 1.0 - clips.smooth((SHEEN_NONE - abs(turned - SHEEN_AT)) / (SHEEN_NONE - SHEEN_FULL))


def draw(folder, name, numbers):
    """Draws those frames of one of the clips written here into a folder."""
    drawing.play(name)
    os.makedirs(folder, exist_ok=True)
    fill = bpy.data.objects["MilO_Light_Fill"].data
    as_it_is = fill.specular_factor
    names = []
    for frame in numbers:
        scene.frame_set(frame)
        fill.specular_factor = as_it_is * sheen(facing())
        scene.render.filepath = os.path.join(folder, "%04d" % frame)
        bpy.ops.render.render(write_still=True)
        names.append("%04d.png" % frame)
    fill.specular_factor = as_it_is
    halo_ends(folder, names)


def lowest(frames):
    """The lowest point of the mascot over the clip that is live, in pixels above the picture's
    lower edge."""
    low = float("inf")
    for frame in range(frames):
        scene.frame_set(frame)
        low = min(low, drawing.seen_in_pixels()[1])
    return low


def main():
    asked = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    plain, measure = "--plain" in asked, "--measure" in asked
    for flag in ("--plain", "--measure"):
        if flag in asked:
            asked.remove(flag)
    every = 1
    if "--every" in asked:
        at = asked.index("--every")
        every = int(asked[at + 1])
        del asked[at:at + 2]
    if not 1 <= len(asked) <= 2:
        raise SystemExit("After '--': a folder for the frames, and if wanted how many pixels there are to the dp, --plain, --measure and --every <n>.")
    folder = os.path.abspath(asked[0])
    to_the_dp = float(asked[1]) if len(asked) > 1 else greeting.PX_PER_DP
    pixels = round(greeting.MASCOT_DP * to_the_dp)
    shown = rig.animation_data.action

    frames = {"In": write_cycle("In", clips.walk_in), "Out": write_cycle("Out", clips.walk_off),
              "OutLit": write_cycle("OutLit", lambda t: clips.walk_off(t, 1.0)),
              "Middle": write_middle("Middle", False), "MiddleLit": write_middle("MiddleLit", True)}
    width, height, axis, standing = drawing.set_picture(frames, pixels, drawing.MARGIN + round(ROOM * pixels))
    if not plain:
        greeting.halo_see_through()

    # where the shoes stand on the floor, frame by frame, and what the two walks are in the picture
    stood, soles, walks = {}, {}, {}
    for name, part in (("In", "in"), ("Middle", "middle"), ("Out", "out")):
        drawing.play(name)
        stood[part] = shoes_through(frames[name])
        soles[part] = round(lowest(frames[name]), 1)
    a_unit = across(clips.pace_way(0.0))[0] - across((0.0, 0.0, 0.0))[0]  # a unit of the way he goes, on the floor under him
    for part in ("in", "out"):
        pace, strays = measure_walk(stood[part])
        walks[part] = pace, strays
    # One number moves both walks: the mean of what a frame carries the four feet back.
    a_frame = sum(pace[side] for pace, _ in walks.values() for side in pace) / 4
    stride = a_frame * CYCLE

    if measure:
        pass
    elif plain:
        draw(os.path.join(folder, "out-lit-plain"), "OutLit", range(CYCLE))
    else:
        draw(os.path.join(folder, "in"), "In", range(CYCLE))
        draw(os.path.join(folder, "middle"), "Middle", range(0, MIDDLE_FRAMES, every))
        draw(os.path.join(folder, "out"), "Out", range(CYCLE))
        at = MIDDLE_FRAMES - (clips.AWAY_FRAMES - 1)  # the frame of the middle that is TurnAway's frame 1
        draw(os.path.join(folder, "middle-lit"), "MiddleLit", [at + f - 1 for f in GOING_OUT])
        draw(os.path.join(folder, "out-lit"), "OutLit", range(CYCLE))

    for name in frames:
        bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + name])
    rig.animation_data.action = shown

    print("picture: %d x %d pixels, %g to the dp, %d frames a second, see-through, %s"
          % (width, height, to_the_dp, clips.FPS, "no halo" if plain else "with a halo round lit brake lights"))
    print("he stands %d pixels high; his axis is %d pixels from the left edge; standing, his soles are %d pixels above the lower edge; "
          "walking in %.1f (%.1f dp), walking off %.1f (%.1f dp)"
          % (pixels, axis, standing, soles["in"], soles["in"] / to_the_dp, soles["out"], soles["out"] / to_the_dp))
    print("the walks: %d frames to a cycle. A cycle carries him %.2f pixels (%.2f dp), a frame %.3f pixels; "
          "%.2f units of the model, of which one is %.1f pixels under him" % (CYCLE, stride, stride / to_the_dp, a_frame, clips.PACE_STRIDE, a_unit))
    for part, what in (("in", "walking in"), ("out", "walking off")):
        pace, strays = walks[part]
        slip, drift = strays(a_frame)
        print("  %s: a frame carries the left foot %.3f pixels back and the right foot %.3f. Moved %.3f with every frame, an edge of a "
              "shoe that is on the floor stays within %.2f pixels (%.2f dp) of its place across, %.2f (%.2f dp) up and down"
              % (what, pace["L"], pace["R"], a_frame, slip, slip / to_the_dp, drift, drift / to_the_dp))
    print("middle: %d frames (%s)%s" % (MIDDLE_FRAMES, ", ".join("%s %d" % (name, count) for name, _, count in MIDDLE),
                                        "" if every == 1 else ", every %d. drawn" % every))
    if plain or measure:
        return

    told = dict(pixels_to_the_dp=to_the_dp, width=width, height=height, axis=axis, standing_soles=standing,
                soles=soles, frames_a_second=clips.FPS, cycle_frames=CYCLE, stride=round(stride, 3),
                middle_frames=MIDDLE_FRAMES, middle_every=every,
                clips=[dict(name=name, frames=count) for name, _, count in MIDDLE],
                lights_on=1, step_off=MIDDLE_FRAMES - (clips.AWAY_FRAMES - 1) + clips.STEP_OFF - 1,
                lit_instead=[MIDDLE_FRAMES - (clips.AWAY_FRAMES - 1) + f - 1 for f in GOING_OUT],
                stood=stood)
    with open(os.path.join(folder, "wave2.json"), "w") as f:
        json.dump(told, f, indent=1)


if __name__ != "__milo_lib__":
    main()

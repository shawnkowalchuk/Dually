# render_moonwalk.py - draws the app's second greeting, the moonwalk, as pictures: the mascot
# gliding backward in his hat, and what he does in the middle of the screen. Run it after the
# other scripts, with Blender WITHOUT its window, and name a folder for the frames:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_moonwalk.py -- <folder> [<pixels to the dp>] [--every <n>]
#
# It draws into <folder>. pack_moonwalk.py makes the two pictures the app carries out of that,
# and preview_moonwalk.py lays it over a picture of Home as a film:
#
#   glide/0000.png ...    one cycle of the clip "Moonwalk", drawn on the spot. The frame after
#                         the last is the first again
#   middle/0000.png ...   the clips "MoonwalkStop", "KickSpin" and "MoonwalkOn" one after the
#                         other. Its first frame is the frame the glide would show next, and the
#                         frame after its last is the glide's first
#   moonwalk.json         what the frames are: the picture's size, where he stands in it, how far
#                         one cycle of the glide carries him. Both other scripts read it
#
# He is drawn as the greeting draws him: MASCOT_DP high from the studio's camera, under its
# lights, on a see-through background, turned GLIDE_ANGLE degrees from the viewer. He glides to
# the left and faces the way he came from, so he is turned to the right. With no pixels to the
# dp named there are 2.5 of them, which is what the app's pictures have and what pack_moonwalk.py
# asks for; the preview asks for what its picture of Home has. --every 3 draws only every third
# frame of the middle, for a quick look.
#
# The pictures hold the halo round the brake lights where they are lit (halo_see_through in
# render_greeting.py), so they are for a dark ground.
#
# NOTHING IS SAVED, and nothing here writes into the app: that is pack_moonwalk.py's step.

import json
import os
import sys
import types
import bpy

# Degrees he is turned from the viewer while he glides: as far as in the greeting's walk, the
# other way.
GLIDE_ANGLE = 55.0
# Clear room round him in the picture, for the halo to fade in: this part of his height.
ROOM = 0.14


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

GLIDE_FRAMES = 2 * clips.MOON_HALF
MIDDLE = (("MoonwalkStop", clips.moonwalk_stop, clips.STOP_FRAMES), ("KickSpin", clips.kick_spin, clips.KICK_FRAMES),
          ("MoonwalkOn", clips.moonwalk_on, clips.ON_FRAMES))


def key(frame):
    """Keys the pose as it stands, with the whole mascot turned to the right."""
    greeting.key(frame, angle=-GLIDE_ANGLE)
    clips.key_dance(frame)


def write_glide():
    clips.reset_pose()
    clips.new_action(drawing.DRAWN + "Glide", GLIDE_FRAMES - 1, True)
    for frame in range(GLIDE_FRAMES):
        clips.moonwalk(frame / clips.FPS)
        key(frame)
    return GLIDE_FRAMES


def write_middle():
    """The three clips one after the other. Each one's last frame is the next one's first, and
    is written once."""
    clips.reset_pose()
    total = sum(frames for _, _, frames in MIDDLE)
    clips.new_action(drawing.DRAWN + "Middle", total - 1, False)
    at = 0
    for _, pose_at, frames in MIDDLE:
        for frame in range(frames):
            pose_at(frame / clips.FPS)
            key(at + frame)
        at += frames
    return total


def toe(side):
    """Where the camera draws the point a shoe stands on when it is up on its toe: across and
    up, in pixels from the picture's lower left corner."""
    posed = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
    at = greeting.seen(posed.matrix_world @ posed.pose.bones["foot." + side].matrix @ clips.V(0.0, clips.TOE, 0.0))
    return at[0] * scene.render.resolution_x, at[1] * scene.render.resolution_y


def measure_glide():
    """How far one cycle of the glide carries him in the picture, and how well the foot that is
    up on its toe stays where it is once every frame is moved that far on from the last. The
    glide must be live. Each foot is on its toe for half the cycle: the left one first."""
    half = GLIDE_FRAMES // 2
    paths = {}
    for side, start in (("L", 0), ("R", half)):
        paths[side] = []
        for step in range(half + 1):
            scene.frame_set((start + step) % GLIDE_FRAMES)
            paths[side].append(toe(side))
    stride = sum(path[-1][0] - path[0][0] for path in paths.values())
    a_frame = stride / GLIDE_FRAMES
    slips = [abs(x - path[0][0] - a_frame * step) for path in paths.values() for step, (x, _) in enumerate(path)]
    drifts = [abs(y - path[0][1]) for path in paths.values() for _, y in path]
    return stride, max(slips), max(drifts)


def halo_ends(folder):
    """Stops if anything is drawn on the border of a picture in the folder: the halo's edge, or
    the mascot's, would show there."""
    for name in sorted(os.listdir(folder)):
        alpha = greeting.alpha_of(os.path.join(folder, name))
        if max(alpha[0].max(), alpha[-1].max(), alpha[:, 0].max(), alpha[:, -1].max()) > 0:
            raise SystemExit("%s is not see-through at its edge: make ROOM larger, or HALO_ENDS in render_greeting.py." % name)


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
    every = 1
    if "--every" in asked:
        at = asked.index("--every")
        every = int(asked[at + 1])
        del asked[at:at + 2]
    if not 1 <= len(asked) <= 2:
        raise SystemExit("After '--': a folder for the frames, and if wanted how many pixels there are to the dp.")
    folder = os.path.abspath(asked[0])
    to_the_dp = float(asked[1]) if len(asked) > 1 else greeting.PX_PER_DP
    pixels = round(greeting.MASCOT_DP * to_the_dp)
    shown = rig.animation_data.action

    frames = {"Glide": write_glide(), "Middle": write_middle()}
    width, height, axis, _ = drawing.set_picture(frames, pixels, drawing.MARGIN + round(ROOM * pixels))
    greeting.halo_see_through()

    drawing.play("Glide")
    stride, slip, drift = measure_glide()
    soles = lowest(frames["Glide"])
    drawing.draw(os.path.join(folder, "glide"), frames["Glide"])
    drawing.play("Middle")
    os.makedirs(os.path.join(folder, "middle"), exist_ok=True)
    for frame in range(0, frames["Middle"], every):
        scene.frame_set(frame)
        scene.render.filepath = os.path.join(folder, "middle", "%04d" % frame)
        bpy.ops.render.render(write_still=True)
    for part in ("glide", "middle"):
        halo_ends(os.path.join(folder, part))

    for name in frames:
        bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + name])
    rig.animation_data.action = shown

    told = dict(pixels_to_the_dp=to_the_dp, width=width, height=height, axis=axis, soles=round(soles, 1),
                frames_a_second=clips.FPS, glide_frames=frames["Glide"], stride=round(stride, 2),
                middle_frames=frames["Middle"], middle_every=every,
                clips=[dict(name=name, frames=count) for name, _, count in MIDDLE])
    with open(os.path.join(folder, "moonwalk.json"), "w") as f:
        json.dump(told, f, indent=2)
    print("picture: %d x %d pixels, %g to the dp, %d frames a second, see-through, with a halo round lit brake lights"
          % (width, height, to_the_dp, clips.FPS))
    print("he stands %d pixels high; his axis is %d pixels from the left edge; gliding, his soles are %.1f pixels (%.1f dp) above the lower edge"
          % (pixels, axis, soles, soles / to_the_dp))
    print("glide: %d frames, one cycle. It carries him %.1f pixels (%.2f dp), %.2f units of the model"
          % (frames["Glide"], stride, stride / to_the_dp, clips.MOON_STRIDE))
    print("  moved a %dth of that with every frame, the foot on its toe stays within %.1f pixels of its place across, %.1f up and down"
          % (frames["Glide"], slip, drift))
    print("middle: %d frames (%s)%s" % (frames["Middle"], ", ".join("%s %d" % (name, count) for name, _, count in MIDDLE),
                                        "" if every == 1 else ", every %d. drawn" % every))


if __name__ != "__milo_lib__":
    main()

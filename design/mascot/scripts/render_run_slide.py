# render_run_slide.py - draws the run and the slide as pictures, for a preview of them: the
# mascot running in, running on the spot with his shoes smoking, bolting, braking and sliding
# out with his brake lights lit. Run it after the other scripts, with Blender WITHOUT its
# window, and name a folder for the frames:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- <folder> [<pixels>] [--smoke with|without|alone] [--plain] [--every <n>] [--frames <first>-<last>] [--turn <degrees>]
#
# It draws the whole of it (act_pose in animate_milo.py: the clips Run, SpinUp, Burnout,
# BrakeSlide and Slide one after the other) into <folder>, and preview_run_slide.py makes a
# film of that:
#
#   with/0000.png ...     him and the smoke, each in front of the other as they are. This is
#                         what is drawn when --smoke is not said
#   without/0000.png ...  him alone: what the app would carry if it drew the smoke itself
#   alone/0000.png ...    the smoke alone, with nothing of him: not even where he would hide
#                         a puff of it
#   run-slide.json        what the frames are: the picture's size, where he stands in it, which
#                         clip each frame is, the frame the brake lights come on in, and for
#                         every frame how far he is from the spot he runs on, in pixels.
#                         preview_run_slide.py reads it
#
# EVERY FRAME IS DRAWN ON THE SPOT, as the walk and the moonwalk are: he stands on one place in
# the picture, and whoever shows the frames moves the picture by what run-slide.json says. The
# smoke stays where it was made, so in these pictures it goes back past him as fast as he goes
# forward. That is why the picture is much wider to his left than to his right (ROOM): the
# smoke he leaves behind has to be in it for as long as it is on a screen.
#
# One run draws one of the three; all three are the same size of picture, with him on the same
# spot. <pixels> is how high the standing mascot is drawn; with none said it is PIXELS, the
# height preview_run_slide.py shows him at on a phone's screen. --every 3 draws every third
# frame only and --frames 70-100 only those, for a quick look; --turn 40 turns the whole
# mascot, smoke and all, that many degrees further to his left, to look at him from another side.
#
# He is drawn as the greeting draws him: from the studio's camera, under its lights, on a
# see-through background, the dials' black faces dulled, turned RUN_ANGLE degrees to the right.
# The pictures hold the halo round the brake lights where they are lit (halo_see_through in
# render_greeting.py), so they are for a dark ground. With --plain they are drawn without it,
# into with-plain/ and so on: those are right on any ground, white too.
#
# THE APP DOES NOT SHOW THIS. It is the drawing step for a preview; nothing here is packed into
# pictures the app carries. NOTHING IS SAVED.

import json
import os
import sys
import types
import bpy
from math import atan2, degrees
from mathutils import Matrix

# How high the standing mascot is drawn when no height is said: as high as the app's greeting
# shows him, 150 dp, on a screen with 2.625 pixels to the dp.
PIXELS = 394
# Degrees he is turned from the room's front while he runs, to the right. With RUN_SLANT in
# animate_milo.py it makes the way he goes level in the picture: the studio's camera stands a
# little to one side, and "level" is 6.5 degrees off the room's own left and right.
RUN_ANGLE = 58.0
# The picture, in parts of his standing height: how far it reaches to the left and to the right
# of the point he stands on, above his soles and below them.
ROOM = dict(left=2.45, right=0.85, up=1.45, down=0.16)
SMOKE = ("with", "without", "alone")


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
ACT = "RunSlide"


def write(turn):
    """The whole of it as one clip in memory, keyed on every frame, the whole mascot turned
    RUN_ANGLE degrees to the right and `turn` degrees further. Returns how many frames it has."""
    clips.reset_pose()
    clips.new_action(drawing.DRAWN + ACT, clips.ACT_FRAMES - 1, False)
    for frame in range(clips.ACT_FRAMES):
        clips.act_pose(frame)
        greeting.key(frame, angle=-(RUN_ANGLE + turn))
        clips.key_run(frame)
    return clips.ACT_FRAMES


def set_picture(pixels):
    """The picture: a fixed room round him. Returns its size, and where he stands in it: his
    axis from the left edge and his soles above the lower edge."""
    axis, soles = round(ROOM["left"] * pixels), round(ROOM["down"] * pixels)
    width = 2 * round((ROOM["left"] + ROOM["right"]) * pixels / 2)
    height = 2 * round((ROOM["up"] + ROOM["down"]) * pixels / 2)
    greeting.set_stage(width, height, pixels)
    greeting.slide_view("shift_x", lambda: axis / width - greeting.seen((0, 0, 0))[0])
    greeting.slide_view("shift_y", lambda: (soles - drawing.seen_in_pixels()[1]) / height)
    return width, height, axis, soles


def across(point):
    """Where the camera draws a point of the scene, in pixels from the picture's lower left corner."""
    at = greeting.seen(point)
    return at[0] * scene.render.resolution_x, at[1] * scene.render.resolution_y


def measure_way():
    """A unit of the way he goes, on the floor under him, as the camera draws it: how many
    pixels across and how many up; and the same four units behind him, where the smoke of the
    run on the spot is when he has slid out. The clip must be live."""
    scene.frame_set(0)
    turned = Matrix.Rotation(rig.rotation_euler.z, 3, "Z")
    way = turned @ clips.run_way()
    here_, on, behind, further = (across(way * far) for far in (0.0, 1.0, -4.0, -3.0))
    return (on[0] - here_[0], on[1] - here_[1]), (further[0] - behind[0], further[1] - behind[1]), degrees(atan2(way.y, way.x))


def measure_run(a_frame):
    """How well a foot that is planted keeps its place once every frame of the run is moved
    `a_frame` pixels on from the last: for each foot, in the frames it is on the floor, the
    point of the floor under its ankle. Returns the largest slip across and up and down. The
    clip must be live."""
    planted = round(clips.RUN_DUTY * clips.RUN_FRAMES)
    slips, drifts = [], []
    for side, start in (("L", 0), ("R", clips.RUN_FRAMES // 2)):
        path = []
        for step in range(planted):
            scene.frame_set((start + step) % clips.RUN_FRAMES)
            posed = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
            under = posed.matrix_world @ posed.pose.bones["foot." + side].matrix.translation
            under.z = 0.0  # the foot's bone is lifted while its shoe is tipped: the floor under it is asked
            path.append(across(under))
        slips += [abs(x - path[0][0] + a_frame * step) for step, (x, _) in enumerate(path)]
        drifts += [abs(y - path[0][1]) for _, y in path]
    return max(slips), max(drifts)


def show_smoke(how):
    """Draws him and the smoke, him alone, or the smoke alone."""
    bpy.data.collections["MilO_Smoke"].hide_render = how == "without"
    if how == "alone":
        for ob in bpy.data.collections["MilO_Mascot"].objects:
            ob.hide_render = True


def edges(folder, names):
    """Which of the pictures have something drawn on their right, upper or lower edge, where
    nothing should be: the halo's end or the smoke's would show there. The left edge is not
    asked: the smoke he leaves behind goes out of the picture there, once he is far enough."""
    touched = []
    for name in names:
        alpha = greeting.alpha_of(os.path.join(folder, name))
        if max(alpha[0].max(), alpha[-1].max(), alpha[:, -1].max()) > 0:
            touched.append(name)
    return touched


def main():
    asked = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    plain = "--plain" in asked
    if plain:
        asked.remove("--plain")
    said = {"--smoke": "with", "--every": "1", "--turn": "0", "--frames": ""}
    for flag in said:
        if flag in asked:
            at = asked.index(flag)
            said[flag] = asked[at + 1] if at + 1 < len(asked) else ""
            del asked[at:at + 2]
    how = said["--smoke"]
    if how not in SMOKE or not said["--every"].isdigit() or not 1 <= len(asked) <= 2:
        raise SystemExit("After '--': a folder for the frames, and if wanted how high he is drawn in pixels, --smoke with, "
                         "without or alone, --plain, --every <n>, --frames <first>-<last> and --turn <degrees>.")
    every, turn = int(said["--every"]), float(said["--turn"])
    only = [int(number) for number in said["--frames"].split("-")] if said["--frames"] else None
    folder = os.path.abspath(asked[0])
    pixels = int(asked[1]) if len(asked) > 1 else PIXELS
    where = how + ("-plain" if plain else "")
    shown = rig.animation_data.action
    blown = bpy.data.objects["MilO_Smoke_Rig"]
    stored = blown.animation_data.action if blown.animation_data else None

    frames = write(turn)
    width, height, axis, soles = set_picture(pixels)
    left, low, right, high = drawing.reach({ACT: frames})
    if axis + left < 2 or axis + right > width - 2 or soles + high > height - 2 or soles + low < 2:
        raise SystemExit("He reaches out of the picture: %d to the left of his axis, %d to the right, %d above his soles, "
                         "%d below. Make ROOM larger." % (-left, right, high, -low))
    if not plain:
        greeting.halo_see_through()
    drawing.play(ACT)
    (a_unit, rises), (behind, behind_rises), heading = measure_way()
    travel = [round(clips.run_travel(frame) * a_unit, 2) for frame in range(frames)]
    a_frame = clips.RUN_STRIDE / clips.RUN_FRAMES * a_unit
    slip, drift = measure_run(a_frame)
    puffs = clips.write_smoke(drawing.DRAWN + "Smoke")
    drawing.play(ACT)  # the smoke's clip was worked out by posing him: the clip is live again
    show_smoke(how)

    os.makedirs(os.path.join(folder, where), exist_ok=True)
    first, last = only if only else (0, frames - 1)
    last = min(last, frames - 1)
    names = []
    for frame in range(first, last + 1, every):
        scene.frame_set(frame)
        scene.render.filepath = os.path.join(folder, where, "%04d" % frame)
        bpy.ops.render.render(write_still=True)
        names.append("%04d.png" % frame)
    touched = edges(os.path.join(folder, where), names)

    bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + ACT])
    bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + "Smoke"])
    rig.animation_data.action = shown
    if stored:
        blown.animation_data.action = stored

    parts = []
    for frame in range(frames):
        name, _ = clips.act_part(frame)
        if not parts or parts[-1]["name"] != name:
            parts.append(dict(name=name, first=frame, frames=0))
        parts[-1]["frames"] += 1
    told = dict(pixels=pixels, width=width, height=height, axis=axis, soles=soles, frames_a_second=clips.FPS,
                frames=frames, parts=parts, brake_frame=clips.ACT_BRAKE_ON, a_unit=round(a_unit, 3),
                stride=round(clips.RUN_STRIDE * a_unit, 2), travel=travel, drawn={})
    # What an earlier run drew into this folder is kept on the list, if it was the same picture.
    try:
        with open(os.path.join(folder, "run-slide.json")) as f:
            before = json.load(f)
        if all(before.get(key) == value for key, value in json.loads(json.dumps(told)).items() if key != "drawn"):
            told["drawn"] = before.get("drawn", {})
    except (OSError, ValueError):
        pass
    told["drawn"][where] = dict(first=first, last=last, every=every, turn=turn)
    with open(os.path.join(folder, "run-slide.json"), "w") as f:
        json.dump(told, f, indent=2)

    print("picture: %d x %d pixels, %d frames a second, see-through, %s; %s"
          % (width, height, clips.FPS, "no halo" if plain else "with a halo round lit brake lights",
             {"with": "him and the smoke", "without": "him without the smoke", "alone": "the smoke alone"}[how]))
    print("he stands %d pixels high; his axis is %d pixels from the left edge, his soles %d above the lower edge"
          % (pixels, axis, soles))
    print("  he reaches %d pixels to the left of his axis and %d to the right, %d above his soles"
          % (round(-left), round(right), round(high)))
    print("the whole: %d frames, %.2f seconds%s: %s" % (
        frames, frames / clips.FPS, "" if every == 1 and not only else ", drawn: every %d. from %d to %d" % (every, first, last),
        ", ".join("%s %d from frame %d" % (part["name"], part["frames"], part["first"]) for part in parts)))
    print("  the brake lights come on in frame %d, %.3f seconds in; the smoke is %d puffs" % (clips.ACT_BRAKE_ON, clips.ACT_BRAKE_ON / clips.FPS, puffs))
    print("the way he goes: %.1f degrees from the room's left-to-right. A unit of it under him is %.1f pixels across and %.1f up; "
          "four units behind him it is %.1f across and %.1f up" % (heading, a_unit, rises, behind, behind_rises))
    print("  a cycle of the run carries him %.1f pixels, a frame %.2f; moved that far with every frame, a planted foot "
          "stays within %.1f pixels of its place across, %.1f up and down" % (clips.RUN_STRIDE * a_unit, a_frame, slip, drift))
    print("  at the brake he is %.0f pixels on from the spot he ran on; at the last frame, %.0f" % (travel[clips.ACT_BRAKE_ON], travel[-1]))
    if touched:
        print("  DRAWN ON THE PICTURE'S RIGHT, UPPER OR LOWER EDGE in %d frames, the first %s: make ROOM larger" % (len(touched), touched[0]))


if __name__ != "__milo_lib__":
    main()

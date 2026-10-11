# render_party.py - draws the party as pictures, for a preview of it: the mascot in his party
# cap, bringing out a party popper and firing it, and the confetti. Run it after the other
# scripts, with Blender WITHOUT its window, and name a folder for the frames:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_party.py -- <folder> [<pixels>] [--confetti with|without|alone] [--every <n>] [--frames <first>-<last>] [--turn <degrees>]
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_party.py -- <folder> [<pixels>] --cap
#
# It draws the clip "OneYear" into <folder>, and preview_party.py makes a film of that:
#
#   with/0000.png ...     him and the confetti, each in front of the other as they are. This is
#                         what is drawn when --confetti is not said
#   without/0000.png ...  him alone: what the app would carry if it drew the confetti itself
#   alone/0000.png ...    the confetti alone, with nothing of him: not even where he would
#                         hide a piece of it
#   party.json            what the frames are: the picture's size, where he stands in it, how
#                         many frames there are, the frame the popper goes off on and where
#                         the popper's mouth then is in the picture, and which frames of each
#                         of the three were drawn. preview_party.py reads it
#
# With --cap it draws two stills instead, of him standing in his party cap, and nothing else:
#
#   cap/0000.png          from the front
#   cap/0001.png          three quarters on, turned CAP_TURN degrees to his left
#
# They are CAP_PIXELS high if no height is said, and their picture is measured as
# render_clips.py measures it: as far as he reaches, and a margin.
#
# One run draws one of the three; all three are the same size of picture, with him on the same
# spot. <pixels> is how high the standing mascot is drawn, without his cap; with none said it is
# PIXELS, at which the picture is as wide as a phone's screen in preview_party.py. The picture
# is not measured from what is in it, as render_clips.py measures: it is a fixed room round him
# (ROOM), as many times his height, because confetti that drifts out of the picture is to drift
# out of it. --every 3 draws every third frame only and --frames 60-84 only those, for a quick
# look; --turn 40 turns the whole mascot, confetti and all, that many degrees to his left, to
# look at him from the side.
#
# He is drawn as the greeting draws him: from the studio's camera, under its lights, on a
# see-through background, the dials' black faces dulled. He faces the viewer and no brake light
# is lit, so there is no halo, and the pictures are right on any ground.
#
# THE APP DOES NOT SHOW THIS. It is the drawing step for a preview; nothing here is packed into
# pictures the app carries. NOTHING IS SAVED.

import json
import os
import sys
import types
import bpy

# How high the standing mascot is drawn when no height is said.
PIXELS = 600
# The picture, in parts of his standing height: how far it reaches to each side of the point he
# stands on, above his soles and below them. At PIXELS it is 1080 pixels wide.
ROOM = dict(side=1080 / (2 * PIXELS), up=2.2, down=0.1)
CONFETTI = ("with", "without", "alone")
# The stills of the cap: how high he is drawn, and how far he is turned in the second.
CAP_PIXELS, CAP_TURN = 560, 40.0


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
CLIP = "OneYear"


def write(turn):
    """The party's clip in memory, keyed on every frame, the whole mascot turned `turn` degrees
    to his left. Returns how many frames it has."""
    clips.reset_pose()
    clips.new_action(drawing.DRAWN + CLIP, clips.PARTY_FRAMES, False)
    for frame in range(clips.PARTY_FRAMES + 1):
        clips.one_year(frame / clips.FPS)
        greeting.key(frame, angle=-turn)
        clips.key_party(frame)
    return clips.PARTY_FRAMES + 1


def set_picture(pixels):
    """The picture: a fixed room round him. Returns its size, and where he stands in it: his
    axis from the left edge and his soles above the lower edge."""
    width = 2 * round(ROOM["side"] * pixels)
    height = 2 * round((ROOM["up"] + ROOM["down"]) * pixels / 2)
    soles = round(ROOM["down"] * pixels)
    greeting.set_stage(width, height, pixels)  # it leaves him standing, his axis in the middle
    greeting.slide_view("shift_y", lambda: (soles - drawing.seen_in_pixels()[1]) / height)
    return width, height, width // 2, soles


def show_confetti(how):
    """Draws him and the confetti, him alone, or the confetti alone."""
    bpy.data.collections["MilO_Confetti"].hide_render = how == "without"
    if how == "alone":
        for ob in bpy.data.collections["MilO_Mascot"].objects:
            ob.hide_render = True


def draw_cap(folder, pixels):
    """The two stills of him standing in his cap."""
    turns = (0.0, CAP_TURN)
    clips.reset_pose()
    clips.new_action(drawing.DRAWN + "Cap", len(turns) - 1, False)
    for frame, turn in enumerate(turns):
        clips.neutral()
        clips.set_party(cap=1.0)
        greeting.key(frame, angle=-turn)
        clips.key_party(frame)
    width, height, _, _ = drawing.set_picture({"Cap": len(turns)}, pixels)
    drawing.play("Cap")
    drawing.draw(os.path.join(folder, "cap"), len(turns))
    bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + "Cap"])
    print("the cap: 2 stills of %d x %d pixels, he %d high, see-through: from the front, and turned %g degrees to his left"
          % (width, height, pixels, CAP_TURN))


def main():
    asked = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    cap = "--cap" in asked
    if cap:
        asked.remove("--cap")
    said = {"--confetti": "with", "--every": "1", "--turn": "0", "--frames": ""}
    for flag in said:
        if flag in asked:
            at = asked.index(flag)
            said[flag] = asked[at + 1] if at + 1 < len(asked) else ""
            del asked[at:at + 2]
    how = said["--confetti"]
    if how not in CONFETTI or not said["--every"].isdigit() or not 1 <= len(asked) <= 2:
        raise SystemExit("After '--': a folder for the frames, and if wanted how high he is drawn in pixels, "
                         "--confetti with, without or alone, --every <n>, --frames <first>-<last> and --turn <degrees>; "
                         "or --cap for two stills of the cap.")
    every, turn = int(said["--every"]), float(said["--turn"])
    only = [int(number) for number in said["--frames"].split("-")] if said["--frames"] else None
    folder = os.path.abspath(asked[0])
    shown = rig.animation_data.action
    if cap:
        draw_cap(folder, int(asked[1]) if len(asked) > 1 else CAP_PIXELS)
        rig.animation_data.action = shown
        return
    pixels = int(asked[1]) if len(asked) > 1 else PIXELS
    thrown = bpy.data.objects["MilO_Confetti_Rig"]
    stored = thrown.animation_data.action if thrown.animation_data else None

    frames = write(turn)
    width, height, axis, soles = set_picture(pixels)
    left, low, right, high = drawing.reach({CLIP: frames})
    if axis + left < 2 or axis + right > width - 2 or soles + high > height - 2 or soles + low < 2:
        raise SystemExit("He reaches out of the picture: %d to the left of his axis, %d to the right, %d above his soles, "
                         "%d below. Make ROOM larger." % (-left, right, high, -low))
    drawing.play(CLIP)
    mouth, way = clips.write_confetti(drawing.DRAWN + "Confetti")
    # Where the camera draws the popper's mouth in the frame of the pop, which the scene is at
    # now: across from the left edge and up from the lower one, in pixels.
    seen = greeting.seen(rig.matrix_world @ mouth)
    mouth_at = (round(seen[0] * width, 1), round(seen[1] * height, 1))
    show_confetti(how)

    os.makedirs(os.path.join(folder, how), exist_ok=True)
    first, last = only if only else (0, frames - 1)
    for frame in range(first, min(last, frames - 1) + 1, every):
        scene.frame_set(frame)
        scene.render.filepath = os.path.join(folder, how, "%04d" % frame)
        bpy.ops.render.render(write_still=True)

    bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + CLIP])
    bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + "Confetti"])
    rig.animation_data.action = shown
    if stored:
        thrown.animation_data.action = stored

    told = dict(pixels=pixels, width=width, height=height, axis=axis, soles=soles, frames_a_second=clips.FPS,
                frames=frames, hold_frames=clips.PARTY_HOLD, pop_frame=clips.PARTY_POP, mouth=mouth_at, drawn={})
    # What an earlier run drew into this folder is kept on the list, if it was the same picture.
    try:
        with open(os.path.join(folder, "party.json")) as f:
            before = json.load(f)
        if all(before.get(key) == value for key, value in json.loads(json.dumps(told)).items() if key != "drawn"):
            told["drawn"] = before.get("drawn", {})
    except (OSError, ValueError):
        pass
    told["drawn"][how] = dict(first=first, last=min(last, frames - 1), every=every, turn=turn)
    with open(os.path.join(folder, "party.json"), "w") as f:
        json.dump(told, f, indent=2)
    print("picture: %d x %d pixels, %d frames a second, see-through; %s"
          % (width, height, clips.FPS, {"with": "him and the confetti", "without": "him without the confetti", "alone": "the confetti alone"}[how]))
    print("he stands %d pixels high; his axis is %d pixels from the left edge, his soles %d above the lower edge"
          % (pixels, axis, soles))
    print("  he reaches %d pixels to the left of his axis and %d to the right, %d above his soles"
          % (round(-left), round(right), round(high)))
    print("%s: %d frames, %.2f seconds%s; he stands still for the first %d; the popper goes off on frame %d, %.3f seconds in"
          % (CLIP, frames, (frames - 1) / clips.FPS,
             "" if every == 1 and not only else ", drawn: every %d. from %d to %d" % (every, first, min(last, frames - 1)),
             clips.PARTY_HOLD, clips.PARTY_POP, clips.PARTY_POP / clips.FPS))
    print("  the popper's mouth is then %g pixels from the left edge and %g above the lower edge; in his own room it is at "
          "(%.2f, %.2f, %.2f) and points (%.2f, %.2f, %.2f)" % (*mouth_at, *mouth, *way))


if __name__ != "__milo_lib__":
    main()

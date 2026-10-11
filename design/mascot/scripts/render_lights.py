# render_lights.py - draws the brake lights to be shown: the mascot from behind with them off
# and lit, from behind and to one side with them lit, and a turn-around in which they come on
# while his back is to the viewer. Run it after the other scripts, with Blender WITHOUT its
# window, and name a folder for the frames:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_lights.py -- <folder>
#
# It draws into <folder>, and "pack_clips.py lights <folder>" makes the pictures in
# design/mascot/renders/ out of that:
#
#   see-through/0000.png to 0002.png   the three stills on a see-through background, as the
#                                      other pictures are drawn. No halo: a see-through
#                                      picture cannot hold one (on_ground in render_greeting.py)
#   dark/0000.png to 0002.png          the same three on the app's dark page, with the halo
#   dark/turn/0000.png ...             the turn-around on the dark page, with the halo
#
# These pictures are of him standing, and no stored clip is that with the lights lit (the
# moonwalk's clips light them, in the middle of a dance). So this script writes clips of its
# own, in memory: he stands, his slider "brake" is keyed, and the whole mascot is turned about
# the point he stands on, as render_greeting.py turns him for the walk. The turn-around is a
# turntable, not an animation: his feet turn with him.
#
# NOTHING IS SAVED.

import os
import sys
import types
import bpy

# How high the standing mascot is drawn in the stills and in the turn-around.
STILL_PIXELS = 560
TURN_PIXELS = 320
# The app's dark page (MiloColors, and --page in website/styles.css).
DARK = "121316"
# The stills: how far he is turned from facing the viewer, and how far the lights are on.
STILLS = ((180.0, 0.0), (180.0, 1.0), (140.0, 1.0))
# The turn-around: once round in this many frames, which pack_clips.py shows twelve to the
# second. The lights come on between the first two angles and go out between the last two.
TURN_FRAMES = 36
LIT_BETWEEN = (100.0, 140.0, 225.0, 265.0)


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


def write(name, shots):
    """A clip in memory of the standing mascot, one frame for each (angle, lights) given."""
    clips.reset_pose()
    clips.new_action(drawing.DRAWN + name, len(shots) - 1, False)
    for frame, (angle, on) in enumerate(shots):
        clips.neutral()
        clips.set_brake(on)
        greeting.key(frame, angle=angle)
        clips.key_brake(frame)
    return len(shots)


def turn():
    on_from, on_at, off_from, off_at = LIT_BETWEEN
    shots = []
    for frame in range(TURN_FRAMES):
        angle = 360.0 * frame / TURN_FRAMES
        lit = clips.smooth((angle - on_from) / (on_at - on_from)) * (1 - clips.smooth((angle - off_from) / (off_at - off_from)))
        shots.append((angle, lit))
    return shots


def main():
    asked = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(asked) != 1:
        raise SystemExit("Name one folder after '--' for the frames.")
    folder = os.path.abspath(asked[0])
    shown = rig.animation_data.action
    frames = {"Stills": write("Stills", STILLS), "Turn": write("Turn", turn())}

    # See-through first: once a ground has been asked for, it stays for the rest of the run.
    size = drawing.set_picture({"Stills": frames["Stills"]}, STILL_PIXELS)
    drawing.play("Stills")
    drawing.draw(os.path.join(folder, "see-through"), frames["Stills"])
    print("see-through stills: %d x %d pixels" % size[:2])

    for name, pixels, where in (("Stills", STILL_PIXELS, "dark"), ("Turn", TURN_PIXELS, os.path.join("dark", "turn"))):
        size = drawing.set_picture({name: frames[name]}, pixels, drawing.MARGIN + round(drawing.HALO_ROOM * pixels))
        greeting.on_ground(DARK)
        drawing.play(name)
        drawing.draw(os.path.join(folder, where), frames[name])
        print("%s on #%s: %d frames of %d x %d pixels" % (name.lower(), DARK, frames[name], *size[:2]))

    for name in frames:
        bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + name])
    rig.animation_data.action = shown


if __name__ != "__milo_lib__":
    main()

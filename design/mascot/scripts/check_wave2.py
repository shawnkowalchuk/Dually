# check_wave2.py - says, for every frame of the wave, walked through, which parts of the mascot
# go into one another that should not: a shoe or a hand into his body or into a dial's housing,
# a shoe into the other shoe, a hand into a shoe or into the other hand. Run it after the other
# scripts, with Blender WITHOUT its window:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/check_wave2.py
#
# It asks what check_run_slide.py asks of the run and the slide, in the same way and of the
# same pairs of parts (that script is read for them): the meshes as they are posed, frame by
# frame. The frames are render_wave2.py's: one cycle of the walk in, the middle, one cycle of
# the walk off. NOTHING IS SAVED.

import os
import types
import bpy


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


asked = library("check_run_slide.py")
drawn = library("render_wave2.py")
scene = bpy.context.scene


def main():
    shown = drawn.rig.animation_data.action
    parts = (("In", "the walk in", drawn.write_cycle("In", drawn.clips.walk_in)),
             ("Middle", "the middle", drawn.write_middle("Middle", False)),
             ("Out", "the walk off", drawn.write_cycle("Out", drawn.clips.walk_off)))
    found, frames = {}, 0
    for name, what, count in parts:
        drawn.drawing.play(name)
        frames += count
        for frame in range(count):
            scene.frame_set(frame)
            graph = bpy.context.evaluated_depsgraph_get()
            surfaces = {part: asked.surface(part, graph) for pair in asked.PAIRS for part in pair}
            for a, b in asked.PAIRS:
                if surfaces[a].overlap(surfaces[b]):
                    found.setdefault((a, b), []).append("%s %d" % (what, frame))
    for name, _, _ in parts:
        bpy.data.actions.remove(bpy.data.actions[drawn.drawing.DRAWN + name])
    drawn.rig.animation_data.action = shown
    for (a, b), where in sorted(found.items()):
        print("%s goes into %s in %d frames: %s" % (a, b, len(where), ", ".join(where)))
    if not found:
        print("in %d frames of the wave, walked through, nothing goes into anything: %d pairs of parts asked" % (frames, len(asked.PAIRS)))


if __name__ != "__milo_lib__":
    main()

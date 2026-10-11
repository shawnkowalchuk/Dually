# check_run_slide.py - says, for every frame of the run and the slide, which parts of the
# mascot go into one another that should not: a shoe or a hand into his body or into a dial's
# housing, a shoe into the other shoe, a hand into a shoe or into the other hand. Run it after
# the other scripts, with Blender WITHOUT its window:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/check_run_slide.py
#
# It asks the meshes as they are posed, frame by frame, whether their surfaces cross. That is
# not always seen in a picture: a shoe's top inside the foot of his body is hidden by the body,
# and a fist's fingers in his side look like a fist beside him. His arms and legs are not
# asked: they come out of his body, as they always do.
#
# The poses come from animate_milo.py's functions, as in render_run_slide.py, which is read for
# them. NOTHING IS SAVED.

import os
import types
import bpy
from mathutils.bvhtree import BVHTree


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


drawn = library("render_run_slide.py")
clips = drawn.clips
scene = bpy.context.scene
HOUSINGS = sorted(ob.name for ob in bpy.data.collections["MilO_Mascot"].objects
                  if ob.name.startswith("Eye_") and ob.name.endswith("_Housing"))
PAIRS = ([(part, "Body") for part in ("Shoe.L", "Shoe.R", "Hand.L", "Hand.R")]
         + [(hand, housing) for hand in ("Hand.L", "Hand.R") for housing in HOUSINGS]
         + [(hand, shoe) for hand in ("Hand.L", "Hand.R") for shoe in ("Shoe.L", "Shoe.R")]
         + [("Shoe.L", "Shoe.R"), ("Hand.L", "Hand.R")])


def surface(name, graph):
    """An object's surface as it is posed now, in the scene, to ask what crosses it."""
    posed = bpy.data.objects[name].evaluated_get(graph)
    mesh = posed.to_mesh()
    points = [posed.matrix_world @ v.co for v in mesh.vertices]
    faces = [tuple(p.vertices) for p in mesh.polygons]
    posed.to_mesh_clear()
    return BVHTree.FromPolygons(points, faces)


def main():
    shown = drawn.rig.animation_data.action
    frames = drawn.write(0.0)
    drawn.drawing.play(drawn.ACT)
    found = {}
    for frame in range(frames):
        scene.frame_set(frame)
        graph = bpy.context.evaluated_depsgraph_get()
        surfaces = {name: surface(name, graph) for pair in PAIRS for name in pair}
        for a, b in PAIRS:
            if surfaces[a].overlap(surfaces[b]):
                found.setdefault((a, b), []).append(frame)
    bpy.data.actions.remove(bpy.data.actions[drawn.drawing.DRAWN + drawn.ACT])
    drawn.rig.animation_data.action = shown
    for (a, b), where in sorted(found.items()):
        print("%s goes into %s in %d frames: %s"
              % (a, b, len(where), ", ".join("%d (%s %d)" % (frame, *clips.act_part(frame)) for frame in where)))
    if not found:
        print("in %d frames of the run and the slide nothing goes into anything: %d pairs of parts asked" % (frames, len(PAIRS)))


if __name__ != "__milo_lib__":
    main()

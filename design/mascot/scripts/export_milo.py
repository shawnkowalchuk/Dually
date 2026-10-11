# export_milo.py - writes the mascot to a .glb file with every clip, then reads the file back and
# prints what is in it. Run it inside Blender after build_milo.py and animate_milo.py.
#
# The app does not carry this file: it plays pictures of the mascot (render_greeting.py). The
# model is kept for whatever comes later.
#
# Only the "MilO_Mascot" collection is exported: no camera, lights or backdrop. The model's own
# pose in the file is the rest pose (arms straight out); every clip stashed on the rig's NLA
# tracks becomes one named animation, sampled on every frame so that everything the rig works
# out for itself (legs following the feet, hoses following their bones, needles following the
# sliders, the bone "brake" growing with the brake lights' slider) is written as plain bone
# movement. The lenses' glow itself is not: a .glb cannot hold a material that follows a slider.
#
# The hat is left out (LEFT_OUT). In the Blender file a slider keeps it off his head in every
# clip but the moonwalk's. A .glb has no such thing: a clip that says nothing about the hat
# shows it as it was built, on his head, and the exporter writes nothing for a part that keeps
# still through a clip. He would wear it in every clip. The bone "hat" is in the file, and the
# moonwalk's clips move it, so a hat can be hung on it.
#
# The party cap and the party popper are left out for the same reason: each is put on, or into
# his fist, by a slider, and in the .glb he would wear the one and hold the other in every
# clip. Their bones are in the file ("party_hat", "popper", and "popper_string" and
# "popper_pull" for the string), and the clip OneYear moves them. The confetti is not in the
# file either: it is a collection of its own, and only "MilO_Mascot" is exported.

import os
import json
import struct
import bpy

def here():
    """This script's folder: from its own path, or beside the .blend when it is run from
    Blender's text editor."""
    path = globals().get("__file__", "")
    return os.path.dirname(os.path.abspath(path)) if os.path.isfile(path) else bpy.path.abspath("//scripts")


# TODO(debt): the .glb plays the moonwalk without the hat (FINDINGS_LOG, 2026-10-10).
# TODO(debt): and the party, OneYear, without the cap, the popper and the confetti.
LEFT_OUT = ("Hat", "Party_Cap", "Popper")
MASCOT = os.path.normpath(os.path.join(here(), ".."))
OUT = os.path.join(MASCOT, "export", "milo_mascot.glb")


def export(path):
    """Writes the model to `path` with every clip."""
    if bpy.context.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    rig = bpy.data.objects["MilO_Rig"]
    shown = rig.animation_data.action
    rig.animation_data.action = None
    for b in rig.pose.bones:
        b.location = (0.0, 0.0, 0.0)
        b.rotation_euler = (0.0, 0.0, 0.0)
        b.scale = (1.0, 1.0, 1.0)
    for ob in bpy.context.view_layer.objects:
        try:
            ob.select_set(False)
        except RuntimeError:
            pass
    for ob in bpy.data.collections["MilO_Mascot"].objects:
        ob.select_set(ob.name not in LEFT_OUT)
    bpy.context.view_layer.objects.active = rig
    os.makedirs(os.path.dirname(path), exist_ok=True)

    wanted = dict(
        filepath=path, export_format="GLB", use_selection=True, export_apply=True, export_yup=True,
        export_cameras=False, export_lights=False, export_extras=False, export_skins=True,
        export_animations=True, export_animation_mode="ACTIONS", export_force_sampling=True,
        export_optimize_animation_size=True, export_optimize_animation_keep_anim_armature=False,
        export_reset_pose_bones=True, export_anim_slide_to_zero=True, export_morph=False,
        # Off: the clips written are the ones stashed on the rig's tracks. On, the exporter
        # looks for every action in the file that moves a bone.
        export_anim_single_armature=False,
    )
    known = bpy.ops.export_scene.gltf.get_rna_type().properties.keys()
    bpy.ops.export_scene.gltf(**{k: v for k, v in wanted.items() if k in known})

    # The exporter plays every clip, and leaves what only the moonwalk's clips and the party's
    # key where the last of them left it: the hat or the cap on, the brake lights lit, the
    # popper in his fist, the mascot turned. The file is saved after this script, and is to be
    # saved with all of that off.
    for prop in ("hat", "brake", "party_hat", "popper", "pull"):
        rig[prop] = 0.0
    for b in rig.pose.bones:
        b.location = (0.0, 0.0, 0.0)
        b.rotation_euler = (0.0, 0.0, 0.0)
        b.scale = (1.0, 1.0, 1.0)
    rig.animation_data.action = shown
    bpy.context.scene.frame_set(bpy.context.scene.frame_current)
    return path


def describe(path):
    """What the file holds: its size, where the bytes go, and each clip's name and length."""
    with open(path, "rb") as f:
        data = f.read()
    length = struct.unpack_from("<I", data, 12)[0]
    doc = json.loads(data[20:20 + length].decode("utf-8"))
    views, accessors = doc.get("bufferViews", []), doc.get("accessors", [])

    def size(accessor_ids):
        return sum(views[v]["byteLength"] for v in {accessors[a]["bufferView"] for a in accessor_ids})

    mesh_ids, points, triangles = set(), 0, 0
    for mesh in doc.get("meshes", []):
        for prim in mesh["primitives"]:
            mesh_ids |= set(prim["attributes"].values()) | {prim["indices"]}
            points += accessors[prim["attributes"]["POSITION"]]["count"]
            triangles += accessors[prim["indices"]]["count"] // 3
    anim_ids = {s[k] for a in doc.get("animations", []) for s in a["samplers"] for k in ("input", "output")}
    skin_ids = {s["inverseBindMatrices"] for s in doc.get("skins", []) if "inverseBindMatrices" in s}
    names = [n.get("name", "") for n in doc["nodes"]]

    print("file: %s  %.2f MB (%d bytes)" % (path, len(data) / 1048576, len(data)))
    print("  meshes %.0f KB | animation %.0f KB | skin %.0f KB | json %.0f KB"
          % (size(mesh_ids) / 1024, size(anim_ids) / 1024, size(skin_ids) / 1024, length / 1024))
    print("  %d points, %d triangles, %d draw calls, %d materials, %d joints, %d images"
          % (points, triangles, sum(len(m["primitives"]) for m in doc.get("meshes", [])),
             len(doc.get("materials", [])), sum(len(s["joints"]) for s in doc.get("skins", [])), len(doc.get("images", []))))
    for anim in doc.get("animations", []):
        seconds = max(accessors[s["input"]]["max"][0] for s in anim["samplers"])
        moved = sorted({names[c["target"]["node"]] for c in anim["channels"]})
        needles = [n for n in moved if n.startswith("needle_")]
        print("  clip %-12s %.2f s  %3d channels on %2d bones  needles: %s"
              % (anim["name"], seconds, len(anim["channels"]), len(moved), ", ".join(needles) or "none"))
    return doc


describe(export(OUT))

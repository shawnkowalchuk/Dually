# build_milo.py - rebuilds the MilO mascot (model + rig) from scratch inside the "MilO_Mascot"
# collection, the confetti of his party popper inside "MilO_Confetti" and the smoke of his shoes
# inside "MilO_Smoke". Run it inside Blender. It only ever touches objects in those three
# collections.
#
# The character faces -Y, stands on z = 0 and is about 2 units tall. ".L" is the character's own
# left (+X). Everything is built in a neutral rest pose: arms straight out, hands open, palms down.

import os
import random
import bpy
import bmesh
from math import radians, sin, cos, pi, exp, hypot, sqrt
from mathutils import Vector, Matrix

COLL = "MilO_Mascot"
# The confetti of the party popper: a collection of its own, so that a picture can be drawn
# with it, without it, or of it alone. It is hidden wherever it is not asked for.
CONFETTI = "MilO_Confetti"
# The smoke his shoes make when he runs on the spot and when he slides: a collection of its
# own for the same reason, and hidden in the same way.
SMOKE = "MilO_Smoke"
def here():
    """This script's folder: from its own path, or beside the .blend when it is run from
    Blender's text editor."""
    path = globals().get("__file__", "")
    return os.path.dirname(os.path.abspath(path)) if os.path.isfile(path) else bpy.path.abspath("//scripts")


# The app's own typeface, for the numbers on the dials.
SORA = os.path.normpath(os.path.join(here(), "..", "..", "..", "app", "src", "main", "res", "font", "sora.ttf"))

# ---------------------------------------------------------------- dimensions
BODY_W, BODY_D, BODY_H = 1.06, 0.44, 1.00
BODY_Z0 = 0.72
BODY_C = Vector((0.0, 0.0, BODY_Z0 + BODY_H / 2))
FRONT_Y = -BODY_D / 2
BODY_R = 0.085

GAUGE_Y = -0.30  # world y of both dial faces
GAUGE_BACK_Y = 0.19
SPEEDO = dict(key="Speedo", bone="speedo", cx=-0.19, cz=1.69, R=0.33, Rb=0.262, wr=0.024, hz=0.030)
TACH = dict(key="RPM", bone="rpm", cx=0.38, cz=1.57, R=0.225, Rb=0.178, wr=0.017, hz=0.022)

# The letter M of Sora at weight 700, as the app's own mark draws it
# (app/src/main/res/drawable/ic_launcher_foreground.xml). Where the font's outline crosses itself
# at the two inner corners, the crossing point is used, so the shape is one simple outline.
M_OUTLINE = [
    (41.89, 64.95), (41.89, 43.05), (48.67, 43.05), (53.67, 55.37), (54.24, 55.37), (59.2, 43.05),
    (66.11, 43.05), (66.11, 64.95), (61.23, 64.95), (61.23, 48.06), (56.15, 60.51), (51.45, 60.51),
    (46.4, 48.176), (46.4, 64.95),
]
M_HEIGHT = 0.38
M_CX, M_CZ = 0.0, 1.07
M_DEPTH = 0.04
M_CHAMFER = 0.012

LEG_X, HIP_Z, KNEE_Z, ANKLE_Z = 0.27, 0.74, 0.59, 0.44
LEG_R0, LEG_R1 = 0.066, 0.078
SOLE_T = 0.07
SHOE_HW, SHOE_HL, SHOE_H, SHOE_CY = 0.232, 0.33, 0.33, -0.125
SHOE_TAPER = 0.10

ARM_Z, SHOULDER_X, ELBOW_X, WRIST_X = 1.17, 0.50, 0.78, 1.04
ARM_R = 0.05
PALM_C = Vector((1.245, 0.0, ARM_Z))
PALM_SIZE = (0.27, 0.34, 0.20)
PALM_R = 0.088
CUFF_X, CUFF_R, CUFF_r = WRIST_X + 0.045, 0.112, 0.058
FINGER_X = 1.355
FINGER_R = 0.062
FINGERS = [("finger_a", -0.113, 1.0), ("finger_b", 0.0, 1.08), ("finger_c", 0.113, 0.94)]
FINGER_L1, FINGER_L2 = 0.095, 0.085
THUMB_0 = Vector((1.20, -0.135, ARM_Z - 0.005))
THUMB_DIR = Vector((0.5, -0.866, 0.0)).normalized()
THUMB_R = 0.066
THUMB_L1, THUMB_L2 = 0.11, 0.10

HOSE_BONES = ("upper_arm", "forearm", "thigh", "shin")
HOSE_STEPS = 5

# The brake lights: two round red domes low on the back, close together and proud of the body,
# where a truck has its tail lights and he would have his behind. Each stands BRAKE_X from the
# middle, has a radius of BRAKE_R and is BRAKE_PROUD high at its middle, in a thin rubber rim.
BACK_Y = BODY_D / 2
BRAKE_X, BRAKE_Z = 0.168, 0.965
BRAKE_R, BRAKE_PROUD, BRAKE_RIM = 0.16, 0.10, 0.012
# How strongly a lens glows with the slider "brake" at 1: its material's emission strength.
BRAKE_GLOW = 4.0

# The hat: a black fedora with a green band, for the moonwalk. It is as wide as he is, because
# his head is two dials side by side: both go up into its crown. It rests on the small dial and
# is pulled down over the big one, so it sits at a slant, HAT_ROLL degrees down towards the
# small dial, and it is tipped HAT_TIP degrees forward, low over both. The brim's front edge
# ends above the middle of each dial: the needles and the lower numbers stay in view.
# HAT_AT is the middle of its opening, and the point the bone "hat" turns it about.
HAT_AT = Vector((0.04, -0.055, 1.90))
HAT_TIP, HAT_ROLL = 14.0, 14.0
# The crown: half its width, half its depth, its height. The brim: its width and its thickness.
HAT_A, HAT_B, HAT_H = 0.40, 0.29, 0.37
HAT_BRIM, HAT_FELT = 0.19, 0.024
# How far the crown's front is pressed in from both sides, as a part of its width; how deep the
# crease along its top is; and how far the brim's front edge is snapped down, its back edge up.
HAT_PINCH, HAT_CREASE, HAT_SNAP = 0.36, 0.08, 0.035

# The party cap: a paper cone for the day he has been used for a year, in the app's lime with
# stripes of his own green that wind round it, a white rolled rim and a red pom-pom on its tip.
# (Lime and white stripes were tried: on a white ground the cap was all but gone.) A cone
# cannot be as wide as he is, as the fedora is, so it sits on the big dial, the highest part
# of his head: CAP_SEAT degrees round the dial from its top, towards his right, and its tip
# leans CAP_LEAN degrees further that way and CAP_BACK degrees to the back. Its rim follows
# the dial's round housing.
CAP_SEAT, CAP_LEAN, CAP_BACK = 10.0, 6.0, 4.0
# Half its width at the rim, its height, and how thick the rolled rim is.
CAP_R, CAP_H, CAP_ROLL = 0.21, 0.56, 0.018
# How many stripes of each colour, and how many times they wind round on the way up.
CAP_STRIPES, CAP_TURNS = 5, 0.55
POM_R = 0.075

# The party popper: a cone with a pull string, held in the fist of POPPER_SIDE's hand like an
# ice-cream cone: its mouth comes out on the thumb's side and its tail, with the string, on the
# other. It is built where that hand is in the rest pose, the arm straight out and the palm
# down: POPPER_GRIP is the middle of the fist, as far along the hand from the wrist and as far
# under it, and the mouth points the way the thumb's side does.
POPPER_SIDE, PULL_SIDE = "L", "R"
POPPER_GRIP = (0.29, 0.125)
# Along the popper from the middle of the fist: where the tail ends, where the cone begins,
# where its mouth is; and half the width of the handle, of the cone's foot and of its mouth.
POPPER_TAIL, POPPER_NECK, POPPER_MOUTH = -0.215, 0.185, 0.50
POPPER_R = (0.04, 0.052, 0.14)
# The string: how long it hangs out of the tail, half its thickness, and the bead on its end.
STRING_L, STRING_R, BEAD_R = 0.15, 0.011, 0.036
# Where the other hand pinches the bead, in the rest pose of the left hand (the right hand's
# place is the mirror image): how far along the hand from the wrist, how far towards the thumb
# and how far under the hand.
PINCH = (0.33, 0.10, 0.13)

# The confetti: this many pieces of paper and this many streamers, cut and coloured by a seed,
# so that every build makes the same ones. animate_milo.py throws them (the same seed).
PAPERS, STREAMERS = 130, 6
CONFETTI_SEED = 365
# The app's lime first; the rest are there to be festive on its dark page.
CONFETTI_COLOURS = (("Lime", "#C6F432"), ("White", "#F2F3F5"), ("Red", "#FF3B30"), ("Yellow", "#FFC83D"),
                    ("Blue", "#35B8FF"), ("Pink", "#FF5CA8"))
# A streamer: its length, the radius it is curled to, how many curls, and its ribbon's width.
STREAMER = (0.62, 0.034, 3.5, 0.034)

# The smoke: this many puffs, each a few round lumps grown into one another, shaped by a seed,
# so that every build makes the same ones. animate_milo.py lets them go, and stops if it needs
# more than there are. A puff is built about as large as a ball of radius 1; the size of its
# bone is how large it is drawn.
SMOKE_PUFFS = 112
SMOKE_SEED = 58
# A puff's colour in its middle and at its edge. Pale, for the app's dark page, but not white,
# and a good deal greyer at its edge: on a white page a puff is then a pale grey shape with an
# outline, and each of its lumps has one.
SMOKE_COLOURS = ("#D2D6DC", "#727B89")
# How much of that it glows by itself, and how much it takes from the lights: the lights make
# the top of a puff paler than its underside, and no more.
SMOKE_GLOWS, SMOKE_LIT = 0.62, 0.42


# ---------------------------------------------------------------- small helpers
def arc(a0, a1, n):
    return [radians(a0 + (a1 - a0) * i / n) for i in range(n + 1)]


def srgb(h):
    h = h.lstrip("#")

    def lin(c):
        c /= 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    return (lin(int(h[0:2], 16)), lin(int(h[2:4], 16)), lin(int(h[4:6], 16)), 1.0)


def z_to(direction):
    return Vector((0, 0, 1)).rotation_difference(Vector(direction).normalized()).to_matrix().to_4x4()


def reset_collection(name):
    coll = bpy.data.collections.get(name)
    if coll is None:
        coll = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(coll)
    removers = {
        bpy.types.Mesh: bpy.data.meshes, bpy.types.Armature: bpy.data.armatures,
        bpy.types.Camera: bpy.data.cameras, bpy.types.Light: bpy.data.lights,
    }
    for ob in list(coll.objects):
        data = ob.data
        bpy.data.objects.remove(ob, do_unlink=True)
        if data is not None and data.users == 0:
            for kind, store in removers.items():
                if isinstance(data, kind):
                    store.remove(data)
    return coll


# ---------------------------------------------------------------- materials
def principled(name, color, rough=0.4, metal=0.0, coat=0.0, emit=0.0):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    try:
        m.use_nodes = True
    except Exception:
        pass
    nt = m.node_tree
    bsdf = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
    if bsdf is None:
        nt.nodes.clear()
        bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
        out = nt.nodes.new("ShaderNodeOutputMaterial")
        nt.links.new(bsdf.outputs[0], out.inputs[0])

    def put(key, val):
        if key in bsdf.inputs:
            bsdf.inputs[key].default_value = val

    put("Base Color", color)
    put("Roughness", rough)
    put("Metallic", metal)
    put("Coat Weight", coat)
    put("Coat Roughness", 0.06)
    put("Emission Color", color)
    put("Emission Strength", emit)
    m.diffuse_color = color
    m.roughness = rough
    m.metallic = metal
    return m


def glass_material(name):
    # A plain see-through gloss on the standard shader, so a glTF export carries it unchanged.
    m = principled(name, (0.02, 0.02, 0.02, 1.0), rough=0.03)
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Alpha"].default_value = 0.14
    m.diffuse_color = (1.0, 1.0, 1.0, 0.14)
    try:
        m.surface_render_method = "BLENDED"
    except Exception:
        pass
    return m


def lens_material(name):
    """The brake lights' lens: a dark, glossy red while it is unlit. Its glow is the material's
    emission strength, which follows the rig's slider "brake" (light_up); the red it glows in
    is set here, and is kept this pure so that a lit lens is drawn red and not pink."""
    m = principled(name, srgb("#8E0E0C"), rough=0.10, coat=0.6)
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Emission Color"].default_value = (1.0, 0.004, 0.002, 1.0)
    bsdf.inputs["Emission Strength"].default_value = 0.0
    return m


def felt_material(name):
    """The hat's felt: nearly black, with a soft sheen along its edges. He is shown on the
    app's dark page, where a hat that is plain black is a hole in the picture."""
    m = principled(name, srgb("#1B1B20"), rough=0.5)
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    for key, value in (("Sheen Weight", 0.8), ("Sheen Roughness", 0.35)):
        if key in bsdf.inputs:
            bsdf.inputs[key].default_value = value
    return m


def wool_material(name):
    """The pom-pom on the party cap: red wool, soft at its edges."""
    m = principled(name, srgb("#F01E12"), rough=0.9)
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    for key, value in (("Sheen Weight", 1.0), ("Sheen Roughness", 0.5)):
        if key in bsdf.inputs:
            bsdf.inputs[key].default_value = value
    return m


def smoke_material(name):
    """The smoke: matt, pale in the middle of each lump and greyer where its surface turns away
    from the viewer, which gives every lump of a puff an edge. Most of its colour it glows by
    itself, so that it is the same on any ground and under any light; the lights add a paler top.
    It glows too faintly to get a halo (render_greeting.py)."""
    pale, grey = (srgb(colour) for colour in SMOKE_COLOURS)
    m = principled(name, pale, rough=1.0)
    tree = m.node_tree
    for node in list(tree.nodes):  # the material is kept from one build to the next
        if node.type not in ("BSDF_PRINCIPLED", "OUTPUT_MATERIAL"):
            tree.nodes.remove(node)
    shader = next(n for n in tree.nodes if n.type == "BSDF_PRINCIPLED")
    shader.inputs["Specular IOR Level"].default_value = 0.0
    edge = tree.nodes.new("ShaderNodeLayerWeight")
    edge.inputs["Blend"].default_value = 0.5
    for socket, part in (("Emission Color", SMOKE_GLOWS), ("Base Color", SMOKE_LIT)):
        mix = tree.nodes.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        colours = [s for s in mix.inputs if s.type == "RGBA"]
        for colour, value in zip(colours, (pale, grey)):
            colour.default_value = (*(c * part for c in value[:3]), 1.0)
        tree.links.new(edge.outputs["Facing"], mix.inputs["Factor"])
        tree.links.new(next(s for s in mix.outputs if s.type == "RGBA"), shader.inputs[socket])
    shader.inputs["Emission Strength"].default_value = 1.0
    return m


def make_materials():
    return {
        "green": principled("MilO_Green", srgb("#34BA16"), rough=0.22, coat=0.4),
        "black": principled("MilO_Black_Rubber", (0.012, 0.012, 0.013, 1), rough=0.42),
        "gloss": principled("MilO_Black_Gloss", (0.004, 0.004, 0.005, 1), rough=0.12, coat=0.5),
        "white": principled("MilO_White_Glove", (0.78, 0.78, 0.78, 1), rough=0.32, coat=0.2),
        "chrome": principled("MilO_Chrome", (0.88, 0.89, 0.9, 1), rough=0.1, metal=1.0),
        "mark": principled("MilO_Dial_White", (0.95, 0.95, 0.95, 1), rough=0.5, emit=0.6),
        "dial_green": principled("MilO_Dial_Green", srgb("#35D21C"), rough=0.5, emit=0.8),
        "red": principled("MilO_Red", srgb("#F01E12"), rough=0.35, emit=0.5),
        "glass": glass_material("MilO_Glass"),
        "brake": lens_material("MilO_Brake_Lens"),
        "felt": felt_material("MilO_Hat_Felt"),
        # The party cap and the party popper: the app's lime, and his green, a white and the
        # needles' red as paper (the body's green is glossy, the needles' red glows, and the
        # gloves' white is theirs); and the pom-pom's wool.
        "party_lime": principled("MilO_Party_Lime", srgb("#C6F432"), rough=0.42),
        "party_green": principled("MilO_Party_Green", srgb("#34BA16"), rough=0.42),
        "party_white": principled("MilO_Party_White", (0.82, 0.82, 0.82, 1), rough=0.45),
        "party_red": principled("MilO_Party_Red", srgb("#F01E12"), rough=0.36),
        "pom": wool_material("MilO_Party_Pom"),
    }


# ---------------------------------------------------------------- mesh building blocks
def lathe(bm, profile, segs=32, xf=None, closed=False, mi=0):
    """Revolve a profile of (radius, height) points around Z. A radius of 0 makes a pole."""
    rings, new_verts = [], []
    for rho, z in profile:
        if abs(rho) < 1e-9:
            ring = [bm.verts.new((0.0, 0.0, z))]
        else:
            ring = [bm.verts.new((rho * cos(2 * pi * i / segs), rho * sin(2 * pi * i / segs), z))
                    for i in range(segs)]
        rings.append(ring)
        new_verts += ring
    n = len(rings)
    pairs = [(i, i + 1) for i in range(n - 1)] + ([(n - 1, 0)] if closed else [])
    for a, b in pairs:
        ra, rb = rings[a], rings[b]
        if len(ra) == 1 and len(rb) == 1:
            continue
        for i in range(segs):
            j = (i + 1) % segs
            if len(ra) == 1:
                f = bm.faces.new((ra[0], rb[i], rb[j]))
            elif len(rb) == 1:
                f = bm.faces.new((ra[i], rb[0], ra[j]))
            else:
                f = bm.faces.new((ra[i], rb[i], rb[j], ra[j]))
            f.material_index = mi
    if xf is not None:
        bmesh.ops.transform(bm, matrix=xf, verts=new_verts)
    return new_verts


def capsule(bm, p0, p1, r, segs=18, hemi=5, mi=0):
    p0, p1 = Vector(p0), Vector(p1)
    length = (p1 - p0).length
    prof = [(r * cos(a), r * sin(a)) for a in arc(-90, 0, hemi)]
    prof += [(r * cos(a), length + r * sin(a)) for a in arc(0, 90, hemi)]
    prof[0], prof[-1] = (0.0, -r), (0.0, length + r)
    return lathe(bm, prof, segs, Matrix.Translation(p0) @ z_to(p1 - p0), mi=mi)


def tube(bm, p0, p1, r0, r1, rings=16, segs=20, mi=0):
    p0, p1 = Vector(p0), Vector(p1)
    length = (p1 - p0).length
    prof = [(0.0, 0.0)]
    prof += [(r0 + (r1 - r0) * i / rings, length * i / rings) for i in range(rings + 1)]
    prof += [(0.0, length)]
    return lathe(bm, prof, segs, Matrix.Translation(p0) @ z_to(p1 - p0), mi=mi)


def torus(bm, center, axis, big_r, small_r, segs=36, msegs=14, mi=0):
    prof = [(big_r + small_r * cos(2 * pi * i / msegs), small_r * sin(2 * pi * i / msegs))
            for i in range(msegs)]
    return lathe(bm, prof, segs, Matrix.Translation(Vector(center)) @ z_to(axis), closed=True, mi=mi)


def rounded_box(bm, center, size, radius, segs=5):
    before = set(bm.verts)
    made = bmesh.ops.create_cube(bm, size=1.0)["verts"]
    bmesh.ops.scale(bm, vec=Vector(size), verts=made)
    edges = list({e for v in made for e in v.link_edges})
    bmesh.ops.bevel(bm, geom=edges, offset=radius, offset_type="OFFSET", segments=segs,
                    profile=0.5, affect="EDGES", clamp_overlap=True)
    mine = [v for v in bm.verts if v not in before]
    bmesh.ops.translate(bm, vec=Vector(center), verts=mine)
    return mine


def finish(coll, name, bm, mats, sharp=40, recalc=True):
    if recalc:
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for m in mats:
        me.materials.append(m)
    me.polygons.foreach_set("use_smooth", [True] * len(me.polygons))
    if sharp:
        me.set_sharp_from_angle(angle=radians(sharp))
    me.update()
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    return ob


def text_mesh(body, size, font, embolden=0.0):
    """The letters as a flat mesh in the XY plane, centred on its own bounding box."""
    cu = bpy.data.curves.new("tmp_text", "FONT")
    cu.body = body
    cu.size = size
    cu.font = font
    cu.offset = embolden
    cu.resolution_u = 6
    ob = bpy.data.objects.new("tmp_text", cu)
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
    bpy.data.objects.remove(ob, do_unlink=True)
    bpy.data.curves.remove(cu)
    xs = [v.co.x for v in me.vertices]
    ys = [v.co.y for v in me.vertices]
    me.transform(Matrix.Translation((-(min(xs) + max(xs)) / 2, -(min(ys) + max(ys)) / 2, 0.0)))
    return me


def merge(bm, me, xf, mi):
    me.transform(xf)
    n0 = len(bm.faces)
    bm.from_mesh(me)
    bm.faces.ensure_lookup_table()
    for f in bm.faces[n0:]:
        f.material_index = mi
    bpy.data.meshes.remove(me)


def offset_outline(pts, d):
    n = len(pts)
    area = sum(pts[i].x * pts[(i + 1) % n].y - pts[(i + 1) % n].x * pts[i].y for i in range(n)) / 2
    sign = 1.0 if area > 0 else -1.0
    out = []
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
        e1, e2 = (p1 - p0).normalized(), (p2 - p1).normalized()
        n1 = Vector((e1.y, -e1.x)) * sign
        n2 = Vector((e2.y, -e2.x)) * sign
        out.append(p1 + (n1 + n2) / max(1.0 + n1.dot(n2), 0.25) * d)
    return out


# ---------------------------------------------------------------- body
def build_body(coll, mats):
    bm = bmesh.new()
    rounded_box(bm, BODY_C, (BODY_W, BODY_D, BODY_H), BODY_R, 8)
    body = finish(coll, "Body", bm, [mats["green"], mats["gloss"]])

    # The M is cut into the front: a chamfered rim, then straight walls down to a flat floor.
    s = M_HEIGHT / 21.9
    pts = [Vector(((x - 54.0) * s, -(y - 54.0) * s)) for x, y in M_OUTLINE]
    lead = 0.02
    layers = [
        (offset_outline(pts, M_CHAMFER + lead), FRONT_Y - lead),
        (pts, FRONT_Y + M_CHAMFER),
        (pts, FRONT_Y + M_DEPTH),
    ]
    bm = bmesh.new()
    rings = [[bm.verts.new((p.x + M_CX, y, p.y + M_CZ)) for p in outline] for outline, y in layers]
    n = len(pts)
    caps = [bm.faces.new(rings[0]), bm.faces.new(rings[-1])]
    for a, b in zip(rings[:-1], rings[1:]):
        for i in range(n):
            j = (i + 1) % n
            bm.faces.new((a[i], b[i], b[j], a[j]))
    # Triangles only: the rim's faces are not flat, and the exact solver returns nothing for those.
    bmesh.ops.triangulate(bm, faces=bm.faces[:])
    cutter = finish(coll, "tmp_M_cutter", bm, [], sharp=None)

    uncut = len(body.data.vertices)
    cut = None
    for solver in ("EXACT", "MANIFOLD", "FLOAT"):
        mod = body.modifiers.new("cut", "BOOLEAN")
        mod.operation = "DIFFERENCE"
        mod.object = cutter
        try:
            mod.solver = solver
        except TypeError:
            body.modifiers.remove(mod)
            continue
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        result = bpy.data.meshes.new_from_object(body.evaluated_get(dg))
        body.modifiers.remove(mod)
        if len(result.vertices) > uncut:
            cut = result
            print("M cut with the %s solver" % solver)
            break
        bpy.data.meshes.remove(result)
    if cut is None:
        raise RuntimeError("The M could not be cut into the body with any solver")
    old = body.data
    body.data = cut
    bpy.data.meshes.remove(old)
    cut.name = body.name
    cutter_mesh = cutter.data
    bpy.data.objects.remove(cutter, do_unlink=True)
    bpy.data.meshes.remove(cutter_mesh)

    # Walls and floor of the letter are glossy black; the chamfered rim stays green.
    half_w = M_HEIGHT * (24.22 / 21.9) / 2 + 0.03
    half_h = M_HEIGHT / 2 + 0.03
    limit = FRONT_Y + M_CHAMFER * 0.9
    black = 0
    for p in cut.polygons:
        c = p.center
        inside = abs(c.x - M_CX) < half_w and abs(c.z - M_CZ) < half_h and c.y < FRONT_Y + M_DEPTH + 0.01
        if inside and min(cut.vertices[i].co.y for i in p.vertices) > limit:
            p.material_index = 1
            black += 1
    cut.polygons.foreach_set("use_smooth", [True] * len(cut.polygons))
    cut.set_sharp_from_angle(angle=radians(35))
    cut.update()
    return body, black


# ---------------------------------------------------------------- gauges (the eyes)
def gauge_frame(g):
    # Gauge-local x is world x, local y is world z (up on the dial), local z points at the viewer.
    return Matrix.Translation((g["cx"], GAUGE_Y, g["cz"])) @ Matrix.Rotation(radians(90), 4, "X")


def dial_angle(g, value):
    """Angle on the dial, counter-clockwise from 3 o'clock, for a speed or an engine speed."""
    return radians(210.0 - (value if g is SPEEDO else value * 30.0))


def build_gauge(coll, mats, g, font):
    G = gauge_frame(g)
    R, Rb, wr, hz = g["R"], g["Rb"], g["wr"], g["hz"]
    Rv = Rb - wr
    key = g["key"]
    zb = -(GAUGE_BACK_Y - GAUGE_Y)

    # housing (green) and bezel (chrome)
    bm = bmesh.new()
    e = R * 0.2
    prof = [(0.0, zb)]
    prof += [(R - e + e * sin(a), zb + e - e * cos(a)) for a in arc(0, 90, 6)]
    prof += [(R - e + e * cos(a), -e + e * sin(a)) for a in arc(0, 90, 6)]
    prof += [(0.0, 0.0)]
    lathe(bm, prof, 64, G, mi=0)
    ring = [(Rb + wr * cos(2 * pi * i / 16), 0.01 + hz * sin(2 * pi * i / 16)) for i in range(16)]
    lathe(bm, ring, 64, G, closed=True, mi=1)
    housing = finish(coll, "Eye_%s_Housing" % key, bm, [mats["green"], mats["chrome"]])

    # dial: black face, green band, red zone, ticks, numbers
    bm = bmesh.new()
    lathe(bm, [(0.0, 0.004), (Rb, 0.004)], 64, mi=0)

    def band(r0, r1, v0, v1, z, mi, steps=48):
        a0, a1 = dial_angle(g, v0), dial_angle(g, v1)
        inner, outer = [], []
        for i in range(steps + 1):
            a = a0 + (a1 - a0) * i / steps
            inner.append(bm.verts.new((r0 * cos(a), r0 * sin(a), z)))
            outer.append(bm.verts.new((r1 * cos(a), r1 * sin(a), z)))
        for i in range(steps):
            bm.faces.new((inner[i], inner[i + 1], outer[i + 1], outer[i])).material_index = mi

    def tick(value, r0, r1, w, z=0.0075):
        a = dial_angle(g, value)
        er, et = Vector((cos(a), sin(a), 0.0)), Vector((-sin(a), cos(a), 0.0))
        up = Vector((0.0, 0.0, z))
        quad = [er * r0 - et * w / 2, er * r0 + et * w / 2, er * r1 + et * w / 2, er * r1 - et * w / 2]
        bm.faces.new([bm.verts.new(q + up) for q in quad]).material_index = 1

    def label(words, size, pos, embolden=0.0):
        merge(bm, text_mesh(words, size, font, embolden), Matrix.Translation((pos[0], pos[1], 0.0075)), 1)

    if g is SPEEDO:
        band(0.925 * Rv, 0.965 * Rv, 0, 240, 0.006, 2)
        for v in range(0, 241, 10):
            major = v % 20 == 0
            tick(v, (0.75 if major else 0.82) * Rv, 0.90 * Rv, 0.007 if major else 0.004)
        for v in range(0, 241, 40):
            a = dial_angle(g, v)
            label(str(v), 0.046, (0.57 * Rv * cos(a), 0.57 * Rv * sin(a)), 0.0012)
        label("km/h", 0.042, (0.0, -0.46 * Rv), 0.001)
    else:
        band(0.925 * Rv, 0.965 * Rv, 0, 6.5, 0.006, 2)
        band(0.86 * Rv, 0.965 * Rv, 6.5, 8, 0.006, 3, steps=12)
        for i in range(0, 17):
            major = i % 2 == 0
            tick(i / 2, (0.75 if major else 0.82) * Rv, 0.90 * Rv, 0.006 if major else 0.0035)
        for v in range(0, 9):
            a = dial_angle(g, v)
            label(str(v), 0.038, (0.60 * Rv * cos(a), 0.60 * Rv * sin(a)), 0.001)
        label("RPM", 0.03, (0.0, -0.40 * Rv), 0.001)
        label("x1000", 0.02, (0.0, -0.62 * Rv), 0.0006)
    bmesh.ops.transform(bm, matrix=G, verts=bm.verts[:])
    dial = finish(coll, "Eye_%s_Dial" % key, bm, [mats["gloss"], mats["mark"], mats["dial_green"], mats["red"]],
                  sharp=None, recalc=False)

    # needle (points at 12 o'clock at rest) and its centre cap
    bm = bmesh.new()
    length, hw, tail = 0.82 * Rv, 0.046 * Rv, 0.17 * Rv
    z0, z1 = 0.018, 0.026
    flat = [(-hw, -tail), (hw, -tail), (hw * 0.25, length), (-hw * 0.25, length)]
    lo = [bm.verts.new((x, y, z0)) for x, y in flat]
    hi = [bm.verts.new((x, y, z1)) for x, y in flat]
    bm.faces.new(lo)
    bm.faces.new(hi)
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((lo[i], lo[j], hi[j], hi[i]))
    rc, capz, caph = 0.15 * Rv, 0.016, 0.125 * Rv
    cap = [(0.0, capz), (rc, capz)]
    cap += [(rc * cos(a), capz + caph * 0.4 + caph * 0.6 * sin(a)) for a in arc(0, 90, 5)]
    lathe(bm, cap, 32, mi=1)
    bmesh.ops.transform(bm, matrix=G, verts=bm.verts[:])
    needle = finish(coll, "Eye_%s_Needle" % key, bm, [mats["red"], mats["gloss"]], sharp=50)

    # glass: a shallow dome over the dial
    bm = bmesh.new()
    rg, ze, zc = Rv + 0.006, 0.9 * hz, 0.9 * hz + 0.14 * Rv
    prof = [(rg * i / 8, ze + (zc - ze) * (1 - (i / 8) ** 2)) for i in range(0, 9)]
    lathe(bm, prof, 64, G)
    glass = finish(coll, "Eye_%s_Glass" % key, bm, [mats["glass"]], sharp=None, recalc=False)
    glass.visible_shadow = False
    return housing, dial, needle, glass


# ---------------------------------------------------------------- limbs, hands, shoes (left side)
def build_leg_bm():
    bm = bmesh.new()
    x = LEG_X
    tube(bm, (x, 0, HIP_Z + 0.07), (x, 0, ANKLE_Z - 0.08), LEG_R0, LEG_R1, rings=18)
    return bm


def build_arm_bm():
    bm = bmesh.new()
    tube(bm, (SHOULDER_X - 0.06, 0, ARM_Z), (WRIST_X + 0.03, 0, ARM_Z), ARM_R, ARM_R, rings=20)
    return bm


def build_shoe_bm():
    bm = bmesh.new()
    # upper: half an egg, larger at the toe
    # A ball cut below its middle, so the widest part sits a little above the sole.
    low = sin(radians(-24))
    dome = [(0.0, -0.18), (cos(radians(-24)), -0.18)]
    dome += [(cos(a), (sin(a) - low) / (1 - low)) for a in arc(-24, 90, 14)]
    dome[-1] = (0.0, 1.0)
    for v in lathe(bm, dome, 40, mi=0):
        x, y, z = v.co
        k = 1.0 - SHOE_TAPER * y
        v.co = Vector((LEG_X + x * SHOE_HW * k, SHOE_CY + y * SHOE_HL, SOLE_T + z * SHOE_H))
    # sole
    e, ez = 0.06, 0.3
    sole = [(0.0, 0.0)]
    sole += [(1 - e + e * sin(a), ez - ez * cos(a)) for a in arc(0, 90, 4)]
    sole += [(1 - e + e * cos(a), 1 - ez + ez * sin(a)) for a in arc(0, 90, 4)]
    sole += [(0.0, 1.0)]
    for v in lathe(bm, sole, 40, mi=1):
        x, y, z = v.co
        k = 1.0 - SHOE_TAPER * y
        v.co = Vector((LEG_X + x * (SHOE_HW - 0.004) * k, SHOE_CY + y * (SHOE_HL - 0.004), z * SOLE_T))
    # the rolled collar round the ankle
    torus(bm, (LEG_X, 0, ANKLE_Z - 0.01), (0, 0, 1), 0.10, 0.055, mi=0)
    return bm


HAND_PARTS = {1: "hand"}


def build_hand_bm():
    bm = bmesh.new()
    layer = bm.verts.layers.int.new("part")

    def tag(pid, bone):
        HAND_PARTS[pid] = bone
        for v in bm.verts:
            if v[layer] == 0:
                v[layer] = pid

    torus(bm, (CUFF_X, 0, ARM_Z), (1, 0, 0), CUFF_R, CUFF_r)
    rounded_box(bm, PALM_C, PALM_SIZE, PALM_R, 5)
    tag(1, "hand")
    pid = 2
    for name, y, k in FINGERS:
        a = Vector((FINGER_X, y, ARM_Z))
        b = a + Vector((FINGER_L1 * k, 0, 0))
        c = b + Vector((FINGER_L2 * k, 0, 0))
        capsule(bm, a, b, FINGER_R)
        tag(pid, name + ".01")
        capsule(bm, b, c, FINGER_R * 0.96)
        tag(pid + 1, name + ".02")
        pid += 2
    t1 = THUMB_0 + THUMB_DIR * THUMB_L1
    t2 = t1 + THUMB_DIR * THUMB_L2
    capsule(bm, THUMB_0, t1, THUMB_R)
    tag(pid, "thumb.01")
    capsule(bm, t1, t2, THUMB_R * 0.96)
    tag(pid + 1, "thumb.02")
    return bm


# ---------------------------------------------------------------- brake lights (on the back)
def build_brake_lights_bm():
    """Two full domes, one each side of the middle. The lenses are material 0, their rubber
    rims material 1."""
    bm = bmesh.new()
    full = 2.0 / 2.6  # fuller than a ball's cap, so the lens has a shoulder to catch the light
    dome = [(BRAKE_R, -0.012)]  # it starts a little inside the body
    dome += [(BRAKE_R * cos(a) ** full, BRAKE_PROUD * sin(a) ** full) for a in arc(0, 90, 12)]
    dome[-1] = (0.0, BRAKE_PROUD)
    for side in (-1, 1):
        x = side * BRAKE_X
        lathe(bm, dome, 40, Matrix.Translation((x, BACK_Y, BRAKE_Z)) @ z_to((0, 1, 0)), mi=0)
        torus(bm, (x, BACK_Y + BRAKE_RIM * 0.45, BRAKE_Z), (0, 1, 0), BRAKE_R + BRAKE_RIM * 0.55, BRAKE_RIM, segs=40, mi=1)
    return bm


def light_up(lens, arm):
    """Ties the lens's glow to the rig's slider "brake": unlit at 0, BRAKE_GLOW at 1. The
    material is kept from one build to the next, so a rule left by the last build goes first."""
    tree = lens.node_tree
    if tree.animation_data:
        for old in list(tree.animation_data.drivers):
            tree.animation_data.drivers.remove(old)
    shader = next(n for n in tree.nodes if n.type == "BSDF_PRINCIPLED")
    drv = tree.driver_add(shader.inputs["Emission Strength"].path_from_id("default_value")).driver
    drv.type = "SCRIPTED"
    var = drv.variables.new()
    var.name = "v"
    var.type = "SINGLE_PROP"
    var.targets[0].id = arm
    var.targets[0].data_path = '["brake"]'
    drv.expression = "v * %g" % BRAKE_GLOW


# ---------------------------------------------------------------- the hat
def hat_place():
    """Where the hat is and how it is tilted: its own up is Z and its front is -Y."""
    return (Matrix.Translation(HAT_AT) @ Matrix.Rotation(radians(HAT_ROLL), 4, "Y")
            @ Matrix.Rotation(radians(HAT_TIP), 4, "X"))


def build_hat_bm():
    """A fedora: felt is material 0, the band material 1. It is turned like a round hat on a
    wheel and then pressed into shape: made oval, the crown's front pinched in from both sides
    and creased along its top, the brim snapped down in front and up behind. Open underneath,
    inside the crown, where the dials are."""
    bm = bmesh.new()
    a, b, h, w, t = HAT_A, HAT_B, HAT_H, HAT_BRIM, HAT_FELT
    side = 0.76 * h  # the crown's side goes up to here, and leans in a little on the way

    def lean(z):
        return 1.0 - 0.08 * (min(z, side) / side) ** 1.3

    # A profile point is (across, height): across up to 1 is a part of the crown's half width,
    # what is more than 1 is that far out along the brim.
    felt = [(0.95, -t), (1.0 + w - t, -t)]
    felt += [(1.0 + w - t / 2 + t / 2 * cos(u), -t / 2 + t / 2 * sin(u)) for u in arc(-90, 90, 6)]
    felt += [(1.0 + w * 0.5, 0.0), (1.012, 0.0)]
    felt += [(lean(side * i / 6), side * i / 6) for i in range(7)]
    felt += [(0.70 + (lean(side) - 0.70) * cos(u), side + (h - side) * sin(u)) for u in arc(0, 90, 6)[1:]]
    felt += [(0.5, h), (0.26, h), (0.0, h)]
    band = [(lean(z) - (0.03 if tucked else 0.0), z)
            for z, tucked in ((0.012, True), (0.012, False), (0.055, False), (0.1, False), (0.1, True))]

    for profile, proud, mi in ((felt, 0.0, 0), (band, 0.009, 1)):
        for v in lathe(bm, profile, 56, mi=mi):
            x, y, z = v.co
            across = hypot(x, y)
            if across < 1e-9:
                c = s = 0.0
            else:
                c, s = x / across, y / across
            crown = min(across, 1.0)
            out = max(across - 1.0, 0.0)
            x, y = (a * crown + out + proud) * c, (b * crown + out + proud) * s
            front = -y / b  # 1 at the crown's front, -1 at its back
            if z > 0.0 and out < 0.02:
                up = z / h
                high = min(1.0, max(0.0, (up - 0.25) / 0.75))
                x *= 1.0 - HAT_PINCH * high * high * (3 - 2 * high) * max(0.0, front) ** 1.5
                top = min(1.0, max(0.0, (up - 0.72) / 0.28))
                z -= HAT_CREASE * top * exp(-(x / (0.34 * a)) ** 2) * (0.7 + 0.3 * front)
                z *= 1.0 + 0.07 * front * high
            elif out > 0.0:
                z -= HAT_SNAP * (out / w) * (-y / (b + w)) - 0.03 * (out / w) ** 2 * (x / (a + w)) ** 2
            v.co = (x, y, z)
    bmesh.ops.transform(bm, matrix=hat_place(), verts=bm.verts[:])
    return bm


# ---------------------------------------------------------------- the party cap
def housing_radius(g, y):
    """How far a dial's round housing reaches from the dial's axis at the depth y: it is rounded
    off at its front and at its back (build_gauge)."""
    e = g["R"] * 0.2
    into = max(GAUGE_Y + e - y, y - (GAUGE_BACK_Y - e), 0.0)
    return g["R"] - e + sqrt(max(e * e - into * into, 0.0))


def cap_seat():
    """The middle of the cap's rim, on the big dial's housing; the way the housing faces there;
    and the cap's tip."""
    seat_at, tip_at, back = radians(CAP_SEAT), radians(CAP_SEAT + CAP_LEAN), radians(CAP_BACK)
    out = Vector((-sin(seat_at), 0.0, cos(seat_at)))
    seat = Vector((SPEEDO["cx"], (GAUGE_Y + GAUGE_BACK_Y) / 2, SPEEDO["cz"])) + out * SPEEDO["R"]
    tip = seat + CAP_H * Vector((-sin(tip_at) * cos(back), sin(back), cos(tip_at) * cos(back)))
    return seat, out, tip


def build_cap_bm():
    """A paper cone: the paper is material 0, its stripes material 1, the rolled rim material 2
    and the pom-pom material 3. Its rim is laid on the dial's housing, which is round, so the rim is
    not flat; every line of the cone runs straight from the rim to the tip. The points of the
    mesh wind round with the stripes, so a stripe's edge is an edge of the mesh and is clean."""
    bm = bmesh.new()
    seat, out, tip = cap_seat()
    across = Vector((out.z, 0.0, -out.x))
    back = Vector((0.0, 1.0, 0.0))
    axis = Vector((SPEEDO["cx"], 0.0, SPEEDO["cz"]))

    def rim(turn, wider=0.0, sunk=0.006):
        """A point of the rim, laid on the housing, and the way the housing faces there."""
        p = seat + (CAP_R + wider) * (cos(turn) * across + sin(turn) * back)
        radial = Vector((p.x - axis.x, 0.0, p.z - axis.z)).normalized()
        return axis + Vector((0.0, p.y, 0.0)) + radial * (housing_radius(SPEEDO, p.y) - sunk), radial

    wide = 5  # faces across a stripe
    segs, rings, top = 2 * CAP_STRIPES * wide, 16, 0.94
    rows = []
    for i in range(rings + 1):
        up = top * i / rings
        row = []
        for j in range(segs):
            foot = rim(2 * pi * (j / segs + CAP_TURNS * up))[0]
            row.append(bm.verts.new(foot + (tip - foot) * up))
        rows.append(row)
    for low, high in zip(rows[:-1], rows[1:]):
        for j in range(segs):
            k = (j + 1) % segs
            bm.faces.new((low[j], low[k], high[k], high[j])).material_index = (j // wide) % 2
    closed = bm.verts.new(seat + (tip - seat) * 0.98)
    for j in range(segs):
        bm.faces.new((rows[-1][j], rows[-1][(j + 1) % segs], closed)).material_index = (j // wide) % 2

    # the rim, rolled outwards: a thin ring that lies on the housing
    around = 8
    rolls = []
    for j in range(segs):
        turn = 2 * pi * j / segs
        foot, up = rim(turn, wider=CAP_ROLL * 0.5, sunk=-CAP_ROLL * 0.75)
        outward = (cos(turn) * across + sin(turn) * back).normalized()
        rolls.append([bm.verts.new(foot + CAP_ROLL * (cos(2 * pi * u / around) * outward + sin(2 * pi * u / around) * up))
                      for u in range(around)])
    for j in range(segs):
        a, b = rolls[j], rolls[(j + 1) % segs]
        for u in range(around):
            v = (u + 1) % around
            bm.faces.new((a[u], b[u], b[v], a[v])).material_index = 2

    # the pom-pom: a ball of wool, a little lumpy, with the cone's tip inside it
    middle = seat + (tip - seat) * 0.985
    ball = [(POM_R * cos(a), POM_R * sin(a)) for a in arc(-90, 90, 10)]
    ball[0], ball[-1] = (0.0, -POM_R), (0.0, POM_R)
    for v in lathe(bm, ball, 20, mi=3):
        d = v.co.normalized()
        v.co = middle + v.co * (1.0 + 0.09 * sin(9 * d.x + 1.0) * sin(8 * d.y + 2.0) * sin(10 * d.z + 0.5))
    return bm


# ---------------------------------------------------------------- the party popper
POPPER_PARTS = {1: "popper", 2: "popper_string", 3: "popper_pull"}


def popper_place():
    """Where the popper is in the rest pose and how it lies: its own up, +Z, is the way its
    mouth points, which is the way of the thumb's side of the hand, to the front."""
    along, under = POPPER_GRIP
    side = 1.0 if POPPER_SIDE == "L" else -1.0
    return Matrix.Translation((side * (WRIST_X + along), 0.0, ARM_Z - under)) @ z_to((0, -1, 0))


def build_popper_bm():
    """A cone on a handle, with a knob on its tail and a string with a bead out of that. Red is
    material 0, white 1, lime 2 and the dark inside of its mouth 3. Built upright, its mouth up
    and the middle of the fist at 0, and then laid into the hand. Its points are numbered in
    the layer "part" for the three bones that carry it (POPPER_PARTS)."""
    bm = bmesh.new()
    layer = bm.verts.layers.int.new("part")

    def tag(part):
        for v in bm.verts:
            if v[layer] == 0:
                v[layer] = part

    handle, foot, mouth = POPPER_R
    tail, neck, top = POPPER_TAIL, POPPER_NECK, POPPER_MOUTH

    def wide(z):
        return foot + (mouth - foot) * (z - neck) / (top - neck)

    # the knob on the tail, and the handle the fist is closed round
    knob = [(0.0, tail), (0.034, tail)] + [(0.034 + 0.022 * sin(a), tail + 0.026 - 0.026 * cos(a)) for a in arc(0, 180, 8)]
    lathe(bm, knob + [(handle, tail + 0.052)], 24, mi=2)
    lathe(bm, [(handle, tail + 0.052), (handle, neck - 0.012)], 24, mi=1)
    # the cone: red, with a white band and a lime lip round its mouth
    band = (neck + 0.52 * (top - neck), neck + 0.74 * (top - neck))
    lathe(bm, [(handle, neck - 0.012), (foot, neck), (wide(band[0]), band[0])], 40, mi=0)
    lathe(bm, [(wide(band[0]), band[0]), (wide(band[1]), band[1])], 40, mi=1)
    lathe(bm, [(wide(band[1]), band[1]), (mouth, top)], 40, mi=0)
    torus(bm, (0, 0, top), (0, 0, 1), mouth, 0.013, segs=40, msegs=10, mi=2)
    lathe(bm, [(mouth - 0.004, top), (0.03, top - 0.2), (0.0, top - 0.2)], 40, mi=3)
    tag(1)
    tube(bm, (0, 0, tail + 0.01), (0, 0, tail - STRING_L), STRING_R, STRING_R, rings=1, segs=10, mi=1)
    tag(2)
    ball = [(BEAD_R * cos(a), tail - STRING_L + BEAD_R * sin(a)) for a in arc(-90, 90, 8)]
    ball[0], ball[-1] = (0.0, tail - STRING_L - BEAD_R), (0.0, tail - STRING_L + BEAD_R)
    lathe(bm, ball, 16, mi=2)
    tag(3)
    bmesh.ops.transform(bm, matrix=popper_place(), verts=bm.verts[:])
    return bm


# ---------------------------------------------------------------- the confetti
def build_confetti(rig):
    """The pieces the popper shoots: PAPERS pieces of paper, STREAMERS curled streamers and the
    flash at its mouth, in the collection CONFETTI. They are one mesh on a skeleton of their
    own, a bone to a piece, and every piece is built at that skeleton's foot: animate_milo.py
    moves them. Nothing of the collection is drawn or shown unless a script asks for it."""
    coll = reset_collection(CONFETTI)
    coll.hide_viewport = coll.hide_render = False
    rnd = random.Random(CONFETTI_SEED)
    colours = [principled("MilO_Confetti_" + name, srgb(colour), rough=0.6, emit=0.3) for name, colour in CONFETTI_COLOURS]
    # The flash glows in its own colour and takes none from the lights, or it is drawn white.
    flash = principled("MilO_Confetti_Flash", (1.0, 0.78, 0.16, 1.0), rough=0.9, emit=1.0)
    next(n for n in flash.node_tree.nodes if n.type == "BSDF_PRINCIPLED").inputs["Base Color"].default_value = (0.0, 0.0, 0.0, 1.0)

    bm = bmesh.new()
    layer = bm.verts.layers.int.new("part")
    bones = []

    def piece(name, verts):
        bones.append(name)
        for v in verts:
            v[layer] = len(bones)

    for i in range(PAPERS):
        # a slip of paper, or now and then a round one; the lime comes up twice as often
        colour = rnd.choice([0, 0, 1, 2, 3, 4, 5])
        if rnd.random() < 0.22:
            r = rnd.uniform(0.028, 0.038)
            verts = [bm.verts.new((r * cos(2 * pi * k / 10), r * sin(2 * pi * k / 10), 0.0)) for k in range(10)]
        else:
            w, h = rnd.uniform(0.03, 0.044), rnd.uniform(0.019, 0.028)
            verts = [bm.verts.new(p) for p in ((-w, -h, 0.0), (w, -h, 0.0), (w, h, 0.0), (-w, h, 0.0))]
        bm.faces.new(verts).material_index = colour
        piece("paper.%03d" % i, verts)

    length, curl, curls, width = STREAMER
    steps = 56
    for i in range(STREAMERS):
        # a ribbon wound round its own length, like a paper streamer
        colour, hand = (0, 3, 5, 4, 2, 1)[i % 6], 1.0 if i % 2 else -1.0
        edges = []
        for k in range(steps + 1):
            turn = hand * 2 * pi * curls * k / steps
            at = Vector((curl * cos(turn), length * k / steps, curl * sin(turn)))
            edges.append((bm.verts.new(at - Vector((0, width / 2, 0))), bm.verts.new(at + Vector((0, width / 2, 0)))))
        for (a, b), (c, d) in zip(edges[:-1], edges[1:]):
            bm.faces.new((a, c, d, b)).material_index = colour
        piece("streamer.%d" % i, [v for edge in edges for v in edge])

    # the flash at the popper's mouth: a star that faces the viewer, there for three frames
    points, tips, dips = 9, 0.28, 0.11
    star = [bm.verts.new(((dips if k % 2 else tips) * sin(pi * k / points), 0.0, (dips if k % 2 else tips) * cos(pi * k / points)))
            for k in range(2 * points)]
    middle = bm.verts.new((0.0, 0.0, 0.0))
    for k in range(2 * points):
        bm.faces.new((middle, star[k], star[(k + 1) % (2 * points)])).material_index = len(colours)
    piece("flash", star + [middle])

    mesh = finish(coll, "Confetti", bm, colours + [flash], sharp=None, recalc=False)
    # Paper casts no shadow here: it would flicker over his face, and a picture of the confetti
    # alone laid over one of him without it is then the picture of both.
    mesh.visible_shadow = False

    data = bpy.data.armatures.new("MilO_Confetti_Rig")
    arm = bpy.data.objects.new("MilO_Confetti_Rig", data)
    coll.objects.link(arm)
    for o in bpy.context.view_layer.objects:
        try:
            o.select_set(False)
        except RuntimeError:
            pass
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    for name in bones:
        b = data.edit_bones.new(name)
        b.head, b.tail = (0.0, 0.0, 0.0), (0.0, 0.1, 0.0)
        b.align_roll(Vector((0, 0, 1)))
    bpy.ops.object.mode_set(mode="OBJECT")
    for pb in arm.pose.bones:
        pb.rotation_mode = "XYZ"
    skin_by_part(mesh, arm, dict(enumerate(bones, 1)))
    # It goes where he goes, and is turned with him when a script turns him for a picture.
    arm.parent = rig
    arm.select_set(False)
    bpy.context.view_layer.objects.active = rig
    coll.hide_viewport = coll.hide_render = True
    return len(bones)


# ---------------------------------------------------------------- the smoke
def build_smoke(rig):
    """The puffs his shoes make: SMOKE_PUFFS of them in the collection SMOKE, one mesh on a
    skeleton of its own, a bone to a puff, every puff built at that skeleton's foot, as the
    confetti is: animate_milo.py lets them go. A puff is three to five round lumps grown into
    one another, like a small cloud. Nothing of the collection is drawn or shown unless a
    script asks for it."""
    coll = reset_collection(SMOKE)
    coll.hide_viewport = coll.hide_render = False
    rnd = random.Random(SMOKE_SEED)
    bm = bmesh.new()
    layer = bm.verts.layers.int.new("part")
    bones = []
    for i in range(SMOKE_PUFFS):
        # one large lump, and smaller ones round it, more to its sides than above and below
        lumps = [(Vector((0.0, 0.0, 0.0)), rnd.uniform(0.66, 0.78))]
        first = rnd.uniform(0.0, 2 * pi)
        more = rnd.choice((2, 3, 3, 4))
        for k in range(more):
            round_, r = first + 2 * pi * (k + rnd.uniform(-0.25, 0.25)) / more, rnd.uniform(0.40, 0.58)
            lumps.append((Vector((cos(round_), rnd.uniform(-0.5, 0.5), 0.7 * sin(round_))) * rnd.uniform(0.50, 0.66), r))
        made = []
        for at, r in lumps:
            # A ball of twenty faces, which the object rounds when it is drawn (below); rounding
            # takes a quarter off its width, which is put on here.
            ball = bmesh.ops.create_icosphere(bm, subdivisions=1, radius=r * 1.28)["verts"]
            bmesh.ops.translate(bm, vec=at, verts=ball)
            made += ball
        bones.append("puff.%03d" % i)
        for v in made:
            v[layer] = len(bones)

    mesh = finish(coll, "Smoke", bm, [smoke_material("MilO_Smoke")], sharp=None, recalc=False)
    # Smoke casts no shadow: a picture of him alone with one of the smoke alone laid over it is
    # then nearly the picture of both, and no puff darkens his shoes.
    mesh.visible_shadow = False

    data = bpy.data.armatures.new("MilO_Smoke_Rig")
    arm = bpy.data.objects.new("MilO_Smoke_Rig", data)
    coll.objects.link(arm)
    for o in bpy.context.view_layer.objects:
        try:
            o.select_set(False)
        except RuntimeError:
            pass
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    for name in bones:
        b = data.edit_bones.new(name)
        b.head, b.tail = (0.0, 0.0, 0.0), (0.0, 0.1, 0.0)
        b.align_roll(Vector((0, 0, 1)))
    bpy.ops.object.mode_set(mode="OBJECT")
    for pb in arm.pose.bones:
        pb.rotation_mode = "XYZ"
    skin_by_part(mesh, arm, dict(enumerate(bones, 1)))
    # After the skeleton has moved the puffs, each lump is rounded: the file keeps twelve
    # points to a lump, and a picture is drawn from a few hundred.
    rounded = mesh.modifiers.new("Round", "SUBSURF")
    rounded.levels, rounded.render_levels = 2, 3
    # It goes where he goes, and is turned with him when a script turns him for a picture.
    arm.parent = rig
    arm.select_set(False)
    bpy.context.view_layer.objects.active = rig
    coll.hide_viewport = coll.hide_render = True
    return len(bones)


def mirrored(coll, ob, name):
    me = ob.data.copy()
    me.name = name
    me.transform(Matrix.Scale(-1.0, 4, (1, 0, 0)))
    me.flip_normals()
    me.update()
    twin = bpy.data.objects.new(name, me)
    coll.objects.link(twin)
    return twin


# ---------------------------------------------------------------- rig
def build_rig(coll):
    data = bpy.data.armatures.new("MilO_Rig")
    arm = bpy.data.objects.new("MilO_Rig", data)
    coll.objects.link(arm)
    for o in bpy.context.view_layer.objects:
        try:
            o.select_set(False)
        except RuntimeError:
            pass
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    eb = data.edit_bones

    def add(name, head, tail, parent=None, connect=False, z=None, deform=True):
        b = eb.new(name)
        b.head, b.tail = Vector(head), Vector(tail)
        if parent:
            b.parent = eb[parent]
            b.use_connect = connect
        if z is not None:
            b.align_roll(Vector(z))
        b.use_deform = deform
        return b

    add("root", (0, 0, 0), (0, 0.45, 0), z=(0, 0, 1), deform=False)
    add("body", (0, 0, BODY_Z0), (0, 0, BODY_Z0 + 0.5), "root", z=(0, -1, 0))
    for g in (SPEEDO, TACH):
        # Both point out of the dial, so turning one about its own length (local Y) spins it.
        add("eye_" + g["bone"], (g["cx"], 0.0, g["cz"]), (g["cx"], -0.2, g["cz"]), "body", z=(0, 0, 1))
        add("needle_" + g["bone"], (g["cx"], GAUGE_Y, g["cz"]), (g["cx"], GAUGE_Y - 0.1, g["cz"]),
            "eye_" + g["bone"], z=(0, 0, 1))
    # Carries nothing: it only grows with the slider "brake" (below), so that a .glb file, which
    # can hold a bone's movement but neither a slider nor a material's glow, still says in every
    # clip how far the brake lights are on.
    add("brake", (0, BACK_Y, BRAKE_Z), (0, BACK_Y + 0.12, BRAKE_Z), "body", z=(0, 0, 1), deform=False)
    # The hat's bone points up through the hat, as the hat is tilted; its own Z is the hat's
    # front. Turning it about its own X tips the hat further forward.
    tilted = hat_place().to_3x3()
    add("hat", HAT_AT, HAT_AT + tilted @ Vector((0, 0, 0.3)), "body", z=tilted @ Vector((0, -1, 0)))
    # The party cap's bone points from the middle of its rim to its tip, and hangs on the big
    # dial: the cap sits on that dial's housing and goes with it when a clip turns or swells it.
    seat, _, tip = cap_seat()
    add("party_hat", seat, seat + (tip - seat).normalized() * 0.3, "eye_speedo", z=(0, -1, 0))

    for side, sx in ((".L", 1.0), (".R", -1.0)):
        def P(x, y, z, sx=sx):
            return (sx * x, y, z)

        down = (0, 0, -1)
        add("thigh" + side, P(LEG_X, 0, HIP_Z), P(LEG_X, -0.015, KNEE_Z), "body", z=(0, -1, 0))
        add("shin" + side, P(LEG_X, -0.015, KNEE_Z), P(LEG_X, 0, ANKLE_Z), "thigh" + side, True, z=(0, -1, 0))
        add("foot" + side, P(LEG_X, 0, 0), P(LEG_X, -0.35, 0), "root", z=(0, 0, 1))
        add("ankle_ik" + side, P(LEG_X, 0, ANKLE_Z), P(LEG_X, 0.12, ANKLE_Z), "foot" + side,
            z=(0, 0, 1), deform=False)

        add("upper_arm" + side, P(SHOULDER_X, 0, ARM_Z), P(ELBOW_X, 0, ARM_Z), "body", z=down)
        add("forearm" + side, P(ELBOW_X, 0, ARM_Z), P(WRIST_X, 0, ARM_Z), "upper_arm" + side, True, z=down)
        add("hand" + side, P(WRIST_X, 0, ARM_Z), P(WRIST_X + 0.2, 0, ARM_Z), "forearm" + side, True, z=down)
        for name, y, k in FINGERS:
            a = Vector((FINGER_X, y, ARM_Z))
            b = a + Vector((FINGER_L1 * k, 0, 0))
            c = b + Vector((FINGER_L2 * k, 0, 0))
            add(name + ".01" + side, P(*a), P(*b), "hand" + side, z=down)
            add(name + ".02" + side, P(*b), P(*c), name + ".01" + side, True, z=down)
        t1 = THUMB_0 + THUMB_DIR * THUMB_L1
        t2 = t1 + THUMB_DIR * THUMB_L2
        add("thumb.01" + side, P(*THUMB_0), P(*t1), "hand" + side, z=down)
        add("thumb.02" + side, P(*t1), P(*t2), "thumb.01" + side, True, z=down)

    # The party popper lies in one fist. Its bone reaches from the middle of the fist to its
    # mouth: the bone's end is where the confetti comes out. The string is a bone that reaches
    # from the tail to the bead (a rule below keeps it reaching), and the bead is a bone of its
    # own, which the other hand can take: "grip" is where that hand pinches it.
    place = popper_place()
    add("popper", place @ Vector((0, 0, 0)), place @ Vector((0, 0, POPPER_MOUTH)), "hand." + POPPER_SIDE, z=(0, 0, -1))
    bead = Vector((0, 0, POPPER_TAIL - STRING_L))
    add("popper_string", place @ Vector((0, 0, POPPER_TAIL)), place @ bead, "popper", z=(0, 0, -1))
    add("popper_pull", place @ bead, place @ (bead - Vector((0, 0, 0.08))), "popper", z=(0, 0, -1))
    along, to_thumb, under = PINCH
    pinch = Vector(((-1.0 if PULL_SIDE == "R" else 1.0) * (WRIST_X + along), -to_thumb, ARM_Z - under))
    add("grip." + PULL_SIDE, pinch, pinch + Vector((0, -0.08, 0)), "hand." + PULL_SIDE, z=(0, 0, -1), deform=False)

    # Arms and legs are hoses. Each control bone bends as a curve (a bendy bone), and a row of
    # short ordinary bones follows that curve and carries the mesh. Bendy bones alone do not
    # survive a glTF export; the short bones do, and the limb looks the same.
    hoses = []
    for control in [b.name for b in eb if b.name.split(".")[0] in HOSE_BONES]:
        c = eb[control]
        c.bbone_segments = 12
        c.use_deform = False
        base, side = control.rsplit(".", 1)
        step = (c.tail - c.head) / HOSE_STEPS
        # the lower bone of each limb gets one more, at its far end
        count = HOSE_STEPS + (1 if base in ("forearm", "shin") else 0)
        for i in range(count):
            h = eb.new("hose.%s.%d.%s" % (base, i, side))
            h.head = c.head + step * i
            h.tail = h.head + step
            h.roll = c.roll
            h.parent = c
            h.use_deform = True
            hoses.append((h.name, control, i / HOSE_STEPS))
    bpy.ops.object.mode_set(mode="OBJECT")

    for pb in arm.pose.bones:
        pb.rotation_mode = "XYZ"
    tucked = data.collections.new("Hose (automatic)")
    for name, control, along in hoses:
        follow = arm.pose.bones[name].constraints.new("COPY_TRANSFORMS")
        follow.target = arm
        follow.subtarget = control
        follow.head_tail = along
        follow.use_bbone_shape = True
        tucked.assign(data.bones[name])
    tucked.is_visible = False
    for side in (".L", ".R"):
        ik = arm.pose.bones["shin" + side].constraints.new("IK")
        ik.target = arm
        ik.subtarget = "ankle_ik" + side
        ik.chain_count = 2
        ik.use_stretch = True
        for name in ("thigh", "shin"):
            arm.pose.bones[name + side].ik_stretch = 0.25

    # Two sliders on the rig drive the needles.
    arm["speed_kmh"] = 80.0
    arm["rpm"] = 3.0
    arm.id_properties_ui("speed_kmh").update(min=0.0, max=240.0, soft_min=0.0, soft_max=240.0,
                                             description="Where the speedometer needle points")
    arm.id_properties_ui("rpm").update(min=0.0, max=8.0, soft_min=0.0, soft_max=8.0,
                                       description="Where the RPM needle points, in thousands")
    for bone, prop, expr in (("needle_speedo", "speed_kmh", "radians(120 - v)"),
                             ("needle_rpm", "rpm", "radians(120 - v * 30)")):
        fc = arm.driver_add('pose.bones["%s"].rotation_euler' % bone, 1)
        drv = fc.driver
        drv.type = "SCRIPTED"
        var = drv.variables.new()
        var.name = "v"
        var.type = "SINGLE_PROP"
        var.targets[0].id = arm
        var.targets[0].data_path = '["%s"]' % prop
        drv.expression = expr

    # A third slider lights the brake lights: 0 is unlit, 1 is full on. The lenses' material
    # follows it (light_up), and so does the size of the bone "brake", from 1 to 2.
    arm["brake"] = 0.0
    arm.id_properties_ui("brake").update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0,
                                         description="How far the brake lights are on")
    for fc in arm.driver_add('pose.bones["brake"].scale'):
        drv = fc.driver
        drv.type = "SCRIPTED"
        var = drv.variables.new()
        var.name = "v"
        var.type = "SINGLE_PROP"
        var.targets[0].id = arm
        var.targets[0].data_path = '["brake"]'
        drv.expression = "1 + v"

    # A fourth slider puts the hat on: 0 is no hat, 1 is the hat. It is the size of the bone
    # "hat", which carries the hat: at 0 the whole hat is one point inside the dials, and
    # nothing of it is drawn. So every clip that does not ask for the hat is drawn without it.
    arm["hat"] = 0.0
    arm.id_properties_ui("hat").update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0,
                                       description="The hat: 0 is none, 1 is on")
    for fc in arm.driver_add('pose.bones["hat"].scale'):
        drv = fc.driver
        drv.type = "SCRIPTED"
        var = drv.variables.new()
        var.name = "v"
        var.type = "SINGLE_PROP"
        var.targets[0].id = arm
        var.targets[0].data_path = '["hat"]'
        drv.expression = "v"

    # Three more sliders are for the party: "party_hat" puts the party cap on and "popper"
    # puts the popper in his fist, each as "hat" puts the fedora on: it is the size of the bone
    # that carries the thing, and at 0, where the file is saved, nothing of it is drawn.
    # "pull" puts the bead on the popper's string into the other hand: at 0 it hangs straight
    # out of the tail, at 1 it is where that hand pinches, and the string reaches after it.
    for prop, bone, what in (("party_hat", "party_hat", "The party cap: 0 is none, 1 is on"),
                             ("popper", "popper", "The party popper in his fist: 0 is none, 1 is there")):
        arm[prop] = 0.0
        arm.id_properties_ui(prop).update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0, description=what)
        for fc in arm.driver_add('pose.bones["%s"].scale' % bone):
            tie(fc.driver, arm, prop, "v")
    arm["pull"] = 0.0
    arm.id_properties_ui("pull").update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0,
                                        description="The popper's string: 0 hangs, 1 is in the other hand")
    take = arm.pose.bones["popper_pull"].constraints.new("COPY_LOCATION")
    take.name = "Taken"
    take.target = arm
    take.subtarget = "grip." + PULL_SIDE
    tie(arm.driver_add('pose.bones["popper_pull"].constraints["Taken"].influence').driver, arm, "pull", "v")
    reach = arm.pose.bones["popper_string"].constraints.new("STRETCH_TO")
    reach.name = "Reaches"
    reach.target = arm
    reach.subtarget = "popper_pull"
    reach.rest_length = STRING_L
    reach.volume = "NO_VOLUME"
    # With the popper away its bones have no size, and a string cannot reach for a bead that
    # is nowhere: the rule is off until the popper is there.
    tie(arm.driver_add('pose.bones["popper_string"].constraints["Reaches"].influence').driver, arm, "popper", "ceil(v)")
    arm.show_in_front = True
    return arm


def tie(drv, arm, prop, expression):
    """Makes a driver follow one of the rig's sliders: `expression` says how, with v the slider."""
    drv.type = "SCRIPTED"
    var = drv.variables.new()
    var.name = "v"
    var.type = "SINGLE_PROP"
    var.targets[0].id = arm
    var.targets[0].data_path = '["%s"]' % prop
    drv.expression = expression


def bind(ob, arm):
    ob.parent = arm
    mod = ob.modifiers.new("Armature", "ARMATURE")
    mod.object = arm


def rigid(ob, arm, bone):
    ob.vertex_groups.new(name=bone).add(list(range(len(ob.data.vertices))), 1.0, "REPLACE")
    bind(ob, arm)


def skin_parts(ob, arm, side):
    me = ob.data
    vals = [0] * len(me.vertices)
    me.attributes["part"].data.foreach_get("value", vals)
    groups = {}
    for i, pid in enumerate(vals):
        groups.setdefault(pid, []).append(i)
    for pid, idx in groups.items():
        ob.vertex_groups.new(name=HAND_PARTS[pid] + side).add(idx, 1.0, "REPLACE")
    me.attributes.remove(me.attributes["part"])
    bind(ob, arm)


def skin_by_part(ob, arm, bones):
    """A mesh whose points are numbered in the layer "part": each number's points follow the
    bone `bones` names for it."""
    me = ob.data
    vals = [0] * len(me.vertices)
    me.attributes["part"].data.foreach_get("value", vals)
    groups = {}
    for i, pid in enumerate(vals):
        groups.setdefault(pid, []).append(i)
    for pid, idx in groups.items():
        ob.vertex_groups.new(name=bones[pid]).add(idx, 1.0, "REPLACE")
    me.attributes.remove(me.attributes["part"])
    bind(ob, arm)


def skin_hose(ob, arm, upper, lower, side, along):
    """A hose mesh: each ring leans on the two short bones it lies between.
    `along` gives a vertex's distance down the limb from its first joint."""
    bones = arm.data.bones
    chain, reach = [], 0.0
    for base in (upper, lower):
        length = bones["%s.%s" % (base, side)].length
        count = sum(1 for b in bones if b.name.startswith("hose.%s." % base) and b.name.endswith("." + side))
        chain += [("hose.%s.%d.%s" % (base, i, side), reach + length * i / HOSE_STEPS) for i in range(count)]
        reach += length
    groups = [ob.vertex_groups.new(name=name) for name, _ in chain]
    marks = [mark for _, mark in chain]
    for v in ob.data.vertices:
        d = along(v.co)
        j = max(0, min(len(marks) - 2, sum(1 for mark in marks if mark <= d) - 1))
        f = max(0.0, min(1.0, (d - marks[j]) / (marks[j + 1] - marks[j])))
        if f < 1.0:
            groups[j].add([v.index], 1.0 - f, "REPLACE")
        if f > 0.0:
            groups[j + 1].add([v.index], f, "REPLACE")
    bind(ob, arm)


# ---------------------------------------------------------------- build
def build():
    if bpy.context.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    coll = reset_collection(COLL)
    mats = make_materials()
    font = bpy.data.fonts.load(SORA, check_existing=True)

    body, black_faces = build_body(coll, mats)
    eyes = {g["bone"]: build_gauge(coll, mats, g, font) for g in (SPEEDO, TACH)}

    leg_l = finish(coll, "Leg.L", build_leg_bm(), [mats["black"]])
    arm_l = finish(coll, "Arm.L", build_arm_bm(), [mats["black"]])
    shoe_l = finish(coll, "Shoe.L", build_shoe_bm(), [mats["green"], mats["black"]], sharp=50)
    hand_l = finish(coll, "Hand.L", build_hand_bm(), [mats["white"]], sharp=60)
    leg_r, arm_r = mirrored(coll, leg_l, "Leg.R"), mirrored(coll, arm_l, "Arm.R")
    shoe_r, hand_r = mirrored(coll, shoe_l, "Shoe.R"), mirrored(coll, hand_l, "Hand.R")
    lights = finish(coll, "Brake_Lights", build_brake_lights_bm(), [mats["brake"], mats["black"]], sharp=50)
    # They cast no shadow, like the glass over the dials. The studio's rim light stands behind
    # him, and with a shadow the lights darkened the top of a shoe a little in every picture
    # taken from the front: a few hundred pixels, but the pictures he has are not to change.
    lights.visible_shadow = False
    hat = finish(coll, "Hat", build_hat_bm(), [mats["felt"], mats["green"]], sharp=50)
    cap = finish(coll, "Party_Cap", build_cap_bm(),
                 [mats["party_lime"], mats["party_green"], mats["party_white"], mats["pom"]], sharp=50)
    popper = finish(coll, "Popper", build_popper_bm(),
                    [mats["party_red"], mats["party_white"], mats["party_lime"], mats["black"]], sharp=50)

    rig = build_rig(coll)
    rigid(body, rig, "body")
    rigid(lights, rig, "body")
    rigid(hat, rig, "hat")
    rigid(cap, rig, "party_hat")
    skin_by_part(popper, rig, POPPER_PARTS)
    light_up(mats["brake"], rig)
    # The big flat faces set the shading, so the front stays flat right up to its rounded edges.
    flat = body.modifiers.new("Flat faces", "WEIGHTED_NORMAL")
    flat.keep_sharp = True
    flat.weight = 100
    for name, (housing, dial, needle, glass) in eyes.items():
        for ob in (housing, dial, glass):
            rigid(ob, rig, "eye_" + name)
        rigid(needle, rig, "needle_" + name)
    for side, leg, arm, shoe, hand in ((".L", leg_l, arm_l, shoe_l, hand_l), (".R", leg_r, arm_r, shoe_r, hand_r)):
        skin_hose(leg, rig, "thigh", "shin", side[1:], lambda co: HIP_Z - co.z)
        skin_hose(arm, rig, "upper_arm", "forearm", side[1:], lambda co: abs(co.x) - SHOULDER_X)
        rigid(shoe, rig, "foot" + side)
        skin_parts(hand, rig, side)

    pieces = build_confetti(rig)
    puffs = build_smoke(rig)
    bpy.context.view_layer.update()
    print("built %d objects, %d bones, M faces painted black: %d; confetti: %d pieces; smoke: %d puffs"
          % (len(coll.objects), len(rig.data.bones), black_faces, pieces, puffs))
    return rig


build()

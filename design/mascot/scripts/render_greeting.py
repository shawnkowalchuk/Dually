# render_greeting.py - draws the app's greeting as pictures: the mascot walking toward the left,
# and the mascot turning to face the viewer and waving. Run it after the other scripts, with
# Blender WITHOUT its window, and name two folders for the frames:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_greeting.py -- <folder for the walk> <folder for the turn and wave>
#
# Each folder gets numbered PNG frames with a see-through background, all of one size, from the
# studio's camera and under the studio's lights. pack_greeting.py then makes the two pictures
# the app carries out of them. What this script prints at the end (the picture's size,
# the frames, the stride, how far the mascot reaches ahead, where its soles are) is what the
# app's code is told, in core/designsystem/component/MascotWalk.kt.
#
#   the walk           every second frame of the clip "Walk", so one cycle is WALK_FRAMES frames,
#                      with the mascot turned WALK_ANGLE degrees so that it walks to the left
#   the turn and wave  from the walk's first pose and angle to the standing pose, facing the
#                      viewer, in TURN_FRAMES frames with a small hop; then the clip "Wave"
#
# The turn and wave's first frame is the frame the walk would show next, so the one follows the
# other without a jump; its last frame is the standing pose.
#
# NOTHING IS SAVED. The script hides the backdrop, turns the mascot, changes the camera's lens,
# takes the shine off the dials' black faces and writes two clips of its own, all in memory, and
# Blender closes without saving. It changes no collection, no stored clip and no stored material.

import os
import sys
import types
import bpy
import numpy
from math import radians, sin, pi
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

# ---------------------------------------------------------------- what is drawn
# The app draws the standing mascot MASCOT_DP high, whatever the phone's density. The picture
# has two and a half pixels to the dp: a current phone has 2.6 to 3, and at three the two
# pictures together were over a megabyte without looking better on a phone.
PX_PER_DP = 2.5
MASCOT_DP = 150
# The picture. The mascot's axis, about which it turns, is the picture's middle: so it is wide
# enough for the waving hand on one side, and as wide on the other. High enough for the hop.
PICTURE_DP = (240, 168)
# How far above the picture's lower edge the soles are while the mascot walks.
FEET_DP = 2

# Degrees the mascot is turned away from the viewer while it walks. Far enough that it clearly
# walks to the left, not so far that its face is lost: the body is a slab, and from about 60
# degrees on the M and the dials are seen from the side.
WALK_ANGLE = 55.0
# One cycle of "Walk" is 24 frames. Every second one is drawn, so the mascot takes its steps
# twice as fast as the clip does on its own: its stride is short, and at the clip's own speed
# the walk to the middle of a phone would take eight seconds.
WALK_FRAMES = 12
TURN_FRAMES = 11
# How high the mascot hops while it turns, so that its soles do not grind round on the ground.
HOP = 0.06
WAVE_FRAMES = 48
FPS = 24

# The halo round the brake lights where they are lit (on_ground). What glows more strongly than
# HALO_FROM gets one, and white is 1: so a lit lens does, and nothing else the mascot is made of.
HALO_FROM = 1.0
HALO_STRENGTH = 1.6
HALO_SIZE = 0.5
# In a see-through picture the halo has to end before the picture does, or the picture's edge
# shows as the edge of a faint red square. Blender's glow never quite ends: far from the lights
# it is still this faint, as a part of full brightness. So this much is taken off it everywhere
# beside the mascot, and what is fainter is left out (halo_see_through).
HALO_ENDS = 0.13


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


clips = library("animate_milo.py")
rig = clips.rig
pb = clips.pb
scene = bpy.context.scene
camera = scene.camera


# ---------------------------------------------------------------- the two clips, in memory
def pose_now():
    """Everything a clip keys, as it stands."""
    return dict(
        rotation={name: tuple(pb[name].rotation_euler) for name in clips.ROTATED},
        location={name: tuple(pb[name].location) for name in clips.MOVED},
        scale={name: tuple(pb[name].scale) for name in clips.SCALED},
        gauges=(rig["speed_kmh"], rig["rpm"]),
    )


def pose_between(a, b, w):
    """Sets the pose that lies the part w of the way from a to b."""
    def mix(x, y):
        return tuple(p + (q - p) * w for p, q in zip(x, y))

    for name in clips.ROTATED:
        pb[name].rotation_euler = mix(a["rotation"][name], b["rotation"][name])
    for name in clips.MOVED:
        pb[name].location = mix(a["location"][name], b["location"][name])
    for name in clips.SCALED:
        pb[name].scale = mix(a["scale"][name], b["scale"][name])
    rig["speed_kmh"], rig["rpm"] = mix(a["gauges"], b["gauges"])


def key(frame, angle=0.0, lifted=0.0):
    """Keys the pose as it stands, with the whole mascot turned and lifted."""
    clips.key_pose(frame)
    rig.rotation_euler = (0.0, 0.0, -radians(angle))
    rig.location = (0.0, 0.0, lifted)
    rig.keyframe_insert("rotation_euler", frame=frame)
    rig.keyframe_insert("location", frame=frame)


def write_walk():
    """One cycle. The frame after the last is the first again."""
    clips.reset_pose()
    clips.new_action("Greeting_Walk", WALK_FRAMES - 1, True)
    for frame in range(WALK_FRAMES):
        clips.walk(frame / WALK_FRAMES)
        key(frame, angle=WALK_ANGLE)
    return WALK_FRAMES


def write_turn_and_wave():
    """Starts on the walk's first pose, which is the frame the walk would show next."""
    clips.reset_pose()
    clips.walk(0.0)
    walking = pose_now()
    clips.neutral()
    standing = pose_now()
    clips.new_action("Greeting_TurnAndWave", TURN_FRAMES + WAVE_FRAMES, False)
    for frame in range(TURN_FRAMES):
        along = frame / TURN_FRAMES
        w = clips.smooth(along)
        pose_between(walking, standing, w)
        key(frame, angle=WALK_ANGLE * (1 - w), lifted=HOP * sin(pi * along))
    for frame in range(WAVE_FRAMES + 1):
        clips.wave(frame / FPS)
        key(TURN_FRAMES + frame)
    return TURN_FRAMES + WAVE_FRAMES + 1


# ---------------------------------------------------------------- the picture
def mascot_points():
    """Every point of the mascot's surface as it is posed now, in the scene."""
    graph = bpy.context.evaluated_depsgraph_get()
    points = []
    for ob in bpy.data.collections["MilO_Mascot"].objects:
        if ob.type != "MESH":
            continue
        posed = ob.evaluated_get(graph)
        mesh = posed.to_mesh()
        points += [posed.matrix_world @ v.co for v in mesh.vertices]
        posed.to_mesh_clear()
    return points


def seen(point):
    """Where the camera draws a point of the scene: across and up, each from 0 to 1."""
    at = world_to_camera_view(scene, camera, Vector(point))
    return at.x, at.y


def set_stage(width=None, height=None, mascot=MASCOT_DP * PX_PER_DP):
    """A see-through background, and the picture's size. The camera stays where the studio put
    it: only its lens and the part of its view that is kept change, so that the standing mascot
    is MASCOT_DP high and the axis it turns about is the picture's middle. render_clips.py asks
    for a picture and a mascot of its own size, in pixels; the greeting asks for nothing."""
    if width is None:
        width, height = (round(side * PX_PER_DP) for side in PICTURE_DP)
    render = scene.render
    render.film_transparent = True
    render.resolution_x, render.resolution_y, render.resolution_percentage = width, height, 100
    render.image_settings.file_format = "PNG"
    render.image_settings.color_mode = "RGBA"
    render.image_settings.color_depth = "8"
    render.use_file_extension = True
    # The paper would be drawn behind the mascot, and the picture it was built from stands
    # beside it. Neither is in the app's pictures.
    bpy.data.objects["MilO_Backdrop"].hide_render = True
    for ob in bpy.data.collections["MilO_Reference"].objects:
        ob.hide_render = True
    dull_the_dials()

    clips.reset_pose()
    clips.neutral()
    rig.animation_data.action = None
    rig.rotation_euler = (0.0, 0.0, 0.0)
    rig.location = (0.0, 0.0, 0.0)
    bpy.context.view_layer.update()
    lens = camera.data
    lens.shift_x = lens.shift_y = 0.0
    ups = [seen(p)[1] for p in mascot_points()]
    lens.lens *= mascot / ((max(ups) - min(ups)) * height)
    slide_view("shift_x", lambda: 0.5 - seen((0, 0, 0))[0])
    return width, height


def dull_the_dials():
    """Draws the black face of both dials without its shine. That face is glossy, and as the
    mascot came round to the viewer it mirrored the key light: the whole big dial was pale grey
    for three frames and black again, which read as a flicker at the moment he looks at you.
    The dials get a copy of the material, so the M and the needles' caps keep theirs. The marks
    on the dials, the chrome rims and the glass are as they were. In every other frame the two
    faces are a shade blacker, and nothing else is changed."""
    glossy = bpy.data.materials["MilO_Black_Gloss"]
    dull = glossy.copy()
    shader = next(node for node in dull.node_tree.nodes if node.type == "BSDF_PRINCIPLED")
    shader.inputs["Specular IOR Level"].default_value = 0.0
    shader.inputs["Coat Weight"].default_value = 0.0
    for name in ("Eye_Speedo_Dial", "Eye_RPM_Dial"):
        for slot in bpy.data.objects[name].material_slots:
            if slot.material == glossy:
                slot.material = dull


def on_ground(colour):
    """Draws this run's pictures on a flat ground of one colour, given as "rrggbb", instead of
    see-through, and with a soft halo round the brake lights where they are lit. Blender's
    renderer draws no such glow by itself. The halo is made from what the frame EMITS and from
    nothing else, so that no highlight on the chrome or the gloss gets one, and it is laid over
    the frame and its ground as light is: added to them. All in memory.

    It has to be asked for (render_clips.py's --ground, render_lights.py). The greeting and the
    website's pictures do not ask, and nothing here is touched for them: they are drawn as they
    always were. For a see-through picture there is halo_see_through. Call it after set_stage,
    which makes the picture see-through again."""
    def lin(c):
        c /= 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    tree, frame, glow, done = halo_nodes("MilO_Halo")
    over = tree.nodes.new("CompositorNodeAlphaOver")
    over.inputs["Background"].default_value = (*(lin(int(colour[i:i + 2], 16)) for i in (0, 2, 4)), 1.0)
    add = tree.nodes.new("ShaderNodeMix")
    add.data_type = "RGBA"
    add.blend_type = "ADD"
    add.inputs["Factor"].default_value = 1.0
    colours = [socket for socket in add.inputs if socket.type == "RGBA"]
    tree.links.new(frame.outputs["Image"], over.inputs["Foreground"])
    tree.links.new(over.outputs["Image"], colours[0])
    tree.links.new(glow, colours[1])
    tree.links.new(next(socket for socket in add.outputs if socket.type == "RGBA"), done.inputs[0])
    scene.compositing_node_group = tree
    scene.render.use_compositing = True
    scene.render.image_settings.color_mode = "RGB"


def halo_nodes(name):
    """The start of both halos: a new set of nodes of that name with the drawn frame, the glow
    of what it emits, and the way out. Returns the set, the frame's node, the glow and the way
    out's node."""
    # The frame's emission is asked for before the nodes are made: only then do they offer it.
    bpy.context.view_layer.use_pass_emit = True
    old = bpy.data.node_groups.get(name)
    if old:
        bpy.data.node_groups.remove(old)
    tree = bpy.data.node_groups.new(name, "CompositorNodeTree")
    tree.interface.new_socket(name="Image", in_out="OUTPUT", socket_type="NodeSocketColor")
    frame = tree.nodes.new("CompositorNodeRLayers")
    glare = tree.nodes.new("CompositorNodeGlare")
    glare.inputs["Type"].default_value = "Bloom"
    glare.inputs["Quality"].default_value = "High"
    glare.inputs["Threshold"].default_value = HALO_FROM
    glare.inputs["Strength"].default_value = HALO_STRENGTH
    glare.inputs["Size"].default_value = HALO_SIZE
    tree.links.new(frame.outputs["Emission"], glare.inputs["Image"])
    return tree, frame, glare.outputs["Glare"], tree.nodes.new("NodeGroupOutput")


def halo_see_through():
    """Draws this run's pictures see-through as ever, and with the halo round lit brake lights
    in them: for a picture that is laid over a screen, as the app lays the greeting over Home.

    A halo is light, added to whatever lies behind it, and a see-through picture cannot hold
    that: it can only hold a colour and how much of it covers what lies behind. So beside the
    mascot the halo is stored as pure red that covers a part: as much as the halo would be
    bright on black. Over the app's dark page that is the halo, all but exactly, and on black it
    is exactly the picture on_ground("000000") draws. Over a light ground it is a red mist that
    darkens what it should light: this is for dark grounds only. Where the mascot is, the halo
    is added to him as light is, and nothing is see-through.

    A phone lays a see-through picture over the screen with the numbers as they are stored,
    which are not amounts of light but their curved form (sRGB): hence the curve below, the
    same one. The halo's faint far part is left out (HALO_ENDS), so that it ends inside the
    picture. Call it after set_stage."""
    tree, frame, glow, done = halo_nodes("MilO_Halo_See_Through")

    def split(colour):
        node = tree.nodes.new("CompositorNodeSeparateColor")
        tree.links.new(colour, node.inputs["Image"])
        return node.outputs

    def sum_of(operation, *values):
        node = tree.nodes.new("ShaderNodeMath")
        node.operation = operation
        for socket, value in zip(node.inputs, values):
            if isinstance(value, (int, float)):
                socket.default_value = value
            else:
                tree.links.new(value, socket)
        return node.outputs[0]

    drawn, lit = split(frame.outputs["Image"]), split(glow)
    # how bright the halo is here, from 0 to 1, as light and then as a phone stores it
    most = sum_of("MINIMUM", sum_of("MAXIMUM", lit["Red"], sum_of("MAXIMUM", lit["Green"], lit["Blue"])), 1.0)
    curved = sum_of("MULTIPLY_ADD", sum_of("POWER", most, 1 / 2.4), 1.055, -0.055)
    straight = sum_of("MULTIPLY", most, 12.92)
    stored = sum_of("MULTIPLY_ADD", sum_of("LESS_THAN", most, 0.0031308), sum_of("SUBTRACT", straight, curved), curved)
    stored = sum_of("DIVIDE", sum_of("MAXIMUM", sum_of("SUBTRACT", stored, HALO_ENDS), 0.0), 1 - HALO_ENDS)
    # beside the mascot the halo covers that part, in its own colour at full strength; where the
    # mascot is, it is added to him
    clear = sum_of("SUBTRACT", 1.0, drawn["Alpha"])
    covered = sum_of("MULTIPLY_ADD", clear, stored, drawn["Alpha"])
    gain = sum_of("MULTIPLY_ADD", clear, sum_of("DIVIDE", stored, sum_of("MAXIMUM", most, 1e-6)), drawn["Alpha"])
    picture = tree.nodes.new("CompositorNodeCombineColor")
    for channel in ("Red", "Green", "Blue"):
        tree.links.new(sum_of("MULTIPLY_ADD", lit[channel], gain, drawn[channel]), picture.inputs[channel])
    tree.links.new(covered, picture.inputs["Alpha"])
    tree.links.new(picture.outputs["Image"], done.inputs[0])
    scene.compositing_node_group = tree
    scene.render.use_compositing = True


def slide_view(shift, off):
    """Moves the part of the camera's view that is kept, across or up, until off() is nothing.
    What a shift of the lens does to the picture is measured, not assumed."""
    lens = camera.data
    setattr(lens, shift, 0.0)
    before = off()
    setattr(lens, shift, 0.1)
    after = off()
    setattr(lens, shift, 0.1 * before / (before - after))


def stand_on_the_line(frames):
    """Moves the camera's view up or down until the lowest sole of the clip that is live is
    FEET_DP above the picture's lower edge. Measured on the walk, because the mascot stands on
    that line for longest: seen from the studio's camera, which looks down on the ground, the
    foot nearer the viewer is lower in the picture, and both are higher once it has turned."""
    def lowest():
        low = 1.0
        for frame in range(frames):
            scene.frame_set(frame)
            low = min(low, min(seen(p)[1] for p in mascot_points()))
        return low

    slide_view("shift_y", lambda: FEET_DP / PICTURE_DP[1] - lowest())


def render_clip(folder, frames):
    """Draws every frame of the clip that is live, and returns where the mascot is in each:
    (left, top, right, bottom) in pixels from the picture's upper left corner."""
    os.makedirs(folder, exist_ok=True)
    boxes = []
    for frame in range(frames):
        scene.frame_set(frame)
        scene.render.filepath = os.path.join(folder, "%04d" % frame)
        bpy.ops.render.render(write_still=True)
        boxes.append(box_of(scene.render.filepath + ".png"))
    return boxes


def alpha_of(path):
    """A frame's see-through channel, top row first."""
    image = bpy.data.images.load(path)
    width, height = image.size
    pixels = numpy.empty(width * height * 4, dtype=numpy.float32)
    image.pixels.foreach_get(pixels)
    bpy.data.images.remove(image)
    return pixels.reshape(height, width, 4)[::-1, :, 3]


def box_of(path):
    alpha = alpha_of(path)
    rows = numpy.nonzero(alpha.max(axis=1) > 0)[0]
    columns = numpy.nonzero(alpha.max(axis=0) > 0)[0]
    return int(columns[0]), int(rows[0]), int(columns[-1]) + 1, int(rows[-1]) + 1


def sole_seen(side, frame):
    """How far across the picture the camera sees the middle of a shoe's sole in a frame of the
    clip that is live, in pixels. The sole and not the foot's bone: what is nearer to the camera
    moves further in the picture, and the sole is what stands on the ground."""
    scene.frame_set(frame)
    posed = bpy.data.objects["Shoe." + side].evaluated_get(bpy.context.evaluated_depsgraph_get())
    points = [posed.matrix_world @ v.co for v in posed.to_mesh().vertices]
    posed.to_mesh_clear()
    ground = min(p.z for p in points)
    sole = [p for p in points if p.z < ground + 0.01]
    return seen(sum(sole, Vector()) / len(sole))[0] * PICTURE_DP[0] * PX_PER_DP


def stride_by_camera():
    """How far the two planted feet move back in the picture in one cycle of the walk, which
    must be live: the nearer foot's part and the farther foot's. Each is measured while its foot
    is flat on the ground, and a foot is planted for half a cycle, at an even pace."""
    half = WALK_FRAMES // 2
    whole_step = half / (FLAT[1] - FLAT[0])
    return tuple(
        (sole_seen(side, start + FLAT[1]) - sole_seen(side, start + FLAT[0])) * whole_step
        for side, start in (("L", 0), ("R", half))
    )


def near_foot_by_frames(folder):
    """The check on that arithmetic, measured in the drawn frames. While the nearer foot is
    flat on the ground and the other one is in the air, the lowest rows of the picture show its
    sole and nothing else, and the middle of the sole can be followed from frame to frame. The
    farther foot cannot be followed this way: the nearer one, in the air, is drawn across it."""
    def sole(frame):
        alpha = alpha_of(os.path.join(folder, "%04d.png" % frame))
        lowest = numpy.nonzero(alpha.max(axis=1) > 0.5)[0][-1]
        columns = numpy.nonzero(alpha[lowest - 6] > 0.5)[0]
        return (columns[0] + columns[-1] + 1) / 2

    return sole(FLAT[1]) - sole(FLAT[0])


# The frames of the walk between which the left foot, the nearer one, is flat on the ground:
# from a quarter to seven tenths of the half cycle it is planted for (walk() in animate_milo.py).
# The right foot does the same half a cycle later.
FLAT = (WALK_FRAMES // 6, WALK_FRAMES // 3)


def main():
    folders = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(folders) != 2:
        raise SystemExit("Name two folders after '--': one for the walk, one for the turn and wave.")
    walk_folder, wave_folder = (os.path.abspath(folder) for folder in folders)
    shown = rig.animation_data.action
    width, height = set_stage()

    walk_frames = write_walk()
    stand_on_the_line(walk_frames)
    near, far = stride_by_camera()
    walk_boxes = render_clip(walk_folder, walk_frames)
    wave_boxes = render_clip(wave_folder, write_turn_and_wave())
    near_flat_drawn = near_foot_by_frames(walk_folder)

    for action in (bpy.data.actions["Greeting_Walk"], bpy.data.actions["Greeting_TurnAndWave"]):
        bpy.data.actions.remove(action)
    rig.animation_data.action = shown

    boxes = walk_boxes + wave_boxes
    left, top = min(b[0] for b in boxes), min(b[1] for b in boxes)
    right, bottom = max(b[2] for b in boxes), max(b[3] for b in boxes)
    standing = wave_boxes[-1]
    print("picture: %d x %d pixels, %g to the dp: %d x %d dp" % (width, height, PX_PER_DP, *PICTURE_DP))
    print("walk: %d frames in %s" % (len(walk_boxes), walk_folder))
    print("turn and wave: %d frames (%d of them the turn) in %s" % (len(wave_boxes), TURN_FRAMES, wave_folder))
    print("the mascot, over every frame: left %d, top %d, right %d, bottom %d" % (left, top, right, bottom))
    print("  walking: left %d, right %d; the picture's middle is %d, so it reaches %d pixels (%.1f dp) ahead"
          % (min(b[0] for b in walk_boxes), max(b[2] for b in walk_boxes), width // 2,
             width // 2 - min(b[0] for b in walk_boxes), (width // 2 - min(b[0] for b in walk_boxes)) / PX_PER_DP))
    print("  soles above the lower edge: %d pixels while walking, %d standing at the end"
          % (height - max(b[3] for b in walk_boxes), height - standing[3]))
    print("  standing at the end: %d pixels high (%.1f dp)" % (standing[3] - standing[1], (standing[3] - standing[1]) / PX_PER_DP))
    print("stride for one cycle: %.1f pixels (%.2f dp): the nearer foot %.1f, the farther foot %.1f"
          % (near + far, (near + far) / PX_PER_DP, near, far))
    print("  the check, the nearer foot from frame %d to frame %d: %.1f pixels in the drawn frames, %.1f by the camera"
          % (*FLAT, near_flat_drawn, near * (FLAT[1] - FLAT[0]) / (WALK_FRAMES // 2)))
    if left <= 0 or top <= 0 or right >= width or bottom >= height:
        raise SystemExit("The mascot touches the picture's edge in some frame: make PICTURE_DP larger.")


if __name__ != "__milo_lib__":
    main()

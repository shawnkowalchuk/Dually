#!/usr/bin/env python3
# preview_wave2.py - lays the wave, walked through, over a picture of Home, as the app would
# play it as a greeting, and writes what there is to show of it. Plain Python, with Pillow and
# ffmpeg:
#
#   python3 design/mascot/scripts/preview_wave2.py <frames> <folder to write into>
#
# <frames> is what render_wave2.py drew. Drawn with 2.625 pixels to the dp, which is what the
# picture of Home has (HOME in preview_moonwalk.py), the films are 1080 by 2400 pixels; drawn
# with fewer, for a quick look, Home is made smaller to match. Shawn is to choose between two
# ways with the brake lights, so everything is written twice, for A and for B:
#
#   wave2-preview-A.mp4          the whole greeting on the whole screen, 24 frames a second, no
#                                sound. A: the lights go out as he lifts his foot for the first
#                                step off. B: they stay lit all the way off the screen
#   wave2-preview-A.gif          the same, the foot of the screen only and smaller, to open
#                                anywhere. A GIF counts in hundredths of a second: it plays 25
#                                frames a second
#   wave2-sheet-A.png            a frame from every quarter second, with its number and its time
#   wave2-sheet-middle-A.png     every frame from just before he stops until he is on his way
#                                again, with its number and the clip it is of
#   wave2-away-lit-on-dark.png   a still of him walking off with the lights lit, on the app's
#   wave2-away-unlit-on-dark.png dark page, and one with them out
#   wave2-away-lit-on-white.png  the same two on white. The lit one is drawn without its halo,
#   wave2-away-unlit-on-white.png  which is for a dark ground only: render_wave2.py --plain
#   wave2-preview.json           what the films are, in numbers: their frames, and the row of
#                                the screen at which its foot, the part the GIFs show, begins
#
# How it is played is how the app plays the greetings it has (MascotGreeting.kt): a walk is one
# cycle drawn on the spot, shown over and over, and every frame stands a part of the stride to
# the right of the one before, so a planted foot stays where it is. He comes in from just beyond
# the left edge; the walk in ends with the last frame of a cycle, because the middle begins with
# the frame that follows it. The middle is played where he stands, in the middle of the screen.
# The walk off begins with the first frame of a cycle and goes on until he is wholly beyond the
# right edge. His soles are SOLES_DP above the bottom bar's tile, and the app's soft shadow lies
# under his feet. He is laid where the arithmetic puts him, to the part of a pixel, as the app
# lays him. The film ends TAIL frames of Home alone later.
#
# It also says how well his feet keep their place in the film, from where render_wave2.py found
# each shoe's heel and toe on the floor in every frame: how far such an edge moves from one
# frame of the film to the next while it stays on the floor, at most, over the whole film and
# at the two seams; and how like the frames on each side of a seam are.
#
# THESE ARE THE PREVIEWS Shawn was shown on 2026-10-11, before the wave was built into the app.
# He chose A, and the app plays what film A showed (MascotWalk.kt).

import json
import math
import os
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

from preview_moonwalk import BAR_TOP, GIF_WIDTH, HOME, HOME_TO_THE_DP, SHADOW_DP, SOLES_DP, lay, shadow

# The app's dark page (MiloColors), and white.
PAGE = (0x12, 0x13, 0x16)
WHITE = (255, 255, 255)
TAIL = 4  # frames of Home alone at the film's end
# The sheet of every frame of the middle: how many frames of the walk in before it and of the
# walk off after it are on it too.
AROUND = (4, 8)
DENSE = 0.5  # and how large its frames are, as a part of the film's
# The frame of the walk off's cycle the stills are of: his right foot, the nearer one, is flat
# under him, the other is coming forward, and both hands are in view.
STILL = 8


def frames_in(folder):
    """The frames in a folder, by their numbers."""
    return {int(name[:-4]): Image.open(os.path.join(folder, name)).convert("RGBA")
            for name in sorted(os.listdir(folder)) if name.endswith(".png")}


def reach(frames, axis):
    """How far the mascot, and any halo, reaches to the left and to the right of his axis in
    these frames."""
    left = right = 0
    for frame in frames:
        box = frame.getchannel("A").getbbox()
        left, right = max(left, axis - box[0]), max(right, box[2] - axis)
    return left, right


def lay_at(screen, picture, left, top):
    """Lays a see-through picture on the screen with its left edge at `left`, which need not be
    a whole pixel. The app moves him by parts of a pixel, and so does this: laid on whole
    pixels, a planted foot would shiver by half a pixel from frame to frame."""
    whole = math.floor(left)
    if left - whole > 1e-3:
        # shifted with its colours weighted by how much they cover, or an edge would get a fringe
        picture = picture.convert("RGBa").transform(picture.size, Image.AFFINE, (1, 0, whole - left, 0, 1, 0), Image.BILINEAR).convert("RGBA")
    lay(screen, picture, whole, top)


def letters(size):
    try:
        return ImageFont.load_default(size)
    except TypeError:  # an older Pillow has one size only
        return ImageFont.load_default()


def differ(a, b):
    """How far two frames are from each other: of 255, a pixel on average, and at most."""
    most = None
    for band in ImageChops.difference(a, b).split():
        most = band if most is None else ImageChops.lighter(most, band)
    counts = most.histogram()
    return sum(value * count for value, count in enumerate(counts)) / (a.width * a.height), max(v for v, c in enumerate(counts) if c)


def on(ground, picture):
    made = Image.new("RGBA", picture.size, ground + (255,))
    made.alpha_composite(picture)
    return made.convert("RGB")


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Name the folder render_wave2.py drew into, and a folder to write into.")
    drawn, out = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(drawn, "wave2.json")) as f:
        told = json.load(f)
    if told["middle_every"] != 1:
        raise SystemExit("The middle was drawn with --every: draw it whole first.")
    to_the_dp, axis, rate, cycle = told["pixels_to_the_dp"], told["axis"], told["frames_a_second"], told["cycle_frames"]
    a_frame = told["stride"] / cycle
    home = Image.open(HOME).convert("RGBA")
    small = to_the_dp / HOME_TO_THE_DP
    if small != 1.0:  # sides of even numbers of pixels, which the film needs
        home = home.resize((2 * round(home.width * small / 2), 2 * round(home.height * small / 2)), Image.LANCZOS)

    def dp(pixels):
        return pixels / to_the_dp

    walk_in, middle, walk_off = (frames_in(os.path.join(drawn, part)) for part in ("in", "middle", "out"))
    lit_middle = {**middle, **frames_in(os.path.join(drawn, "middle-lit"))}
    lit_off = frames_in(os.path.join(drawn, "out-lit"))
    if sorted(frames_in(os.path.join(drawn, "middle-lit"))) != told["lit_instead"] or len(middle) != told["middle_frames"]:
        raise SystemExit("The folder does not hold the frames wave2.json names.")

    stop = home.width / 2
    # The walk in's soles are the lowest: the foot nearer the viewer is lower in the picture.
    soles_row = BAR_TOP * small - SOLES_DP * to_the_dp
    top = round(soles_row - (told["height"] - told["soles"]["in"]))
    patch = shadow(to_the_dp)
    shadow_top = round(soles_row - SHADOW_DP[2] * to_the_dp - patch.height / 2)
    crop_top = max(0, top - round(12 * to_the_dp))
    names = [clip["name"] for clip in told["clips"] for _ in range(clip["frames"])]
    ahead = reach(walk_in.values(), axis)[1]
    coming = math.ceil((stop + ahead) / a_frame)

    def course(going_off, behind):
        """Every frame of the greeting: the picture shown, where his axis is across the screen,
        which part it is of and which frame of that part."""
        shots = [(walk_in[-k % cycle], stop - a_frame * k, "in", -k % cycle) for k in range(coming, 0, -1)]
        shots += [(picture, stop, "middle", number) for number, picture in sorted(going_off[0].items())]
        k = 0
        while stop + a_frame * k - behind < home.width:
            shots.append((going_off[1][k % cycle], stop + a_frame * k, "out", k % cycle))
            k += 1
        return shots

    def screen(shot):
        picture, x = shot[:2]
        made = home.copy()
        if picture is not None:
            lay_at(made, patch, x - patch.width / 2, shadow_top)
            lay_at(made, picture, x - axis, top)
        return made.convert("RGB")

    def foot(image):
        return image.crop((0, crop_top, home.width, home.height))

    # The sheet of every frame of the middle shows the part of the screen he is in from the
    # first frame on it to the last: what is solid of him in any of the frames, and a little more.
    solid = [frame.getchannel("A").point(lambda a: 255 if a >= 250 else 0).getbbox()
             for frames in (walk_in, lit_middle, lit_off) for frame in frames.values()]
    edge = round(8 * to_the_dp)
    part_of_screen = (max(0, round(stop - axis - a_frame * AROUND[0]) + min(b[0] for b in solid) - edge),
                      max(0, top + min(b[1] for b in solid) - 2 * edge),
                      min(home.width, round(stop - axis + a_frame * AROUND[1]) + max(b[2] for b in solid) + edge),
                      min(home.height, top + max(b[3] for b in solid) + edge))

    written = []
    said = dict(pixels_to_the_dp=to_the_dp, screen=list(home.size), foot_of_screen=crop_top, frames_a_second=rate, tail_frames=TAIL, films={})
    for name, going_off in (("A", (middle, walk_off)), ("B", (lit_middle, lit_off))):
        shots = course(going_off, reach(going_off[1].values(), axis)[0])
        greeting = len(shots)
        shots += [(None, 0.0, "", 0)] * TAIL
        film = os.path.join(out, "wave2-preview-%s.mp4" % name)
        encoder = subprocess.Popen(
            ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % home.size, "-r", str(rate),
             "-i", "-", "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "17", "-preset", "slow", "-movflags", "+faststart", film],
            stdin=subprocess.PIPE)
        tiles, dense = [], []
        for number, shot in enumerate(shots):
            made = screen(shot)
            encoder.stdin.write(made.tobytes())
            if number % (rate // 4) == 0:
                tiles.append(("%d   %.2f s" % (number, number / rate), foot(made).resize(
                    (round(270 * small), round(270 * small * (home.height - crop_top) / home.width)), Image.LANCZOS)))
            if coming - AROUND[0] <= number < coming + len(middle) + AROUND[1]:
                if shot[2] == "middle":
                    part = names[shot[3]]
                    label = "%d  %s %d" % (number, part, shot[3] - names.index(part) + (part == "TurnAway"))
                else:
                    label = "%d  %s %d" % (number, {"in": "WalkIn", "out": "WalkOff"}[shot[2]], shot[3])
                strip = made.crop(part_of_screen)
                dense.append((label, strip.resize((round(strip.width * DENSE), round(strip.height * DENSE)), Image.LANCZOS)))
        encoder.stdin.close()
        if encoder.wait() != 0:
            raise SystemExit("ffmpeg could not write " + film)

        for ending, shown, columns, size in (("", tiles, 6, 15), ("-middle", dense, 8, 13)):
            width, height = shown[0][1].size
            sheet = Image.new("RGB", (columns * width, math.ceil(len(shown) / columns) * height), (0, 0, 0))
            pen = ImageDraw.Draw(sheet)
            for i, (label, tile) in enumerate(shown):
                x, y = (i % columns) * width, (i // columns) * height
                sheet.paste(tile, (x, y))
                pen.rectangle((x, y, x + width - 1, y + height - 1), outline=(70, 70, 70))
                pen.rectangle((x + 1, y + 1, x + 12 + pen.textlength(label, font=letters(size)), y + size + 9), fill=(0, 0, 0))
                pen.text((x + 6, y + 4), label, fill=(255, 255, 255), font=letters(size))
            sheet.save(os.path.join(out, "wave2-sheet%s-%s.png" % (ending, name)))

        look = "crop=%d:%d:0:%d,scale=%d:-2:flags=lanczos" % (home.width, home.height - crop_top, crop_top, min(GIF_WIDTH, home.width))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", film, "-filter_complex",
                        "[0:v]%s,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4,setpts=N/25/TB" % look,
                        "-r", "25", os.path.join(out, "wave2-preview-%s.gif" % name)], check=True)
        written += ["wave2-preview-%s.mp4" % name, "wave2-preview-%s.gif" % name, "wave2-sheet-%s.png" % name, "wave2-sheet-middle-%s.png" % name]

        # How well his feet keep their place in the film: every edge of a shoe that is on the
        # floor in two frames running, and how far it has moved between them on the screen.
        steps, seams, strays = [], {"in": [], "out": []}, []
        held = {}  # for each edge, where it first stood in its time on the floor
        for number in range(1, greeting):
            (_, x0, part0, frame0), (_, x1, part1, frame1) = shots[number - 1][:4], shots[number][:4]
            for edge, now in told["stood"][part1][frame1].items():
                before = told["stood"][part0][frame0][edge]
                if not (before and now):
                    held.pop(edge, None)
                    continue
                was, is_ = (x0 + before[0], before[1]), (x1 + now[0], now[1])
                held.setdefault(edge, was)
                moved = (abs(is_[0] - was[0]), abs(is_[1] - was[1]))
                steps.append(moved)
                strays.append((abs(is_[0] - held[edge][0]), abs(is_[1] - held[edge][1])))
                if number in (coming, coming + 1):
                    seams["in"].append(moved)
                if number in (coming + len(middle), coming + len(middle) + 1):
                    seams["out"].append(moved)
        step, stray, seam_in, seam_out = ((max(m[0] for m in moves), max(m[1] for m in moves)) for moves in (steps, strays, seams["in"], seams["out"]))

        seconds = greeting / rate
        said["films"][name] = dict(frames=greeting, walk_in=coming, middle=len(middle), walk_off=greeting - coming - len(middle))
        print("%s: the greeting is %d frames, %.2f seconds, on a screen %.0f dp wide; the film has %d frames of Home alone after it"
              % (name, greeting, seconds, dp(home.width), TAIL))
        print("  in: %d frames of the walk, %.1f cycles; he starts with his axis %.0f pixels beyond the left edge"
              % (coming, coming / cycle, a_frame * coming - stop))
        print("  the middle: %d frames, from frame %d; the brake lights come on in frame %d and %s"
              % (len(middle), coming, coming + told["lights_on"],
                 "begin to go out in frame %d" % (coming + told["step_off"] + 1) if name == "A" else "stay on"))
        print("  out: %d frames, from frame %d" % (greeting - coming - len(middle), coming + len(middle)))
        print("  his feet: an edge of a shoe that is on the floor moves at most %.2f pixels (%.2f dp) across and %.2f (%.2f dp) up or down "
              "from one frame to the next, and strays at most %.2f pixels (%.2f dp) across and %.2f (%.2f dp) up or down from where it was "
              "put down" % (step[0], dp(step[0]), step[1], dp(step[1]), stray[0], dp(stray[0]), stray[1], dp(stray[1])))
        for seam, what in ((seam_in, "from the walk in into the middle"), (seam_out, "from the middle into the walk off")):
            print("    at the seam %s: at most %.2f pixels (%.2f dp) across, %.2f (%.2f dp) up or down"
                  % (what, seam[0], dp(seam[0]), seam[1], dp(seam[1])))

    # The seams, in the frames themselves. The middle's first frame is the walk in's first, drawn
    # again; and the step from the middle's last frame to the walk off's first is held against
    # the steps before it.
    same = differ(on(PAGE, middle[0]), on(PAGE, walk_in[0]))
    last = len(middle) - 1
    print("the seams, in the drawn frames, over the dark page, of 255:")
    print("  the middle's first frame against the walk in's first, which it is: %.3f a pixel on average, %d at most" % same)
    print("  the middle's last frame against the walk off's first: %.2f on average; the two steps before it: %.2f and %.2f"
          % (differ(on(PAGE, middle[last]), on(PAGE, walk_off[0]))[0], differ(on(PAGE, middle[last - 2]), on(PAGE, middle[last - 1]))[0],
             differ(on(PAGE, middle[last - 1]), on(PAGE, middle[last]))[0]))
    print("  the same with the lights left lit: %.2f; %.2f and %.2f"
          % (differ(on(PAGE, lit_middle[last]), on(PAGE, lit_off[0]))[0], differ(on(PAGE, lit_middle[last - 2]), on(PAGE, lit_middle[last - 1]))[0],
             differ(on(PAGE, lit_middle[last - 1]), on(PAGE, lit_middle[last]))[0]))

    # The stills: him walking off, as large as he was drawn, his shadow under him.
    plain = os.path.join(drawn, "out-lit-plain", "%04d.png" % STILL)
    stills = [("lit-on-dark", lit_off[STILL], PAGE), ("unlit-on-dark", walk_off[STILL], PAGE), ("unlit-on-white", walk_off[STILL], WHITE)]
    if os.path.isfile(plain):
        stills.append(("lit-on-white", Image.open(plain).convert("RGBA"), WHITE))
    for what, picture, ground in stills:
        made = Image.new("RGBA", picture.size, ground + (255,))
        lay_at(made, patch, axis - patch.width / 2, round(told["height"] - told["soles"]["in"] - SHADOW_DP[2] * to_the_dp - patch.height / 2))
        made.alpha_composite(picture)
        made.convert("RGB").save(os.path.join(out, "wave2-away-%s.png" % what))
        written.append("wave2-away-%s.png" % what)

    with open(os.path.join(out, "wave2-preview.json"), "w") as f:
        json.dump(said, f, indent=1)
    print("a cycle of the walk carries him %.1f pixels (%.1f dp); a frame, %.2f pixels (%.2f dp). Walking in he reaches %d pixels "
          "(%.1f dp) ahead of his axis" % (told["stride"], dp(told["stride"]), a_frame, dp(a_frame), ahead, dp(ahead)))
    for name in written:
        print("  %s: %d bytes" % (name, os.path.getsize(os.path.join(out, name))))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# preview_moonwalk.py - lays the moonwalk over a picture of Home, as the app would play it as a
# greeting, and writes what there is to show of it. Plain Python, with Pillow and ffmpeg:
#
#   python3 design/mascot/scripts/preview_moonwalk.py <frames> <folder to write into>
#
# <frames> is what render_moonwalk.py drew. Drawn with 2.625 pixels to the dp, which is what
# the picture of Home has (HOME below), the film is 1080 pixels wide; drawn with fewer, for a
# quick look, Home is made smaller to match. It writes:
#
#   moonwalk.mp4            the whole greeting on the whole screen, 24 frames a second
#   moonwalk.gif            the same, the foot of the screen only and smaller, to open anywhere.
#                           A GIF counts in hundredths of a second: it plays 25 frames a second
#   moonwalk-sheet.png      a frame from every quarter second, with its time
#   moonwalk-<what>.png     large stills: the glide, the brake lights, the pose, the turn
#
# How it is played is how the app plays the greeting it has (MascotWalk.kt): the glide is one
# cycle drawn on the spot, shown over and over, and every frame stands a part of the stride to
# the left of the one before, so the foot that is up on its toe stays where it is. He comes in
# from just beyond the right edge, does the middle part in the middle of the screen, and glides
# on until he is wholly beyond the left edge. The glide in ends with the last frame of a cycle,
# because the middle begins with the frame that follows it; so it begins partway into one, and
# no time is spent before he is seen. (The greeting the app has walks in whole cycles, and up to
# half a second of it is not seen.) His soles are
# SOLES_DP above the bottom bar's tile, and the app's soft shadow lies under his feet.
#
# THE APP DOES NOT PLAY THIS. It is a preview to decide by.

import json
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
# The picture of Home, how many pixels it has to the dp, and the row in it where the bottom
# bar's tile begins.
HOME = os.path.normpath(os.path.join(HERE, "..", "..", "..", "marketing", "screenshots", "home.png"))
HOME_TO_THE_DP = 2.625
BAR_TOP = 2169
# As MascotGreeting.kt has them: the soles above the bar's tile; the shadow's width and height,
# how far its middle is above the soles, and how dark it is in the middle.
SOLES_DP = 4.0
SHADOW_DP = (92.0, 14.0, 3.4, 0.5)
GIF_WIDTH = 400


def frames_in(folder):
    names = sorted(name for name in os.listdir(folder) if name.endswith(".png"))
    return [Image.open(os.path.join(folder, name)).convert("RGBA") for name in names]


def reach(frames, axis):
    """How far the mascot reaches to the left and to the right of his axis in these frames."""
    left = right = 0
    for frame in frames:
        box = frame.getchannel("A").getbbox()
        left, right = max(left, axis - box[0]), max(right, box[2] - axis)
    return left, right


def shadow(to_the_dp):
    wide, high, _, darkest = SHADOW_DP
    size = (round(wide * to_the_dp), round(high * to_the_dp))
    alpha = Image.new("L", size)
    for y in range(size[1]):
        for x in range(size[0]):
            away = math.hypot((x + 0.5) / size[0] * 2 - 1, (y + 0.5) / size[1] * 2 - 1)
            alpha.putpixel((x, y), round(255 * darkest * max(0.0, 1 - away)))
    patch = Image.new("RGBA", size, (0, 0, 0, 255))
    patch.putalpha(alpha)
    return patch


def lay(screen, picture, left, top):
    """Lays a see-through picture on the screen, also where it is partly beyond an edge."""
    box = (max(0, -left), max(0, -top), min(picture.width, screen.width - left), min(picture.height, screen.height - top))
    if box[0] < box[2] and box[1] < box[3]:
        screen.alpha_composite(picture.crop(box), (left + box[0], top + box[1]))


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Name the folder render_moonwalk.py drew into, and a folder to write into.")
    drawn, out = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(drawn, "moonwalk.json")) as f:
        told = json.load(f)
    if told["middle_every"] != 1:
        raise SystemExit("The middle was drawn with --every: draw it whole first.")
    to_the_dp, axis, stride = told["pixels_to_the_dp"], told["axis"], told["stride"]
    home = Image.open(HOME).convert("RGBA")
    small = to_the_dp / HOME_TO_THE_DP
    if small != 1.0:  # sides of even numbers of pixels, which the film needs
        home = home.resize((2 * round(home.width * small / 2), 2 * round(home.height * small / 2)), Image.LANCZOS)
    glide, middle = frames_in(os.path.join(drawn, "glide")), frames_in(os.path.join(drawn, "middle"))
    a_frame = stride / len(glide)
    left_reach, right_reach = reach(glide, axis)

    # where his axis is in every frame of the greeting, and which picture is shown there
    stop = home.width / 2
    coming = math.ceil((home.width - stop + left_reach) / a_frame)
    shots = [(glide[-k % len(glide)], stop + a_frame * k) for k in range(coming, 0, -1)]
    marks = {"glide": len(shots) // 2}
    at = len(shots)
    for clip in told["clips"]:
        marks[clip["name"]] = at
        at += clip["frames"]
    shots += [(picture, stop) for picture in middle]
    k = 0
    while stop - a_frame * k + right_reach > 0:
        shots.append((glide[k % len(glide)], stop - a_frame * k))
        k += 1

    soles_row = BAR_TOP * small - SOLES_DP * to_the_dp
    top = round(soles_row - (told["height"] - told["soles"]))
    patch = shadow(to_the_dp)
    shadow_top = round(soles_row - SHADOW_DP[2] * to_the_dp - patch.height / 2)
    crop_top = max(0, top - round(12 * to_the_dp))

    def screen(shot):
        picture, x = shot
        made = home.copy()
        lay(made, patch, round(x - patch.width / 2), shadow_top)
        lay(made, picture, round(x - axis), top)
        return made.convert("RGB")

    film = os.path.join(out, "moonwalk.mp4")
    rate = str(told["frames_a_second"])
    encoder = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % home.size, "-r", rate,
         "-i", "-", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "17", "-preset", "slow", "-movflags", "+faststart", film],
        stdin=subprocess.PIPE)
    quarter = told["frames_a_second"] // 4
    tiles = []
    stills = {"glide": marks["glide"], "brake-lights": marks["MoonwalkStop"] + 8, "pose": marks["MoonwalkStop"] + 21,
              "turn": marks["KickSpin"] + 13}
    for number, shot in enumerate(shots):
        made = screen(shot)
        encoder.stdin.write(made.tobytes())
        if number % quarter == 0:
            tiles.append((number, made.crop((0, crop_top, home.width, home.height)).resize(
                (round(270 * small), round(270 * small * (home.height - crop_top) / home.width)), Image.LANCZOS)))
        for what, wanted in stills.items():
            if number == wanted:
                made.crop((0, crop_top, home.width, home.height)).save(os.path.join(out, "moonwalk-%s.png" % what))
    encoder.stdin.close()
    if encoder.wait() != 0:
        raise SystemExit("ffmpeg could not write " + film)

    columns = 6
    width, height = tiles[0][1].size
    sheet = Image.new("RGB", (columns * width, math.ceil(len(tiles) / columns) * height), (0, 0, 0))
    pen = ImageDraw.Draw(sheet)
    for i, (number, tile) in enumerate(tiles):
        x, y = (i % columns) * width, (i // columns) * height
        sheet.paste(tile, (x, y))
        pen.rectangle((x, y, x + width - 1, y + height - 1), outline=(70, 70, 70))
        pen.text((x + 6, y + 5), "%.2f s" % (number / told["frames_a_second"]), fill=(255, 255, 255))
    sheet.save(os.path.join(out, "moonwalk-sheet.png"))

    look = "crop=%d:%d:0:%d,scale=%d:-2:flags=lanczos" % (home.width, home.height - crop_top, crop_top, min(GIF_WIDTH, home.width))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", film, "-filter_complex",
                    "[0:v]%s,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4,setpts=N/25/TB" % look,
                    "-r", "25", os.path.join(out, "moonwalk.gif")], check=True)

    seconds = len(shots) / told["frames_a_second"]
    print("the greeting: %d frames, %.2f seconds" % (len(shots), seconds))
    print("  in: %d frames of the glide, %.1f cycles; he starts with his axis %.0f pixels beyond the right edge"
          % (coming, coming / len(glide), stop + a_frame * coming - home.width))
    print("  the middle: %d frames, from frame %d" % (len(middle), marks["MoonwalkStop"]))
    print("  out: %d frames" % k)
    print("  a cycle of the glide carries him %.1f pixels (%.1f dp); a frame, %.2f pixels" % (stride, stride / to_the_dp, a_frame))
    print("  gliding he reaches %d pixels to the left of his axis and %d to the right" % (left_reach, right_reach))
    for name in ("moonwalk.mp4", "moonwalk.gif", "moonwalk-sheet.png", *("moonwalk-%s.png" % what for what in stills)):
        print("  %s: %d bytes" % (name, os.path.getsize(os.path.join(out, name))))


if __name__ == "__main__":
    main()

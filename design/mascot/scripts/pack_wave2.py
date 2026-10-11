#!/usr/bin/env python3
# pack_wave2.py - makes the two pictures the app's first greeting shows, the wave, out of the
# frames render_wave2.py drew. Run it with plain Python, not inside Blender, and name the folder
# that script drew into:
#
#   python3 design/mascot/scripts/pack_wave2.py <frames>
#
# The frames must have been drawn with 2.5 pixels to the dp, which is what render_wave2.py draws
# when it is told nothing else, and what the greeting's other pictures have. It writes two files
# into app/src/main/res/drawable-nodpi/, both WebP with a see-through background, and both are
# committed: the app's build runs none of this.
#
#   mascot_walk_in_and_off.webp  a still picture: one cycle of the walk in and then one cycle of
#                                the walk off, their frames side by side on one sheet, COLUMNS
#                                across, row after row. The app draws the frame it wants,
#                                because it moves the mascot with every frame, in and off.
#   mascot_stop_and_wave.webp    an animated picture that plays once and keeps its last frame:
#                                the stop with the brake lights coming on, the turn to the
#                                viewer, the wave, the turn away with the lights lit, and the
#                                foot lifted for the first step off as they go out. Android
#                                plays it.
#
# What it prints at the end is what the app's code is told: MascotPicture in
# core/designsystem/component/MascotWalk.kt. MascotPicturesTest holds both files to it.
#
# THE BRAKE LIGHTS ARE A's. render_wave2.py draws them two ways, for the two films Shawn was
# shown on 2026-10-11, and he chose A: they go out as he lifts his foot for the first step off.
# This packs middle/ and out/, which are A, and never reads middle-lit/ and out-lit/, which
# were B.
#
# BOTH WALKS ARE ON ONE SHEET, where the trial of the preview had a sheet for each. He is seen
# from another side as he leaves, so the app needs both cycles; one sheet of 24 frames is one
# picture for the app to read, hold and step through, as every greeting has one, and it weighs
# what two sheets of 12 weigh. It takes twice a sheet's memory for as long as the greeting is on
# the screen, which two sheets held together would take too.
#
# All else is pack_moonwalk.py's, and done by its functions, at its settings: colour at 50 of
# 100, the frames cut down to what is drawn in any of them, a frame with a glow in it stored
# with its see-through channel almost whole, a frame that is the frame before not stored, a
# frame that barely changes stored as a patch, and the packed animated picture played back and
# held against the frames that were drawn.
#
# To weigh the greeting at other settings without touching the app, name a folder to write into
# and the colours, of 100, to pack at; each gets a folder of its own inside it:
#
#   python3 design/mascot/scripts/pack_wave2.py <frames> --trial <folder> 60 50 45 40
#
# It needs Pillow ("pip3 install pillow"), and cwebp and webpmux (Homebrew: "brew install webp").

import json
import os
import shutil
import sys
import tempfile

from PIL import Image, ImageChops

import pack_moonwalk as packing
from pack_greeting import APP_PICTURES

# The sheet: this many frames in a row. MascotPicture.SHEET_COLUMNS in the app says the same.
# Four across and six down is 2,160 by 2,640 pixels, under the 4,096 a side that every phone's
# graphics chip takes as one picture.
COLUMNS = 4
SHEET, FILM = "mascot_walk_in_and_off.webp", "mascot_stop_and_wave.webp"
APP = os.path.normpath(os.path.join(APP_PICTURES, "..", "..", "..", ".."))


def pack_sheet(frames, out, packed):
    """Both walks: one still picture with every frame on it, COLUMNS across, row after row. The
    frames touch; each has a see-through margin of its own, so none shows in its neighbour. Its
    see-through channel is stored as an edge's is, or almost whole if a frame has a glow in it."""
    rows, over = divmod(len(frames), COLUMNS)
    if over:
        raise SystemExit("%d frames do not fill rows of %d" % (len(frames), COLUMNS))
    width, height = frames[0].size
    sheet = Image.new("RGBA", (COLUMNS * width, rows * height), (0, 0, 0, 0))
    for number, frame in enumerate(frames):
        sheet.paste(frame, ((number % COLUMNS) * width, (number // COLUMNS) * height))
    least = min(packing.soft_pixels(frame) for frame in frames)
    glow = any(packing.soft_pixels(frame) > packing.GLOW_FROM * least for frame in frames)
    packing.webp(sheet, packing.GLOW if glow else packing.EDGES, os.path.join(packed, "sheet.webp"))
    shutil.move(os.path.join(packed, "sheet.webp"), out)
    return "%d frames on one sheet of %d x %d pixels, %d across and %d down%s" % (
        len(frames), *sheet.size, COLUMNS, rows, ", with a glow" if glow else "")


def check_sheet(frames, out):
    """Reads the packed sheet back and holds every frame of it against the one that was drawn,
    both over the app's dark page. Returns how far the worst frame is: of 255, a pixel on
    average."""
    sheet = Image.open(out).convert("RGBA")
    width, height = frames[0].size
    if sheet.size != (COLUMNS * width, len(frames) // COLUMNS * height):
        raise SystemExit("%s is not %d frames of %d x %d pixels" % (out, len(frames), width, height))
    worst = 0.0
    for number, frame in enumerate(frames):
        left, top = (number % COLUMNS) * width, (number // COLUMNS) * height
        seen = sheet.crop((left, top, left + width, top + height))
        off = ImageChops.difference(packing.on_the_page(seen), packing.on_the_page(frame)).histogram()
        worst = max(worst, sum(value % 256 * count for value, count in enumerate(off)) / (3 * width * height))
    if worst > 2.0:
        raise SystemExit("a frame of %s is %.2f of 255 from the frame that was drawn, a pixel on average" % (out, worst))
    return worst


def pack(walks, middle, sheet, film):
    """Packs both pictures and plays them back. Says what each holds and weighs."""
    with tempfile.TemporaryDirectory() as packed:
        print("  %s: %s, %d bytes" % (os.path.basename(sheet), pack_sheet(walks, sheet, packed), os.path.getsize(sheet)))
        said, stored = packing.pack_middle(middle, film, packed)
        print("  %s: %s, %d bytes" % (os.path.basename(film), said, os.path.getsize(film)))
    print("  read back, every frame of the sheet is within %.2f of 255 of the frame that was drawn, a pixel on average, "
          "and every frame of the animated picture within %.2f" % (check_sheet(walks, sheet), packing.check(middle, film, stored)))
    print("  together: %d bytes" % (os.path.getsize(sheet) + os.path.getsize(film)))


def main():
    trial = "--trial" in sys.argv[2:3]
    if len(sys.argv) < 2 or (len(sys.argv) != 2 and not trial) or (trial and (len(sys.argv) < 5 or not all(c.isdigit() for c in sys.argv[4:]))):
        raise SystemExit("Name the folder render_wave2.py drew into. For a trial, add: --trial <folder to write into> <colour> ...")
    for tool in ("cwebp", "webpmux"):
        if shutil.which(tool) is None:
            raise SystemExit("%s is not installed. On a Mac: brew install webp" % tool)
    drawn = os.path.abspath(sys.argv[1])
    with open(os.path.join(drawn, "wave2.json")) as f:
        told = json.load(f)
    to_the_dp = packing.TO_THE_DP
    if told["pixels_to_the_dp"] != to_the_dp or told["middle_every"] != 1:
        raise SystemExit("Draw the frames again with %g pixels to the dp and without --every: the app's pictures have that." % to_the_dp)
    if round(1000 / told["frames_a_second"]) != packing.FRAME_MS:
        raise SystemExit("The frames were drawn for %d a second, and a frame is shown for %d ms." % (told["frames_a_second"], packing.FRAME_MS))
    walk_in, middle, walk_off = (packing.frames_of(os.path.join(drawn, part)) for part in ("in", "middle", "out"))
    cycle = told["cycle_frames"]
    if len(middle) != told["middle_frames"] or len(walk_in) != cycle or len(walk_off) != cycle:
        raise SystemExit("The folder does not hold the frames wave2.json names.")

    box = packing.cut_box(walk_in + middle + walk_off, walk_in[0].size)
    walk_in, middle, walk_off = ([frame.crop(box) for frame in frames] for frames in (walk_in, middle, walk_off))
    width, height = middle[0].size
    axis = told["axis"] - box[0]
    ahead, behind = packing.drawn_box(walk_in)[2] - axis, axis - packing.drawn_box(walk_off)[0]
    soles_in, soles_off = (told["soles"][part] - (told["height"] - box[3]) for part in ("in", "out"))
    walks = walk_in + walk_off

    if trial:
        out = os.path.abspath(sys.argv[3])
        if os.path.commonpath([out, APP]) == APP:
            raise SystemExit("A trial does not write into the app.")
        for colour in (int(c) for c in sys.argv[4:]):
            packing.COLOUR = ["-q", str(colour), "-sharp_yuv", "-af", "-m", "6"]
            folder = os.path.join(out, "colour-%d" % colour)
            os.makedirs(folder, exist_ok=True)
            print("a trial, colour at %d of 100, in %s:" % (colour, folder))
            pack(walks, middle, os.path.join(folder, SHEET), os.path.join(folder, FILM))
    else:
        os.makedirs(APP_PICTURES, exist_ok=True)
        print("the app's two pictures, in %s:" % APP_PICTURES)
        pack(walks, middle, os.path.join(APP_PICTURES, SHEET), os.path.join(APP_PICTURES, FILM))

    def dp(pixels):
        return pixels / to_the_dp

    print("MascotPicture is told:")
    print("  a frame is %d x %d pixels, %g to the dp: %g x %g dp" % (width, height, to_the_dp, dp(width), dp(height)))
    print("  the sheet holds %d frames, %d across: the walk in's %d, then the walk off's %d. It takes %.1f MB of the phone's graphics memory"
          % (len(walks), COLUMNS, cycle, cycle, COLUMNS * width * (len(walks) // COLUMNS) * height * 4 / 1e6))
    print("  his axis is %d pixels (%.1f dp) from the frame's left edge" % (axis, dp(axis)))
    print("  walking in, his soles are %.1f pixels (%.1f dp) above the frame's lower edge; walking off, the lowest of him is %.1f (%.1f dp)"
          % (soles_in, dp(soles_in), soles_off, dp(soles_off)))
    print("  walking in he reaches %d pixels (%.1f dp) ahead of his axis, to its right; walking off he reaches %d (%.1f dp) behind it, to its left"
          % (ahead, dp(ahead), behind, dp(behind)))
    print("  one cycle of either walk, %d frames, carries him %.2f pixels (%.2f dp)" % (cycle, told["stride"], dp(told["stride"])))
    print("  the middle part is %d frames, %d ms" % (len(middle), len(middle) * packing.FRAME_MS))


if __name__ == "__main__":
    main()

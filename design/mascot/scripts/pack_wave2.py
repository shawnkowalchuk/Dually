#!/usr/bin/env python3
# pack_wave2.py - packs the frames of the wave, walked through, as the app's pictures of the
# mascot are packed, to say what that greeting would weigh in the app. Run it with plain
# Python, not inside Blender, and name the folder render_wave2.py drew into and a folder to
# write into:
#
#   python3 design/mascot/scripts/pack_wave2.py <frames> <folder to write into> [<colour> ...]
#
# A TRIAL. IT WRITES NOTHING INTO THE APP: the folder it is given must not be the app's.
# The frames must have been drawn with 2.5 pixels to the dp, which is what render_wave2.py
# draws when it is told nothing else, and what the app's pictures have.
#
# The greeting would be three pictures where the two the app has are two each, because he is
# seen from another side as he leaves:
#
#   mascot_walk_in.webp        a still picture: one cycle of the walk in, its frames side by
#                              side on one sheet, COLUMNS across, row after row
#   mascot_wave_and_turn.webp  an animated picture that plays once and keeps its last frame:
#                              the stop, the turn to the viewer, the wave, the turn away and
#                              the foot lifted for the first step
#   mascot_walk_off.webp       a still picture: one cycle of the walk off, as the first sheet
#
# It packs them four times, each into a folder of its own inside the one it is given: for each
# of the two ways with the brake lights Shawn is to choose between (A, they go out as he steps
# off; B, they stay lit all the way off), once with colour at 60 of 100, as pack_greeting.py
# packs the wave the app has, and once at 50, as pack_moonwalk.py packs the moonwalk; or at the
# colours it is given after the two folders, to see what a coarser picture would weigh. All else
# is pack_moonwalk.py's, and done by its functions: the frames are cut down to what is drawn in
# any of them, a frame with a glow in it keeps its see-through channel almost whole, a frame
# that is the frame before is not stored, a frame that barely changes is stored as a patch, and
# the packed animated picture is played back and held against the frames that were drawn.
#
# What it prints is what the app's code would be told, and what each picture weighs.
#
# It needs Pillow ("pip3 install pillow"), and cwebp and webpmux (Homebrew: "brew install webp").

import json
import os
import shutil
import sys
import tempfile

from PIL import Image

import pack_moonwalk as packing

# The sheets: this many frames in a row, as the sheet of the wave the app has.
COLUMNS = 4
# Colour, of 100: the wave's, and the moonwalk's.
COLOURS = (60, 50)
# The app holds each greeting's pictures under this together (MascotPicturesTest).
MOST = 1_000_000
APP = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app"))


def pack_sheet(frames, out, packed, least):
    """A walk: one still picture with every frame on it, COLUMNS across, row after row. Its
    see-through channel is stored as an edge's is, or almost whole if a frame has a glow in it."""
    rows, over = divmod(len(frames), COLUMNS)
    if over:
        raise SystemExit("%d frames do not fill rows of %d" % (len(frames), COLUMNS))
    width, height = frames[0].size
    sheet = Image.new("RGBA", (COLUMNS * width, rows * height), (0, 0, 0, 0))
    for number, frame in enumerate(frames):
        sheet.paste(frame, ((number % COLUMNS) * width, (number // COLUMNS) * height))
    glow = any(packing.soft_pixels(frame) > packing.GLOW_FROM * least for frame in frames)
    packing.webp(sheet, packing.GLOW if glow else packing.EDGES, os.path.join(packed, "sheet.webp"))
    shutil.move(os.path.join(packed, "sheet.webp"), out)
    return "%d frames on one sheet of %d x %d pixels, %d across and %d down%s" % (
        len(frames), *sheet.size, COLUMNS, rows, ", with a glow" if glow else "")


def main():
    if len(sys.argv) < 3 or not all(colour.isdigit() for colour in sys.argv[3:]):
        raise SystemExit("Name the folder render_wave2.py drew into, and a folder to write into; then, if wanted, colours of 100 to pack at.")
    colours = tuple(int(colour) for colour in sys.argv[3:]) or COLOURS
    for tool in ("cwebp", "webpmux"):
        if shutil.which(tool) is None:
            raise SystemExit("%s is not installed. On a Mac: brew install webp" % tool)
    drawn, out = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
    if os.path.commonpath([out, APP]) == APP:
        raise SystemExit("This is a trial: it does not write into the app.")
    with open(os.path.join(drawn, "wave2.json")) as f:
        told = json.load(f)
    to_the_dp = packing.TO_THE_DP
    if told["pixels_to_the_dp"] != to_the_dp or told["middle_every"] != 1:
        raise SystemExit("Draw the frames again with %g pixels to the dp and without --every: the app's pictures have that." % to_the_dp)
    if round(1000 / told["frames_a_second"]) != packing.FRAME_MS:
        raise SystemExit("The frames were drawn for %d a second, and a frame is shown for %d ms." % (told["frames_a_second"], packing.FRAME_MS))
    walk_in, middle, walk_off, lit, lit_off = (packing.frames_of(os.path.join(drawn, part))
                                               for part in ("in", "middle", "out", "middle-lit", "out-lit"))
    if len(middle) != told["middle_frames"] or len(lit) != len(told["lit_instead"]):
        raise SystemExit("The folder does not hold the frames wave2.json names.")
    lit_middle = list(middle)
    for number, frame in zip(told["lit_instead"], lit):
        lit_middle[number] = frame

    def dp(pixels):
        return pixels / to_the_dp

    for lights, going, off in (("A", middle, walk_off), ("B", lit_middle, lit_off)):
        box = packing.cut_box(walk_in + going + off, walk_in[0].size)
        cut = [[frame.crop(box) for frame in frames] for frames in (walk_in, going, off)]
        width, height = cut[0][0].size
        axis = told["axis"] - box[0]
        ahead, behind = packing.drawn_box(cut[0])[2] - axis, axis - packing.drawn_box(cut[2])[0]
        least = min(packing.soft_pixels(frame) for frames in cut for frame in frames)
        print("%s, the brake lights %s:" % (lights, "go out as he steps off" if lights == "A" else "stay lit all the way off"))
        print("  a frame is %d x %d pixels, %g to the dp: %g x %g dp; a sheet takes %.1f MB of the phone's graphics memory"
              % (width, height, to_the_dp, dp(width), dp(height), COLUMNS * width * (len(cut[0]) // COLUMNS) * height * 4 / 1e6))
        print("  his axis is %d pixels (%.1f dp) from the frame's left edge; his soles are %.1f pixels (%.1f dp) above its lower edge "
              "walking in, %.1f (%.1f dp) walking off"
              % (axis, dp(axis), *(v for part in ("in", "out") for s in [told["soles"][part] - (told["height"] - box[3])] for v in (s, dp(s)))))
        print("  walking in he reaches %d pixels (%.1f dp) ahead of his axis; walking off he reaches %d (%.1f dp) behind it%s"
              % (ahead, dp(ahead), behind, dp(behind), ", glow and all" if lights == "B" else ""))
        print("  one cycle of either walk, %d frames, carries him %.2f pixels (%.2f dp); the middle part is %d frames, %d ms"
              % (len(cut[0]), told["stride"], dp(told["stride"]), len(going), len(going) * packing.FRAME_MS))
        for colour in colours:
            packing.COLOUR = ["-q", str(colour), "-sharp_yuv", "-af", "-m", "6"]
            folder = os.path.join(out, "%s-colour-%d" % (lights, colour))
            os.makedirs(folder, exist_ok=True)
            files = [os.path.join(folder, name) for name in ("mascot_walk_in.webp", "mascot_wave_and_turn.webp", "mascot_walk_off.webp")]
            with tempfile.TemporaryDirectory() as packed:
                said = [pack_sheet(cut[0], files[0], packed, least)]
                film, stored = packing.pack_middle(cut[1], files[1], packed)
                said += [film, pack_sheet(cut[2], files[2], packed, least)]
            worst = packing.check(cut[1], files[1], stored)
            together = sum(os.path.getsize(path) for path in files)
            print("  colour at %d of 100, in %s:" % (colour, folder))
            for path, what in zip(files, said):
                print("    %s: %s, %d bytes" % (os.path.basename(path), what, os.path.getsize(path)))
            print("    played back, every frame of the animated picture is within %.2f of 255 of the frame that was drawn, a pixel on average" % worst)
            print("    together: %d bytes, %s the %d the app holds a greeting under" % (together, "under" if together < MOST else "OVER", MOST))


if __name__ == "__main__":
    main()

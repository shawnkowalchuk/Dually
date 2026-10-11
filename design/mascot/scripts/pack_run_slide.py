#!/usr/bin/env python3
# pack_run_slide.py - makes the three pictures the app shows when a report has been sent, the
# run and the slide, out of the frames render_run_slide.py drew. Run it with plain Python, not
# inside Blender, and name the folder that script drew into:
#
#   python3 design/mascot/scripts/pack_run_slide.py <frames>
#
# The frames must have been drawn 375 pixels high, which is the app's 2.5 pixels to the dp, with
# the smoke (render_run_slide.py <frames> 375) and, for what is printed about him alone, without
# it (the same with --smoke without). It writes three files into
# app/src/main/res/drawable-nodpi/, all WebP with a see-through background, and all are
# committed: the app's build runs none of this.
#
#   mascot_run_in.webp           a still picture: the last two cycles of the run in, RUN_FRAMES
#                                frames side by side on one sheet, COLUMNS across, row after
#                                row, each with the dust his feet have kicked up behind him.
#                                The app draws the frame it wants, because it moves the mascot
#                                with every frame.
#   mascot_run_on_the_spot.webp  an animated picture that plays once: from the frame he gets to
#                                the spot to the last one before the brake lights come on. He
#                                spins his wheels up, runs on the spot with his shoes smoking,
#                                crouches and bolts.
#   mascot_brake_and_slide.webp  an animated picture that plays once: from the frame the brake
#                                lights come on. He brakes, swings round and slides out, the
#                                smoke left lying behind him.
#
# What it prints at the end is what the app's code is told: RunPicture in
# core/designsystem/component/MascotRunPicture.kt. MascotPicturesTest holds the three files to it.
#
# THE TWO ANIMATED PICTURES ARE ONE SIZE, STAND STILL ON THE SCREEN, AND HE MOVES IN THEM. Every frame was
# drawn on the spot, and the film Shawn approved moved the picture by what run-slide.json says
# he has gone (preview_run_slide.py). The app cannot do that with a picture Android plays:
# Android does not say which frame it shows. So each frame is laid here where the film laid it,
# by whole pixels, on a picture as wide as he goes: the smoke, which stays where it was made,
# then stands still in the picture, and nothing has to follow anything. Both pictures are cut
# to the same box, so the second takes the first's place on the screen without anything moving,
# as the party's two do. The shadow under his feet, which the film drew under him in every
# frame, is laid in for the same reason; in the run in the app draws it itself.
#
# THE SECOND BEGINS WITH THE BRAKE, so that the app, which starts it, knows the moment the
# squeal belongs to, as the party's second picture begins with the pop.
#
# THE SLIDE'S PICTURE ENDS REACH dp TO THE RIGHT OF THE SPOT HE RAN ON. That is as far as the
# drawn frames take the last of him by the frame after the last. A screen whose right edge is
# further from its middle than that has the spot nearer its right edge (RunPicture.SLIDE_REACH).
#
# The rest is pack_moonwalk.py's, and done by its functions, at its settings: colour at 50 of
# 100, a picture cut down to what is drawn in any of its frames, a frame with a glow in it
# stored with its see-through channel almost whole (every frame from the brake on), a frame
# stored as the box that changed where that is less than the whole, and each packed picture
# read back and held against the frames that were laid.
#
# It needs Pillow ("pip3 install pillow"), and cwebp and webpmux (Homebrew: "brew install webp").

import json
import math
import os
import shutil
import sys
import tempfile

from PIL import Image, ImageChops

import pack_moonwalk as packing
from pack_greeting import APP_PICTURES, FRAME_MS, run

# How high the frames must have been drawn: the greeting's 150 dp, with 2.5 pixels to the dp.
TALL_DP = 150
PIXELS = round(TALL_DP * packing.TO_THE_DP)
# The sheet: this many frames in a row. RunPicture.SHEET_COLUMNS in the app says the same.
COLUMNS = 4
# The run's cycle, and how many frames of the run in are on the sheet: its last two cycles. One
# would do for him; the dust behind him is not the same in both.
CYCLE = 8
RUN_FRAMES = 2 * CYCLE
SHEET, SPOT, SLIDE = "mascot_run_in.webp", "mascot_run_on_the_spot.webp", "mascot_brake_and_slide.webp"
# The app's soft shadow under his feet (MascotGreeting.kt, groundShadow), as the film laid it
# (preview_run_slide.py): its width and height in dp, how far its middle is above his soles,
# and how dark it is in the middle.
SHADOW = (92.0, 14.0, 3.4, 0.5)
# A frame is stored as the box that changed if that is less than this part of the picture.
BOX = 0.9


def shadow():
    """The shadow as a picture: black, see-through, fading to nothing at its rim."""
    wide, high, _, darkest = SHADOW
    size = (round(wide * packing.TO_THE_DP), round(high * packing.TO_THE_DP))
    alpha = Image.new("L", size)
    dots = alpha.load()
    for y in range(size[1]):
        for x in range(size[0]):
            away = math.hypot((x + 0.5) / size[0] * 2 - 1, (y + 0.5) / size[1] * 2 - 1)
            dots[x, y] = round(255 * darkest * max(0.0, 1 - away))
    patch = Image.new("RGBA", size, (0, 0, 0, 255))
    patch.putalpha(alpha)
    return patch


def lay(canvas, picture, left, top):
    """Lays a see-through picture on a canvas, also where it is partly beyond an edge."""
    box = (max(0, -left), max(0, -top), min(picture.width, canvas.width - left), min(picture.height, canvas.height - top))
    if box[0] < box[2] and box[1] < box[3]:
        canvas.alpha_composite(picture.crop(box), (left + box[0], top + box[1]))


def frame_of(drawn, number, of="with"):
    """A drawn frame. A pixel that is wholly see-through still holds a colour, which nothing
    shows; it is cleared, so that two frames are not taken to differ there."""
    frame = Image.open(os.path.join(drawn, of, "%04d.png" % number)).convert("RGBA")
    solid = frame.getchannel("A").point(lambda value: 255 if value else 0)
    return Image.composite(frame, Image.new("RGBA", frame.size, (0, 0, 0, 0)), solid)


def whole(low, high):
    """A side from low to high with MARGIN to spare, made up to a multiple of WHOLE pixels."""
    low, high = low - packing.MARGIN, high + packing.MARGIN
    length = high - low + -(high - low) % packing.WHOLE
    low -= (length - (high - low)) // 2
    return low, low + length


def standing_still(drawn, told, numbers, right_most=None):
    """The frames `numbers` as the film laid them on a screen, the shadow under him, on a
    picture that stands still: cut to what is drawn in any of them, and at `right_most` pixels
    to the right of the spot if that is said. Returns the frames, how far the spot he runs on
    is from the picture's left edge and his soles above its lower edge, and for each frame how
    far the leftmost thing in it is from the spot. All in pixels."""
    patch = shadow()
    soles_line = told["height"] - told["soles"]
    patch_top = round(soles_line - SHADOW[2] * packing.TO_THE_DP - patch.height / 2)
    laid, boxes = [], []
    for number in numbers:
        frame = frame_of(drawn, number)
        gone = round(told["travel"][number])  # by whole pixels, as the film laid it
        box = frame.getchannel("A").getbbox() or (told["axis"], patch_top, told["axis"], patch_top)
        left = min(box[0] - told["axis"], -patch.width // 2) + gone
        right = max(box[2] - told["axis"], patch.width - patch.width // 2) + gone
        boxes.append((left, min(box[1], patch_top), right, max(box[3], patch_top + patch.height)))
        laid.append((frame, gone))
    left, right = whole(min(b[0] for b in boxes), max(b[2] for b in boxes))
    if right_most is not None:
        right = right_most
        left -= -(right - left) % packing.WHOLE
    top, bottom = whole(min(b[1] for b in boxes), max(b[3] for b in boxes))
    top, bottom = max(0, top), min(told["height"], bottom)
    top -= -(bottom - top) % packing.WHOLE
    frames, leftmost = [], []
    for frame, gone in laid:
        canvas = Image.new("RGBA", (right - left, bottom - top), (0, 0, 0, 0))
        lay(canvas, patch, gone - patch.width // 2 - left, patch_top - top)
        lay(canvas, frame, gone - told["axis"] - left, -top)
        box = canvas.getchannel("A").getbbox()
        frames.append(canvas)
        # A frame with nothing left in the picture counts as beyond its right edge.
        leftmost.append(right if box is None else box[0] + left)
    return frames, -left, told["soles"] - (told["height"] - bottom), leftmost


def pack_sheet(frames, out, packed):
    """The run in: one still picture with every frame on it, COLUMNS across, row after row."""
    rows, over = divmod(len(frames), COLUMNS)
    if over:
        raise SystemExit("%d frames do not fill rows of %d" % (len(frames), COLUMNS))
    width, height = frames[0].size
    sheet = Image.new("RGBA", (COLUMNS * width, rows * height), (0, 0, 0, 0))
    for number, frame in enumerate(frames):
        sheet.paste(frame, ((number % COLUMNS) * width, (number // COLUMNS) * height))
    packing.webp(sheet, packing.EDGES, os.path.join(packed, "sheet.webp"))
    shutil.move(os.path.join(packed, "sheet.webp"), out)
    return "%d frames on one sheet of %d x %d pixels, %d across and %d down" % (len(frames), *sheet.size, COLUMNS, rows)


def check_sheet(frames, out):
    """Reads the packed sheet back and holds every frame of it against the one that was drawn,
    both over the app's dark page. Returns how far the worst frame is: of 255, a pixel on
    average."""
    sheet = Image.open(out).convert("RGBA")
    width, height = frames[0].size
    worst = 0.0
    for number, frame in enumerate(frames):
        left, top = (number % COLUMNS) * width, (number // COLUMNS) * height
        seen = sheet.crop((left, top, left + width, top + height))
        off = ImageChops.difference(packing.on_the_page(seen), packing.on_the_page(frame)).histogram()
        worst = max(worst, sum(value % 256 * count for value, count in enumerate(off)) / (3 * width * height))
    if worst > 2.0:
        raise SystemExit("a frame of %s is %.2f of 255 from the frame that was drawn, a pixel on average" % (out, worst))
    return worst


def pack_played(frames, see_through, out, packed):
    """One animated picture, played once, every frame shown for FRAME_MS (or a multiple of it
    where the frames after it were the same). A frame is stored as the box in which it differs
    from the one before, if that is less than BOX of the picture. Returns what was stored, and
    the list of it for packing.check()."""
    stored, shown, held, boxes = [], None, 0, 0
    for number, frame in enumerate(frames):
        box = (0, 0, *frame.size) if shown is None else packing.changed_box(shown, frame)
        if box is None:
            stored[-1][1] += FRAME_MS
            held += 1
            continue
        # a box's left and upper edges must lie on even pixels: the file's form says so
        box = (box[0] // 2 * 2, box[1] // 2 * 2, box[2], box[3])
        if (box[2] - box[0]) * (box[3] - box[1]) >= BOX * frame.width * frame.height:
            box = (0, 0, *frame.size)
        elif shown is not None:
            boxes += 1
        path = os.path.join(packed, "%s-%04d.webp" % (os.path.basename(out), number))
        packing.webp(frame.crop(box), see_through, path)
        stored.append([path, FRAME_MS, box[0], box[1]])
        if shown is None:
            shown = frame.copy()
        shown.paste(frame.crop(box), box[:2])
    join = ["webpmux"]
    for path, millis, left, top in stored:
        # No blending, and nothing cleared afterwards: a frame replaces what lies under it, and
        # the rest of the picture stays as the frame before left it.
        join += ["-frame", path, "+%d+%d+%d+0-b" % (millis, left, top)]
    # "-loop 1" is once; 0 is for ever.
    run(join + ["-loop", "1", "-bgcolor", "0,0,0,0", "-o", out])
    said = "%d frames of %d ms on a picture of %d x %d pixels, stored as %d: %d not stored because the frame before is shown longer, %d stored as the box that changed; plays once" % (
        len(frames), FRAME_MS, *frames[0].size, len(stored), held, boxes)
    return said, stored


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Name the folder render_run_slide.py drew into.")
    for tool in ("cwebp", "webpmux"):
        if shutil.which(tool) is None:
            raise SystemExit("%s is not installed. On a Mac: brew install webp" % tool)
    drawn = os.path.abspath(sys.argv[1])
    with open(os.path.join(drawn, "run-slide.json")) as f:
        told = json.load(f)
    everything = dict(first=0, last=told["frames"] - 1, every=1, turn=0)
    if told["pixels"] != PIXELS or told["drawn"].get("with") != everything:
        raise SystemExit("Draw every frame again, with the smoke, %d pixels high: the app's pictures have %g pixels to the dp." % (PIXELS, packing.TO_THE_DP))
    if round(1000 / told["frames_a_second"]) != FRAME_MS:
        raise SystemExit("The frames were drawn for %d a second, and a frame is shown for %d ms." % (told["frames_a_second"], FRAME_MS))
    parts = {part["name"]: part for part in told["parts"]}
    arrives, brake, last = parts["SpinUp"]["first"], told["brake_frame"], told["frames"] - 1
    travel = told["travel"]
    a_frame = told["stride"] / CYCLE
    if parts["Run"]["frames"] < RUN_FRAMES + CYCLE or abs(travel[arrives - 1] - travel[arrives - 2] - a_frame) > 0.01:
        raise SystemExit("The run in is not what this script was written for.")

    def dp(pixels):
        return pixels / packing.TO_THE_DP

    # The run in: its last two cycles, on the spot, each frame cut to what is drawn in any.
    run_in = [frame_of(drawn, number) for number in range(arrives - RUN_FRAMES, arrives)]
    box = packing.cut_box(run_in, run_in[0].size)
    ahead = [frame.getchannel("A").getbbox()[2] - told["axis"] for frame in run_in[:CYCLE]]
    run_in = [frame.crop(box) for frame in run_in]
    sheet_axis, sheet_soles = told["axis"] - box[0], told["soles"] - (told["height"] - box[3])

    # The spot and the slide: two pictures that stand still. He is gone from any screen the
    # slide's picture is for by the frame after the last, one more step of the slide's even pace.
    behind = told["axis"] - frame_of(drawn, last).getchannel("A").getbbox()[0]
    reach = travel[last] + (travel[last] - travel[last - 1]) - behind
    right = math.ceil(reach / packing.WHOLE) * packing.WHOLE
    played, spot_at, soles, leftmost = standing_still(drawn, told, range(arrives, last + 1), right)
    on_spot, slide, leftmost = played[:brake - arrives], played[brake - arrives:], leftmost[brake - arrives:]

    os.makedirs(APP_PICTURES, exist_ok=True)
    outs = [os.path.join(APP_PICTURES, name) for name in (SHEET, SPOT, SLIDE)]
    print("the app's three pictures, in %s:" % APP_PICTURES)
    with tempfile.TemporaryDirectory() as packed:
        print("  %s: %s, %d bytes" % (SHEET, pack_sheet(run_in, outs[0], packed), os.path.getsize(outs[0])))
        said_spot, stored_spot = pack_played(on_spot, packing.EDGES, outs[1], packed)
        print("  %s: %s, %d bytes" % (SPOT, said_spot, os.path.getsize(outs[1])))
        said_slide, stored_slide = pack_played(slide, packing.GLOW, outs[2], packed)
        print("  %s: %s, %d bytes" % (SLIDE, said_slide, os.path.getsize(outs[2])))
    print("  read back, every frame is within %.2f of 255 of the frame that was laid, a pixel on average, on the sheet, %.2f in the "
          "first animated picture and %.2f in the second"
          % (check_sheet(run_in, outs[0]), packing.check(on_spot, outs[1], stored_spot), packing.check(slide, outs[2], stored_slide)))
    print("  together: %d bytes" % sum(os.path.getsize(out) for out in outs))

    print("RunPicture is told:")
    print("  the sheet: a frame is %d x %d pixels, %g to the dp: %g x %g dp; %d frames, %d across; it takes %.1f MB of the phone's graphics memory"
          % (*run_in[0].size, packing.TO_THE_DP, dp(run_in[0].width), dp(run_in[0].height), len(run_in), COLUMNS,
             len(run_in) * run_in[0].width * run_in[0].height * 4 / 1e6))
    print("    his axis is %d pixels (%.1f dp) from a frame's left edge, his soles %d (%.1f dp) above its lower edge"
          % (sheet_axis, dp(sheet_axis), sheet_soles, dp(sheet_soles)))
    print("    in the frames of a cycle he reaches this far ahead of his axis, in dp: %s" % ", ".join("%.1f" % dp(a) for a in ahead))
    print("    a frame of the run carries him %.3f pixels (%.4f dp); in the frame he gets to the spot he is %.2f pixels (%.3f dp) short of it"
          % (a_frame, dp(a_frame), -travel[arrives], dp(-travel[arrives])))
    print("  the two animated pictures: %d x %d pixels, %g x %g dp; the spot he runs on is %d pixels (%.1f dp) from the left edge, "
          "his soles %d (%.1f dp) above the lower edge"
          % (*played[0].size, dp(played[0].width), dp(played[0].height), spot_at, dp(spot_at), soles, dp(soles)))
    for name, frames in ((SPOT, on_spot), (SLIDE, slide)):
        print("    %s: %d frames, %d ms" % (name, len(frames), len(frames) * FRAME_MS))
    print("  they end %d pixels (%.1f dp) to the right of the spot; the last of him has gone %.1f pixels (%.2f dp) "
          "past the spot by the frame after the last" % (right, dp(right), reach, dp(reach)))
    print("  in the slide's frames the leftmost thing drawn is this far to the right of the spot, in dp (a minus is to its left):")
    for row in range(0, len(leftmost), 9):
        print("    " + ", ".join("%.1ff" % dp(value) for value in leftmost[row:row + 9]) + ",")

    # What is said about a screen, for the documents: it needs him alone.
    if told["drawn"].get("without") == everything:
        def his_left(number):
            solid = frame_of(drawn, number, "without").getchannel("A").point(lambda value: 255 if value >= 250 else 0)
            return round(travel[number]) - told["axis"] + (solid.getbbox() or (10 ** 6,))[0]

        for width_dp in (274, 393, 411, 610):
            half = width_dp * packing.TO_THE_DP / 2
            first = arrives
            while first > 0 and half + travel[first - 1] + ahead[(first - 1 - arrives) % CYCLE] > 0:
                first -= 1
            gone = next((number for number in range(brake, last + 1) if his_left(number) >= half), last + 1)
            over = max((number for number, value in zip(range(brake, last + 1), leftmost) if value < half), default=brake - 1) + 1
            print("  on a screen %d dp wide: first seen in frame %d of the whole, at the spot in %d, the brake in %d, the last of him gone in %d, "
                  "over in %d: %d frames, %.2f s" % (width_dp, first, arrives, brake, gone, over, over - first, (over - first) * FRAME_MS / 1000))

if __name__ == "__main__":
    main()

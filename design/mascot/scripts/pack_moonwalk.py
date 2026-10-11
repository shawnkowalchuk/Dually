#!/usr/bin/env python3
# pack_moonwalk.py - makes the two pictures the app's second greeting shows, the moonwalk, out of
# the frames render_moonwalk.py drew. Run it with plain Python, not inside Blender, and name the
# folder that script drew into:
#
#   python3 design/mascot/scripts/pack_moonwalk.py <folder>
#
# The frames must have been drawn with 2.5 pixels to the dp, which is what render_moonwalk.py
# draws when it is told nothing else, and what the greeting's other pictures have. It writes two
# files into app/src/main/res/drawable-nodpi/, both WebP with a see-through background, and both
# are committed: the app's build runs none of this.
#
#   mascot_moonwalk.webp       a still picture: one cycle of the glide, its frames side by side
#                              on one sheet, GLIDE_COLUMNS across, row after row. The app draws
#                              the frame it wants, because it moves the mascot with every frame.
#   mascot_stop_and_spin.webp  an animated picture that plays once and keeps its last frame: the
#                              skid with the brake lights, the pose and the hip thrusts, the
#                              kick and the turn, and the way back into the glide. Android
#                              plays it.
#
# What it prints at the end is what the app's code is told: MoonwalkPicture in
# core/designsystem/component/MascotMoonwalk.kt. MascotPicturesTest holds both files to it.
#
# FOUR THINGS ARE DONE HERE THAT pack_greeting.py DOES NOT DO, each for a reason:
#
#   The frames are cut down.   render_moonwalk.py leaves room round the mascot for the halo of
#                              his brake lights, and most of it stays empty. Every frame is cut
#                              to what is drawn in any of them, with MARGIN pixels to spare. The
#                              empty room cost no bytes, but it cost the phone memory.
#   A glow keeps its softness. pack_greeting.py stores the see-through channel coarsely, in
#                              twelve steps: right for an edge, and a soft halo is then drawn as
#                              rings. A frame with a glow in it gets that channel almost whole
#                              (GLOW). The other frames get what the wave's have (EDGES).
#   A frame that is the one    Twice in the middle part he stands still for two frames. The
#   before is not stored.      second of them is not stored: the first is shown twice as long.
#   A frame that barely        Where only the needles move, the frame is stored as the small
#   changes is a patch.        box that changed, laid over the frame before. So a frame in this
#                              picture may depend on the one before it.
#
# It needs Pillow ("pip3 install pillow"), and cwebp and webpmux (Homebrew: "brew install webp").

import json
import os
import shutil
import sys
import tempfile

from PIL import Image, ImageChops

from pack_greeting import APP_PICTURES, FRAME_MS, run

# The app's pictures of the mascot have this many pixels to the dp (render_greeting.py).
TO_THE_DP = 2.5
# The glide's sheet: this many frames in a row. MoonwalkPicture.GLIDE_COLUMNS in the app says
# the same. Seven across and two down keeps the sheet under 4,096 pixels a side, which every
# phone's graphics chip takes as one picture.
GLIDE_COLUMNS = 7
# Clear pixels kept between what is drawn and the cut picture's edge, at least. The sides are
# then made up to a multiple of WHOLE pixels, which is 4 dp: whole numbers for the app's code.
MARGIN = 8
WHOLE = 10

# Colour at 50 of 100, where the wave's pictures have 60. He is never still in these pictures,
# and at 50 a frame could not be told from one at 60 at a phone's size; at 40 the numbers on the
# dials and the lines between his fingers smear. The rest is pack_greeting.py's: -sharp_yuv keeps
# the green from bleeding into the white gloves, and without -exact the see-through pixels beside
# an edge get that edge's colour, so no fringe is drawn over the app's dark page.
COLOUR = ["-q", "50", "-sharp_yuv", "-af", "-m", "6"]
# The see-through channel, of 100: for a frame with nothing but edges in it, as the wave's
# pictures have it; and for a frame with a glow in it. At 80 the channel is within one step of
# 255 of what was drawn; at 70 and under it has sixteen steps or fewer, and the halo is rings.
EDGES = "50"
GLOW = "80"
# A frame has a glow in it when more than this many times as many of its pixels are partly
# see-through as in the frame with the fewest. Edges alone come to about the same in every frame.
GLOW_FROM = 2.0

# The app's dark page, for the check that the packed picture plays as it was drawn.
PAGE = (0x12, 0x13, 0x16, 255)

# Two frames that differ by no more than this, of 255, in any pixel are the same frame: two runs
# of the renderer on one pose differ by up to 3.
NOISE = 3
# A frame that differs from the one before in a box smaller than this part of the picture is
# stored as that box alone.
PATCH = 0.25


def frames_of(folder):
    """The frames in a folder, in their order. A pixel that is wholly see-through still holds a
    colour, which nothing shows; it is cleared, so that two frames are not taken to differ there."""
    names = sorted(name for name in os.listdir(folder) if name.endswith(".png"))
    if not names:
        raise SystemExit("No frames in %s" % folder)
    frames = []
    for name in names:
        frame = Image.open(os.path.join(folder, name)).convert("RGBA")
        drawn = frame.getchannel("A").point(lambda value: 255 if value else 0)
        frames.append(Image.composite(frame, Image.new("RGBA", frame.size, (0, 0, 0, 0)), drawn))
    return frames


def drawn_box(frames):
    """The box that holds everything drawn in any of the frames."""
    boxes = [frame.getchannel("A").getbbox() for frame in frames]
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))


def cut_box(frames, size):
    """The part of the drawn picture that is kept: what is drawn in any frame and MARGIN more,
    its sides multiples of WHOLE. What is added to make them so goes on evenly, as far as the
    drawn picture has room."""
    left, top, right, bottom = drawn_box(frames)

    def side(low, high, most):
        low, high = max(0, low - MARGIN), min(most, high + MARGIN)
        length = high - low + -(high - low) % WHOLE
        if length > most:
            raise SystemExit("The drawn picture has no room for sides of whole multiples of %d pixels." % WHOLE)
        low = max(0, min(low - (length - (high - low)) // 2, most - length))
        return low, low + length

    (left, right), (top, bottom) = side(left, right, size[0]), side(top, bottom, size[1])
    return left, top, right, bottom


def soft_pixels(frame):
    """How many of a frame's pixels are partly see-through: its edges, and any glow."""
    return sum(frame.getchannel("A").histogram()[1:255])


def changed_box(before, after):
    """The box in which a frame differs from the one before by more than the renderer's noise,
    or None if it does not."""
    most = None
    for band in ImageChops.difference(before, after).split():
        most = band if most is None else ImageChops.lighter(most, band)
    return most.point(lambda value: 255 if value > NOISE else 0).getbbox()


def webp(picture, see_through, path):
    """Packs one picture, with the see-through channel at that quality."""
    drawn = path + ".png"
    picture.save(drawn)
    run(["cwebp", "-quiet", *COLOUR, "-alpha_q", see_through, drawn, "-o", path])
    os.remove(drawn)


def pack_glide(frames, out, packed):
    """The glide: one still picture with every frame on it, GLIDE_COLUMNS across, row after row.
    The frames touch; each has a see-through margin of its own, so none shows in its neighbour."""
    rows, over = divmod(len(frames), GLIDE_COLUMNS)
    if over:
        raise SystemExit("%d frames do not fill rows of %d" % (len(frames), GLIDE_COLUMNS))
    width, height = frames[0].size
    sheet = Image.new("RGBA", (GLIDE_COLUMNS * width, rows * height), (0, 0, 0, 0))
    for number, frame in enumerate(frames):
        sheet.paste(frame, ((number % GLIDE_COLUMNS) * width, (number // GLIDE_COLUMNS) * height))
    webp(sheet, EDGES, os.path.join(packed, "sheet.webp"))
    shutil.move(os.path.join(packed, "sheet.webp"), out)
    return "%d frames on one sheet, %d across and %d down" % (len(frames), GLIDE_COLUMNS, rows)


def pack_middle(frames, out, packed):
    """The middle part: one animated picture, played once. Each frame is shown for FRAME_MS, or
    for a multiple of it where the frames after it were the same. Returns what was stored, and
    the list of it for check()."""
    least = min(soft_pixels(frame) for frame in frames)
    stored = []  # the file, how long it is shown, and where its left and upper edges lie
    shown = None  # what the phone shows after the frames stored so far, as it was drawn
    held = patches = glows = 0
    for number, frame in enumerate(frames):
        box = (0, 0, *frame.size) if shown is None else changed_box(shown, frame)
        if box is None:
            stored[-1][1] += FRAME_MS
            held += 1
            continue
        # a patch's left and upper edges must lie on even pixels: the file's form says so
        box = (box[0] // 2 * 2, box[1] // 2 * 2, box[2], box[3])
        if (box[2] - box[0]) * (box[3] - box[1]) >= PATCH * frame.width * frame.height:
            box = (0, 0, *frame.size)
        elif shown is not None:
            patches += 1
        glow = soft_pixels(frame) > GLOW_FROM * least
        glows += glow
        path = os.path.join(packed, "%04d.webp" % number)
        webp(frame.crop(box), GLOW if glow else EDGES, path)
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
    said = ("%d frames of %d ms, stored as %d: %d not stored because the frame before is shown longer, "
            "%d stored as a patch, %d with a glow; plays once" % (len(frames), FRAME_MS, len(stored), held, patches, glows))
    return said, stored


def on_the_page(picture):
    """A see-through picture as it is seen over the app's dark page."""
    page = Image.new("RGBA", picture.size, PAGE)
    page.alpha_composite(picture)
    return page.convert("RGB")


def check(frames, out, stored):
    """Plays the packed picture as a phone would and holds every frame against the one that was
    drawn, both over the app's dark page: a patch in the wrong place, or a frame too many, shows
    here and not on the phone. Returns how far the worst frame is from the one drawn: of 255, a
    pixel on average."""
    played = Image.open(out)
    if played.n_frames != len(stored) or played.info.get("loop") != 1:
        raise SystemExit("%s does not hold %d frames that play once" % (out, len(stored)))
    worst, at = 0.0, 0
    for number, (_, millis, _, _) in enumerate(stored):
        played.seek(number)
        seen = played.convert("RGBA")  # how long it is shown is known once the frame is read
        if played.info["duration"] != millis:
            raise SystemExit("frame %d of %s is shown for %d ms, not %d" % (number, out, played.info["duration"], millis))
        for _ in range(millis // FRAME_MS):
            off = ImageChops.difference(on_the_page(seen), on_the_page(frames[at])).histogram()
            mean = sum(value % 256 * count for value, count in enumerate(off)) / (3 * seen.width * seen.height)
            worst, at = max(worst, mean), at + 1
    if at != len(frames):
        raise SystemExit("%s plays %d frames, and %d were drawn" % (out, at, len(frames)))
    if worst > 2.0:
        raise SystemExit("a frame of %s is %.2f of 255 from the frame that was drawn, a pixel on average" % (out, worst))
    return worst


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Name the folder render_moonwalk.py drew into.")
    for tool in ("cwebp", "webpmux"):
        if shutil.which(tool) is None:
            raise SystemExit("%s is not installed. On a Mac: brew install webp" % tool)
    drawn = os.path.abspath(sys.argv[1])
    with open(os.path.join(drawn, "moonwalk.json")) as f:
        told = json.load(f)
    if told["pixels_to_the_dp"] != TO_THE_DP or told["middle_every"] != 1:
        raise SystemExit("Draw the frames again with %g pixels to the dp and without --every: the app's pictures have that." % TO_THE_DP)
    if round(1000 / told["frames_a_second"]) != FRAME_MS:
        raise SystemExit("The frames were drawn for %d a second, and a frame is shown for %d ms." % (told["frames_a_second"], FRAME_MS))
    glide, middle = frames_of(os.path.join(drawn, "glide")), frames_of(os.path.join(drawn, "middle"))
    if len(glide) != told["glide_frames"] or len(middle) != told["middle_frames"]:
        raise SystemExit("The folder does not hold the frames moonwalk.json names.")

    box = cut_box(glide + middle, glide[0].size)
    glide, middle = [frame.crop(box) for frame in glide], [frame.crop(box) for frame in middle]
    width, height = glide[0].size
    axis = told["axis"] - box[0]
    soles = told["soles"] - (told["height"] - box[3])
    reach = drawn_box(glide)

    os.makedirs(APP_PICTURES, exist_ok=True)
    sheet, film = os.path.join(APP_PICTURES, "mascot_moonwalk.webp"), os.path.join(APP_PICTURES, "mascot_stop_and_spin.webp")
    with tempfile.TemporaryDirectory() as packed:
        print("mascot_moonwalk.webp: %s, %d bytes" % (pack_glide(glide, sheet, packed), os.path.getsize(sheet)))
        said, stored = pack_middle(middle, film, packed)
        print("mascot_stop_and_spin.webp: %s, %d bytes" % (said, os.path.getsize(film)))
    print("  played back, every frame is within %.2f of 255 of the frame that was drawn, a pixel on average" % check(middle, film, stored))
    print("together: %d bytes" % (os.path.getsize(sheet) + os.path.getsize(film)))

    def dp(pixels):
        return pixels / TO_THE_DP

    print("MoonwalkPicture is told:")
    print("  a frame is %d x %d pixels, %g to the dp: %g x %g dp" % (width, height, TO_THE_DP, dp(width), dp(height)))
    print("  his axis is %d pixels (%.1f dp) from the frame's left edge" % (axis, dp(axis)))
    print("  gliding, his soles are %.1f pixels (%.1f dp) above the frame's lower edge" % (soles, dp(soles)))
    print("  gliding, he reaches %d pixels (%.1f dp) to the left of his axis and %d (%.1f dp) to the right"
          % (axis - reach[0], dp(axis - reach[0]), reach[2] - axis, dp(reach[2] - axis)))
    print("  one cycle of the glide, %d frames, carries him %.1f pixels (%.2f dp)" % (len(glide), told["stride"], dp(told["stride"])))
    print("  the middle part is %d frames, %d ms" % (len(middle), len(middle) * FRAME_MS))


if __name__ == "__main__":
    main()

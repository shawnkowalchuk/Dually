#!/usr/bin/env python3
# pack_party.py - makes the two pictures the app's one-year party shows, out of the frames
# render_party.py drew. Run it with plain Python, not inside Blender, and name the folder that
# script drew into:
#
#   python3 design/mascot/scripts/pack_party.py <folder>
#
# The frames must be render_party.py's "with" frames, him and the confetti, every one of them,
# from the front, drawn at that script's own size: the frames the preview's film was made of. It
# writes two files into app/src/main/res/drawable-nodpi/, both animated WebP pictures with a
# see-through background that play once and keep their last frame, and both are committed: the
# app's build runs none of this.
#
#   mascot_party_popper.webp   from the frame he stands still in to the last one before the
#                              popper goes off: he stands, feels about behind his back, brings
#                              the popper out, holds it up, aims it and takes the string.
#   mascot_party_pop.webp      from the frame the popper goes off on: the flash, the confetti
#                              going up and coming down, his arms up.
#
# THE CLIP IS CUT IN TWO AT THE FRAME OF THE POP, because Android plays an animated picture by
# its own clock and does not say which frame it shows. The app starts the second picture itself,
# and that moment is the pop: the sound is played then, and cannot come apart from the picture
# however slowly a phone draws.
#
# What it prints at the end is what the app's code is told: MascotPartyPicture in
# core/designsystem/component/MascotPartyPicture.kt. MascotPicturesTest holds both files to it.
#
# WHAT IS DONE TO THE FRAMES, each for a reason:
#
#   They are made smaller.     The film's frames have him 600 pixels high, a phone's own
#                              pixels. Packed so, the party weighs over four megabytes. Here
#                              he is APP_PIXELS high, as many pixels as the greetings' mascot
#                              has, and the app shows the picture larger. He is a little
#                              softer than in the film.
#   The room's top is cut.     Nothing is ever drawn in the top of the film's room. It cost no
#                              bytes, but it cost the phone memory.
#   The last frame is left     The app fades him out over the clip's last frames, as the film
#   out.                       does, and in the last one nothing of him is left to see.
#   The frames he stands       He stands still for the clip's first half second, for the app to
#   still in are left out.     fade him in on: thirteen frames that are one picture. Only the
#                              last of them is in the first file, as its first frame; the app
#                              shows that frame, the picture not yet playing, for the time of
#                              the other twelve. Stored once and to be shown for thirteen
#                              frames' time, which is what the file's form is for, Android let
#                              it go after about a tenth of a second (seen on an emulator,
#                              2026-10-10): the popper then went off half a second early.
#   A frame that is the one    Where he stands still again later, the frame is stored once and
#   before is not stored.      shown as long, as pack_moonwalk.py does. Android keeps to that
#                              for every frame but a picture's first.
#   A frame that barely        Where little moves, the frame is stored as the box that changed,
#   changes is a patch.        laid over the frame before, as pack_moonwalk.py does.
#   The confetti is packed     Paper in the air is small, bright and gone in a twenty-fourth of
#   harder than he is.         a second. Its frames get less colour and a coarser see-through
#                              channel than the frames in which he is alone and is looked at.
#   A frame that is fading     Over the clip's last frames the app fades him out. A frame that
#   is packed harder still.    is shown a quarter as strongly is packed with a quarter of the
#                              colour: what packing takes from it fades with it.
#
# It needs Pillow ("pip3 install pillow"), and cwebp and webpmux (Homebrew: "brew install webp").

import json
import os
import shutil
import sys
import tempfile

from PIL import Image

from pack_greeting import APP_PICTURES, FRAME_MS, run
from pack_moonwalk import changed_box, check, frames_of
from preview_party import FADE_OUT

# How high the standing mascot is in the app's pictures, in pixels: what the greetings' mascot
# has (150 dp at 2.5 pixels to the dp). render_party.py draws him DRAWN_PIXELS high for the film.
APP_PIXELS = 375
DRAWN_PIXELS = 600
# The film shows the drawn frames on a screen with this many pixels to the dp
# (preview_party.py: 1080 pixels are a phone's width). It is what the app's sizes are worked out
# from, so that the party is as large on a phone as it was in the film.
FILM_TO_THE_DP = 2.625
# How much of the drawn room's top is cut off, in drawn pixels. Nothing may be drawn there.
CUT_ABOVE = 100

# Colour, of 100, for the frames before the pop and for the frames from it on; the wave's
# pictures have 60 and the moonwalk's 50. The rest is pack_greeting.py's (cwebp_for): -sharp_yuv
# keeps the green from bleeding into the white gloves, and without -exact the see-through pixels
# beside an edge get that edge's colour, so no fringe is drawn over the app's dark page or its
# lime tile.
# The see-through channel, of 100: the wave's 50 before the pop, and less from it on.
BEFORE_THE_POP = dict(colour=40, see_through=50)
FROM_THE_POP = dict(colour=28, see_through=30)
# A frame that is fading out gets its colour in proportion to how strongly it is shown, and no
# less than this.
FADING_LEAST = 6

# A frame that differs from the one before in a box smaller than this part of the picture is
# stored as that box alone.
PATCH = 0.25


def made_smaller(frames, told):
    """The drawn frames as the app carries them: the room's top cut off, and the rest made
    smaller until he is APP_PIXELS high. Returns them with how much smaller they are."""
    if told["pixels"] != DRAWN_PIXELS:
        raise SystemExit("Draw the frames at render_party.py's own size, he %d pixels high: the film was made of those."
                         % DRAWN_PIXELS)
    highest = min(frame.getchannel("A").getbbox()[1] for frame in frames)
    if highest < CUT_ABOVE:
        raise SystemExit("Something is drawn %d pixels below the room's top, and %d are cut off." % (highest, CUT_ABOVE))
    width, height = told["width"], told["height"] - CUT_ABOVE
    by = APP_PIXELS / DRAWN_PIXELS
    if width * by % 1 or height * by % 1:
        raise SystemExit("%d x %d pixels do not come to whole pixels when he is made %d high." % (width, height, APP_PIXELS))
    size = (round(width * by), round(height * by))
    # Pillow makes a see-through picture smaller with its colours weighed by how much of each
    # pixel is covered, so no edge takes on the colour of what is not drawn beside it.
    return [frame.crop((0, CUT_ABOVE, told["width"], told["height"])).resize(size, Image.LANCZOS) for frame in frames], by


def smooth(t):
    """preview_party.py's ease, by which the film fades him in and out."""
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def cwebp_for(colour, see_through, strength=1.0):
    """cwebp's settings for one frame that is shown this strongly, of 1."""
    return ["-q", str(max(FADING_LEAST, round(colour * strength))), "-alpha_q", str(see_through),
            "-sharp_yuv", "-af", "-m", "6"]


def pack_film(frames, settings_for, out, packed):
    """One animated picture, played once. Each frame is shown for FRAME_MS, or for a multiple
    of it where the frames after it were the same. `settings_for` gives cwebp's settings for a
    frame by its number. Returns what was stored, and the list of it for check()."""
    stored = []  # the file, how long it is shown, and where its left and upper edges lie
    shown = None  # what the phone shows after the frames stored so far, as it was drawn
    held = patches = 0
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
        path = os.path.join(packed, "%s-%04d.webp" % (os.path.basename(out), number))
        frame.crop(box).save(path + ".png")
        run(["cwebp", "-quiet", *settings_for(number), path + ".png", "-o", path])
        os.remove(path + ".png")
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
            "%d stored as a patch; plays once" % (len(frames), FRAME_MS, len(stored), held, patches))
    return said, stored


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Name the folder render_party.py drew into.")
    for tool in ("cwebp", "webpmux"):
        if shutil.which(tool) is None:
            raise SystemExit("%s is not installed. On a Mac: brew install webp" % tool)
    drawn = os.path.abspath(sys.argv[1])
    with open(os.path.join(drawn, "party.json")) as f:
        told = json.load(f)
    count, pop, hold = told["frames"], told["pop_frame"], told["hold_frames"]
    if told["drawn"].get("with") != dict(first=0, last=count - 1, every=1, turn=0):
        raise SystemExit("The app's pictures need every frame of him and the confetti, from the front: draw them with render_party.py first.")
    if round(1000 / told["frames_a_second"]) != FRAME_MS:
        raise SystemExit("The frames were drawn for %d a second, and a frame is shown for %d ms." % (told["frames_a_second"], FRAME_MS))
    frames = frames_of(os.path.join(drawn, "with"))
    if len(frames) != count:
        raise SystemExit("The folder does not hold the %d frames party.json names." % count)
    frames, by = made_smaller(frames, told)
    still = frames[:hold + 1]
    if any(changed_box(still[0], frame) is not None for frame in still):
        raise SystemExit("He does not stand still for the clip's first %d frames: the app fades him in on one picture." % (hold + 1))
    # The frames he stands still in but the last are the app's to show; and the clip's last
    # frame is not shown at all: the app has faded him out by then.
    before, after = frames[hold:pop], frames[pop:count - 1]

    os.makedirs(APP_PICTURES, exist_ok=True)
    first, second = os.path.join(APP_PICTURES, "mascot_party_popper.webp"), os.path.join(APP_PICTURES, "mascot_party_pop.webp")

    def fading(number):
        """From the pop on: how strongly a frame is shown, as the film and the app fade him out."""
        return cwebp_for(**FROM_THE_POP, strength=smooth((count - 1 - (pop + number)) / FADE_OUT))

    with tempfile.TemporaryDirectory() as packed:
        for name, part, settings_for, out in (("mascot_party_popper.webp", before, lambda number: cwebp_for(**BEFORE_THE_POP), first),
                                              ("mascot_party_pop.webp", after, fading, second)):
            said, stored = pack_film(part, settings_for, out, packed)
            print("%s: %s, %d bytes" % (name, said, os.path.getsize(out)))
            print("  played back, every frame is within %.2f of 255 of the frame that was drawn, a pixel on average"
                  % check(part, out, stored))
    print("together: %d bytes" % (os.path.getsize(first) + os.path.getsize(second)))

    to_the_dp = FILM_TO_THE_DP * by

    def dp(pixels):
        return pixels / to_the_dp

    width, height = frames[0].size
    axis, soles = told["axis"] * by, told["soles"] * by
    print("MascotPartyPicture is told:")
    print("  a frame is %d x %d pixels; shown as large as in the film that is %.2f x %.2f dp, %.4f pixels to the dp"
          % (width, height, dp(width), dp(height), to_the_dp))
    print("  he stands %d pixels (%.2f dp) high; his axis is %.1f pixels (%.2f dp) from the frame's left edge, "
          "his soles %.1f pixels (%.2f dp) above its lower edge" % (APP_PIXELS, dp(APP_PIXELS), axis, dp(axis), soles, dp(soles)))
    print("  the app shows the first picture's first frame, still, for %d frames' time, %d ms; then the picture plays: "
          "%d frames, %d ms" % (hold, hold * FRAME_MS, len(before), len(before) * FRAME_MS))
    print("  the popper goes off %d ms after he first shows, with the second picture: %d frames, %d ms"
          % ((hold + len(before)) * FRAME_MS, len(after), len(after) * FRAME_MS))
    print("  the app fades him in over the first %d frames and out over the clip's last %d, as preview_party.py does: "
          "the last %d of the second picture" % (hold, FADE_OUT, FADE_OUT - 1))


if __name__ == "__main__":
    main()

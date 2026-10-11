#!/usr/bin/env python3
# preview_party.py - makes a film of the party on a phone's screen, with sound if it is given
# any, and writes what else there is to show of it. Plain Python, with Pillow and ffmpeg:
#
#   python3 design/mascot/scripts/preview_party.py <frames> <folder to write into> [--name <name>] [--pop <sound>] [--before <sound>] [--after <sound>]
#
# <frames> is what render_party.py drew: its "with" frames, him and the confetti. Drawn at
# render_party.py's own size the film is a phone's screen of 1080 by 2400 pixels; drawn smaller,
# for a quick look, the screen is made smaller to match. It writes:
#
#   party-preview.mp4          the film, 24 frames a second. With --name A it is
#                              party-preview-A.mp4, so that several can lie side by side
#   party-preview.gif          the same without sound, the middle of the screen only and
#                              smaller, to open anywhere. A GIF counts in hundredths of a
#                              second: it plays 25 frames a second
#   party-sheet.png            a frame from every quarter second, with its number and its time
#   party-sheet-reach.png      every frame while the hand goes behind his back and comes out
#   party-sheet-pop.png        every frame from the string being taken until after the pop
#   party-cap-*.png            if render_party.py also drew the cap (--cap): the two stills of
#                              him in it, on the dark page and on white
#
# The film is the app's dark page and nothing else on it. He stands in the middle of the
# screen, the app's soft shadow under his feet. He fades in while he stands still at the start
# of the clip, and fades out over the clip's last FADE_OUT frames, the confetti still coming
# down; then the page is empty for a moment.
#
# The sounds are files of any kind ffmpeg reads; none of them is kept in the repository.
# --pop is the bang: where it is loudest in the file is found, and the file is laid so that
# this lies on the frame the popper goes off on (party.json says which). --before is a rustle
# for the popper being brought out, laid so that it begins as the hand comes out from behind
# his back; --after is whatever follows the bang, a horn or a cheer, laid AFTER_FRAMES frames
# after it. A sound may be followed by a colon and how much louder it is to be, in decibels:
# --after horn.mp3:-6.
#
# THE APP DOES NOT PLAY THIS. It is a preview to decide by.

import array
import json
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw

# The app's dark page (MiloColors).
PAGE = (0x12, 0x13, 0x16)
# A phone's screen, and the height at which render_party.py draws him for it.
SCREEN = (1080, 2400)
DRAWN_FOR_SCREEN = 600
# The app's soft shadow under his feet (MascotGreeting.kt), as parts of his height: its width
# and height, how far its middle is above his soles; and how dark it is in the middle.
SHADOW = (92.0 / 150, 14.0 / 150, 3.4 / 150, 0.5)
FADE_OUT = 12  # frames
EMPTY = 6  # frames of the empty page at the end
# The frame the hand starts out from behind his back on, where a rustle begins; and how many
# frames after the bang whatever follows it begins.
OUT_FRAME, AFTER_FRAMES = 41, 7
# The sheets of every frame: from which frame to which.
REACH, POP_AROUND = (20, 51), (-12, 19)
GIF_WIDTH = 400
RATE = 48000  # the film's sound, samples a second


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def shadow(height):
    wide, high, _, darkest = SHADOW
    size = (round(wide * height), round(high * height))
    alpha = Image.new("L", size)
    for y in range(size[1]):
        for x in range(size[0]):
            away = math.hypot((x + 0.5) / size[0] * 2 - 1, (y + 0.5) / size[1] * 2 - 1)
            alpha.putpixel((x, y), round(255 * darkest * max(0.0, 1 - away)))
    patch = Image.new("RGBA", size, (0, 0, 0, 255))
    patch.putalpha(alpha)
    return patch


def faded(picture, part):
    """A see-through picture with only this part of it showing."""
    if part >= 1.0:
        return picture
    out = picture.copy()
    out.putalpha(picture.getchannel("A").point(lambda a: round(a * part)))
    return out


def heard(path):
    """A sound as numbers, one channel, RATE to the second, each from -32768 to 32767."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"],
                         capture_output=True, check=True).stdout
    return array.array("h", raw)


def loudest(samples):
    """Where a sound's bang is, in samples from its start: the loudest sample of the first
    burst that comes within a half of the loudest there is, and where that burst begins (the
    first sample a tenth as loud, within a twentieth of a second before it)."""
    top = max(abs(s) for s in samples)
    front = next(i for i, s in enumerate(samples) if abs(s) >= top / 2)
    burst = samples[front:front + RATE // 33]
    peak = front + max(range(len(burst)), key=lambda i: abs(burst[i]))
    starts = next(i for i in range(max(0, front - RATE // 20), front + 1) if abs(samples[i]) >= top / 10)
    return peak, starts


def begins(samples):
    """Where a sound that is no bang begins: the first sample a tenth as loud as its loudest."""
    top = max(abs(s) for s in samples)
    return next(i for i, s in enumerate(samples) if abs(s) >= top / 10)


def sound_and_gain(said):
    path, _, gain = said.rpartition(":")
    try:
        return (path, float(gain)) if path else (said, 0.0)
    except ValueError:
        return said, 0.0


def lay(path, gain, at, from_sample, number):
    """The part of an ffmpeg filter graph that lays one sound into the film: what is `from_sample`
    samples into the file comes `at` samples into the film. What would come before the film
    begins is cut off."""
    start = at - from_sample
    cut, wait = max(0, -start), max(0, start)
    return ("[%d:a]aresample=%d,aformat=channel_layouts=stereo,atrim=start_sample=%d,asetpts=PTS-STARTPTS,"
            "adelay=delays=%dS:all=1,volume=%.2fdB[s%d]" % (number, RATE, cut, wait, gain, number))


def main():
    asked = sys.argv[1:]
    said = {"--name": "", "--pop": "", "--before": "", "--after": ""}
    for flag in said:
        if flag in asked:
            at = asked.index(flag)
            said[flag] = asked[at + 1] if at + 1 < len(asked) else ""
            del asked[at:at + 2]
    if len(asked) != 2:
        raise SystemExit("Name the folder render_party.py drew into, and a folder to write into; then, if wanted, "
                         "--name <name>, --pop <sound>, --before <sound> and --after <sound>.")
    drawn, out = os.path.abspath(asked[0]), os.path.abspath(asked[1])
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(drawn, "party.json")) as f:
        told = json.load(f)
    count, rate, pop = told["frames"], told["frames_a_second"], told["pop_frame"]
    whole = dict(first=0, last=count - 1, every=1, turn=0)
    if told["drawn"].get("with") != whole:
        raise SystemExit("The film needs every frame of him and the confetti, from the front: draw them with render_party.py first.")
    names = ["%04d.png" % number for number in range(count)]

    small = told["pixels"] / DRAWN_FOR_SCREEN
    screen = tuple(2 * round(side * small / 2) for side in SCREEN)
    # He stands in the middle of the screen: half his height above the middle are his soles.
    left = round(screen[0] / 2 - told["axis"])
    top = round(screen[1] / 2 + told["pixels"] / 2 + told["soles"] - told["height"])
    patch = shadow(told["pixels"])
    shadow_at = (round(screen[0] / 2 - patch.width / 2),
                 round(screen[1] / 2 + told["pixels"] / 2 - SHADOW[2] * told["pixels"] - patch.height / 2))
    # What the sheets and the GIF show of the screen: the rows the picture lies in.
    rows = (max(0, top), min(screen[1], top + told["height"]))

    def frame_of(number):
        """A frame of the film: the page, and over it the shadow and the picture, faded."""
        made = Image.new("RGBA", screen, PAGE + (255,))
        if number >= count:
            return made.convert("RGB")
        part = min(smooth(number / told["hold_frames"]), smooth((count - 1 - number) / FADE_OUT))
        picture = Image.open(os.path.join(drawn, "with", names[number])).convert("RGBA")
        made.alpha_composite(faded(patch, part), shadow_at)
        box = (max(0, -left), max(0, -top), min(picture.width, screen[0] - left), min(picture.height, screen[1] - top))
        made.alpha_composite(faded(picture.crop(box), part), (left + box[0], top + box[1]))
        return made.convert("RGB")

    silent = os.path.join(out, "party-silent.mp4")
    encoder = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % screen, "-r", str(rate),
         "-i", "-", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "17", "-preset", "slow", "-movflags", "+faststart", silent],
        stdin=subprocess.PIPE)
    quarter = rate // 4
    tile = (round(300 * small), round(300 * small * (rows[1] - rows[0]) / screen[0]))
    sheets = {"": [], "-reach": [], "-pop": []}
    for number in range(count + EMPTY):
        made = frame_of(number)
        encoder.stdin.write(made.tobytes())
        if number >= count:
            continue
        shown = made.crop((0, rows[0], screen[0], rows[1])).resize(tile, Image.LANCZOS)
        if number % quarter == 0:
            sheets[""].append((number, shown))
        if REACH[0] <= number <= REACH[1]:
            sheets["-reach"].append((number, shown))
        if pop + POP_AROUND[0] <= number <= pop + POP_AROUND[1]:
            sheets["-pop"].append((number, shown))
    encoder.stdin.close()
    if encoder.wait() != 0:
        raise SystemExit("ffmpeg could not write " + silent)
    seconds = (count + EMPTY) / rate

    written = []
    for ending, tiles in sheets.items():
        columns = 8
        sheet = Image.new("RGB", (columns * tile[0], math.ceil(len(tiles) / columns) * tile[1]), PAGE)
        pen = ImageDraw.Draw(sheet)
        for i, (number, shown) in enumerate(tiles):
            x, y = (i % columns) * tile[0], (i // columns) * tile[1]
            sheet.paste(shown, (x, y))
            pen.rectangle((x, y, x + tile[0] - 1, y + tile[1] - 1), outline=(70, 70, 70))
            pen.text((x + 6, y + 5), "%d   %.2f s%s" % (number, number / rate, "   POP" if number == pop else ""), fill=(255, 255, 255))
        sheet.save(os.path.join(out, "party-sheet%s.png" % ending))
        written.append("party-sheet%s.png" % ending)

    look = "crop=%d:%d:0:%d,scale=%d:-2:flags=lanczos" % (screen[0], rows[1] - rows[0], rows[0], min(GIF_WIDTH, screen[0]))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", silent, "-filter_complex",
                    "[0:v]%s,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4,setpts=N/25/TB" % look,
                    "-r", "25", os.path.join(out, "party-preview.gif")], check=True)
    written.append("party-preview.gif")

    cap = os.path.join(drawn, "cap")
    if os.path.isdir(cap):
        for name, view in (("0000.png", "front"), ("0001.png", "three-quarter")):
            picture = Image.open(os.path.join(cap, name)).convert("RGBA")
            for ground, colour in (("dark", PAGE), ("white", (255, 255, 255))):
                flat = Image.new("RGBA", picture.size, colour + (255,))
                flat.alpha_composite(picture)
                flat.convert("RGB").save(os.path.join(out, "party-cap-%s-on-%s.png" % (view, ground)))
                written.append("party-cap-%s-on-%s.png" % (view, ground))

    film = os.path.join(out, "party-preview%s.mp4" % ("-" + said["--name"] if said["--name"] else ""))
    sounds, laid = [], []
    if said["--pop"]:
        path, gain = sound_and_gain(said["--pop"])
        peak, front = loudest(heard(path))
        sounds.append(lay(path, gain, round(pop / rate * RATE), peak, len(sounds) + 1))
        laid.append("the bang: %s, loudest %.3f s into the file (it begins at %.3f s), laid on frame %d, %.3f s into the film"
                    % (os.path.basename(path), peak / RATE, front / RATE, pop, pop / rate))
    for flag, frame, what in (("--before", OUT_FRAME, "before it"), ("--after", pop + AFTER_FRAMES, "after it")):
        if said[flag]:
            path, gain = sound_and_gain(said[flag])
            front = begins(heard(path))
            sounds.append(lay(path, gain, round(frame / rate * RATE), front, len(sounds) + 1))
            laid.append("%s: %s, which begins %.3f s into the file, laid to begin on frame %d, %.3f s into the film%s"
                        % (what, os.path.basename(path), front / RATE, frame, frame / rate, ", %+g dB" % gain if gain else ""))
    if sounds:
        command = ["ffmpeg", "-y", "-loglevel", "error", "-i", silent]
        for flag in ("--pop", "--before", "--after"):
            if said[flag]:
                command += ["-i", sound_and_gain(said[flag])[0]]
        mixed = ";".join(sounds) + ";" + "".join("[s%d]" % (i + 1) for i in range(len(sounds)))
        # The sounds are added as they are, three decibels quieter together, which leaves room
        # for what packing the film's sound adds to a bang's tip. Nothing else holds the sum
        # down: a bang with a loud sound laid right on top of it would be clipped.
        mixed += "amix=inputs=%d:normalize=0:duration=longest,volume=-3dB,apad,atrim=duration=%.4f[a]" % (len(sounds), seconds)
        subprocess.run(command + ["-filter_complex", mixed, "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                                  "-ar", str(RATE), "-movflags", "+faststart", film], check=True)
        os.remove(silent)
    else:
        os.replace(silent, film)
    written.insert(0, os.path.basename(film))

    print("the film: %d x %d pixels, %d frames, %.2f seconds; the popper goes off on frame %d, %.3f seconds in"
          % (*screen, count + EMPTY, seconds, pop, pop / rate))
    print("  he fades in over the first %d frames and out over the last %d of the clip; then %d frames of the empty page"
          % (told["hold_frames"], FADE_OUT, EMPTY))
    for line in laid:
        print("  " + line)
    if not laid:
        print("  no sound")
    for name in written:
        print("  %s: %d bytes" % (name, os.path.getsize(os.path.join(out, name))))


if __name__ == "__main__":
    main()

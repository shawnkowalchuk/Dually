#!/usr/bin/env python3
# preview_run_slide.py - makes a film of the run and the slide on a phone's screen, with sound
# if it is given any, and writes what else there is to show of it. Plain Python, with Pillow
# and ffmpeg:
#
#   python3 design/mascot/scripts/preview_run_slide.py <frames> <folder to write into> [--name <name>] [--squeal <sound>] [--spin <sound>] [--trimmed <folder>] [--reuse]
#
# <frames> is what render_run_slide.py drew: its "with" frames, him and the smoke. Drawn at
# render_run_slide.py's own size the film is a phone's screen of 1080 by 2400 pixels; drawn
# smaller, for a quick look, the screen is made smaller to match. It writes:
#
#   run-slide-preview-silent.mp4   the film without sound, 24 frames a second
#   run-slide-preview-<name>.mp4   the same film with the sounds given, if --name and a sound
#                                  are given: so that several can lie side by side
#   run-slide-preview.gif          the film without sound, the part of the screen he is in and
#                                  smaller, to open anywhere. A GIF counts in hundredths of a
#                                  second: it plays 25 frames a second
#   run-slide-sheet.png            a frame from every quarter second, with its number and time
#   run-slide-sheet-brake.png      every frame round the brake and the swing
#   run-slide-lights-on-dark.png   a still of the slide, the brake lights lit, on the dark page
#   run-slide-lights-on-white.png  the same on white, if render_run_slide.py also drew that
#                                  frame with --plain (a halo is for a dark ground only)
#   run-slide-smoke-three-ways.png two frames with the smoke, without it and the smoke alone,
#                                  if render_run_slide.py drew all three; and the same on
#                                  white, if it drew those frames of all three with --plain
#
# The film is the app's dark page and nothing else on it. Every frame was drawn on the spot;
# here the picture is moved by what run-slide.json says he has gone, as the app moves the walk
# of its greeting: he comes in from beyond the left edge, the spot he runs on is the middle of
# the screen, and he slides out beyond the right edge. The app's soft shadow lies under his
# feet. The film begins LEAD frames before anything of him is on the screen and ends EMPTY
# frames after the last of him and of his smoke has gone.
#
# The sounds are files of any kind ffmpeg reads; none of them is kept in the repository.
# --squeal is the tyres' squeal of the slide. Where it begins in its file is found, not
# assumed (begins, below), and it is laid so that this lies on the frame the brake lights come
# on in (run-slide.json says which); it is faded out so that it has ended when the last of him
# has left the screen. --spin is a sound for the run on the spot: it is laid from where it
# begins in its file onto the frame he gets to the spot, and faded out as he brakes. A sound
# may be followed by a colon and how much louder it is to be, in decibels: --spin burnout.mp3:-12.
# Every edge a sound is cut at is faded over a few thousandths of a second, so that nothing
# clicks. With --trimmed and a folder, each sound is also written there as it was cut and faded
# (<its name>-trimmed.wav). --reuse keeps the silent film that is there and only lays sound on it.
#
# THE APP DOES NOT PLAY THIS. It is a preview to decide by.

import array
import json
import math
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw

# The app's dark page (MiloColors).
PAGE = (0x12, 0x13, 0x16)
WHITE = (255, 255, 255)
# A phone's screen, and the height at which render_run_slide.py draws him for it: the 150 dp
# of the app's greeting.
SCREEN = (1080, 2400)
DRAWN_FOR_SCREEN = 394
# The app's soft shadow under his feet (MascotGreeting.kt), as parts of his height: its width
# and height, how far its middle is above his soles; and how dark it is in the middle.
SHADOW = (92.0 / 150, 14.0 / 150, 3.4 / 150, 0.5)
LEAD, EMPTY = 3, 6  # frames of the empty page before he comes and after the last of it has gone
REACH = 0.62  # how far he reaches behind his axis as he slides, as a part of his height
# The sheet of every frame: from how many frames before the brake to how many after. The
# stills: how many frames after the brake; and the frame of the run on the spot that is shown
# beside it, as many frames before he stops running.
BRAKE_AROUND = (-11, 16)
STILL_AFTER, SPOT_BEFORE = 9, 7
GIF_WIDTH = 400
RATE = 48000  # the film's sound, samples a second
# A sound's edges, in seconds: how long before it begins its file is cut, and how long that
# edge is faded for; how long the squeal fades out for, and how long after the last of him has
# left the screen it has ended; how long the run on the spot's sound fades in and out for; and
# the fade at the end of any file, which may end on a click.
CUT_BEFORE, EDGE = 0.006, 0.004
SQUEAL_OUT, SQUEAL_AFTER = 0.30, 0.0
SPIN_IN, SPIN_OUT = 0.08, 0.12
FILE_END = 0.012


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


def lay(screen, picture, left, top):
    """Lays a see-through picture on the screen, also where it is partly beyond an edge."""
    box = (max(0, -left), max(0, -top), min(picture.width, screen.width - left), min(picture.height, screen.height - top))
    if box[0] < box[2] and box[1] < box[3]:
        screen.alpha_composite(picture.crop(box), (left + box[0], top + box[1]))


def heard(path):
    """A sound as numbers, one channel, RATE to the second, each from -32768 to 32767."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"],
                         capture_output=True, check=True).stdout
    return array.array("h", raw)


def loudness(samples, hop=RATE // 500):
    """How loud a sound is in every 2 thousandths of a second of it: the root of the mean square."""
    return [math.sqrt(sum(s * s for s in samples[i:i + hop]) / hop) for i in range(0, len(samples) - hop + 1, hop)], hop


def begins(samples):
    """Where a sound begins, in samples from the start of its file: the first 2 thousandths of
    a second that are a tenth as loud as its loudest (20 decibels under it) and are followed by
    a fiftieth of a second that is as loud on the whole, so that a single click before the
    sound is not taken for its beginning. Returns that, and how loud the loudest sample is, as
    a part of full."""
    loud, hop = loudness(samples)
    top = max(loud)
    if top <= 0:
        raise SystemExit("A sound is silent.")
    for i, level in enumerate(loud):
        after = loud[i:i + 10]
        if level >= top / 10 and sum(after) / len(after) >= top / 10:
            return i * hop, max(abs(s) for s in samples) / 32768.0
    return 0, max(abs(s) for s in samples) / 32768.0


def sound_and_gain(said):
    path, _, gain = said.rpartition(":")
    try:
        return (path, float(gain)) if path else (said, 0.0)
    except ValueError:
        return said, 0.0


def cut(path, out, start, length, fade_in, fade_out):
    """Writes the part of a sound that begins `start` seconds into its file and is `length`
    seconds long (or as long as the file goes on), faded in and out, as a .wav file. The file's
    own end is faded too. Returns how long what was written is."""
    whole = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                                 capture_output=True, text=True, check=True).stdout)
    length = min(length, whole - start)
    ends = min(fade_out, length / 2) if start + length < whole - 1e-3 else min(FILE_END, length / 2)
    chain = ("atrim=start=%.6f:duration=%.6f,asetpts=PTS-STARTPTS,aresample=%d,aformat=sample_fmts=s16:channel_layouts=stereo,"
             "afade=t=in:st=0:d=%.6f,afade=t=out:st=%.6f:d=%.6f" % (start, length, RATE, fade_in, length - ends, ends))
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", path, "-af", chain, out], check=True)
    return length


def main():
    asked = sys.argv[1:]
    reuse = "--reuse" in asked
    if reuse:
        asked.remove("--reuse")
    said = {"--name": "", "--squeal": "", "--spin": "", "--trimmed": ""}
    for flag in said:
        if flag in asked:
            at = asked.index(flag)
            said[flag] = asked[at + 1] if at + 1 < len(asked) else ""
            del asked[at:at + 2]
    if len(asked) != 2:
        raise SystemExit("Name the folder render_run_slide.py drew into, and a folder to write into; then, if wanted, "
                         "--name <name>, --squeal <sound>, --spin <sound>, --trimmed <folder> and --reuse.")
    drawn, out = os.path.abspath(asked[0]), os.path.abspath(asked[1])
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(drawn, "run-slide.json")) as f:
        told = json.load(f)
    count, rate, brake = told["frames"], told["frames_a_second"], told["brake_frame"]
    whole = dict(first=0, last=count - 1, every=1, turn=0)
    if told["drawn"].get("with") != whole:
        raise SystemExit("The film needs every frame of him and the smoke: draw them with render_run_slide.py first.")
    spot = next(part["first"] for part in told["parts"] if part["name"] == "SpinUp")

    small = told["pixels"] / DRAWN_FOR_SCREEN
    screen = tuple(2 * round(side * small / 2) for side in SCREEN)
    # He stands in the middle of the screen's height: half his height above the middle are his soles.
    top = round(screen[1] / 2 + told["pixels"] / 2 + told["soles"] - told["height"])
    patch = shadow(told["pixels"])
    shadow_top = round(screen[1] / 2 + told["pixels"] / 2 - SHADOW[2] * told["pixels"] - patch.height / 2)
    # What the sheets and the GIF show of the screen: the rows the picture lies in.
    rows = (max(0, top), min(screen[1], top + told["height"]))

    def picture(number, of="with"):
        return Image.open(os.path.join(drawn, of, "%04d.png" % number)).convert("RGBA")

    def axis_at(number):
        """Where his axis is across the screen in a frame: the spot he runs on is the middle."""
        return screen[0] / 2 + told["travel"][number]

    # Which frames have anything on the screen, of him or of the smoke; and when the last of
    # him has left it. That is asked of the frames without the smoke, if they were drawn: of
    # what is solid in them, which is he, and not of the halo, which is see-through. If they
    # were not drawn it is reckoned from how far he reaches behind him as he slides (REACH).
    seen, cut_off = [], []
    for number in range(count):
        left = round(axis_at(number) - told["axis"])
        box = picture(number).getchannel("A").getbbox()
        if box and left + box[2] > 0 and left + box[0] < screen[0]:
            seen.append(number)
            if left > 0 and box[0] == 0:
                cut_off.append(number)
    first, last = seen[0], seen[-1]
    alone_drawn = told["drawn"].get("without") == whole
    gone = None
    for number in range(brake, count):
        left = round(axis_at(number) - told["axis"])
        if alone_drawn:
            box = picture(number, "without").getchannel("A").point(lambda a: 255 if a >= 250 else 0).getbbox()
        else:
            box = (told["axis"] - round(REACH * told["pixels"]), 0, 0, 0)
        if box is None or left + box[0] >= screen[0]:
            gone = number
            break
    if gone is None:
        raise SystemExit("He has not left the screen by the last frame: the slide is too short for this screen.")
    shown = list(range(first, last + 1))
    frames = LEAD + len(shown) + EMPTY
    at = {number: LEAD + i for i, number in enumerate(shown)}  # the film's frame for a frame of the whole
    if spot not in at or first == 0:
        raise SystemExit("He is already on the screen in the first frame that was drawn: the run in is too short for this screen.")

    def frame_of(index, of="with", page=PAGE):
        """A frame of the film: the page, and over it the shadow and the picture where he is."""
        made = Image.new("RGBA", screen, page + (255,))
        if LEAD <= index < LEAD + len(shown):
            number = shown[index - LEAD]
            x = axis_at(number)
            if not of.startswith("alone"):  # his shadow is his: the smoke alone has none
                lay(made, patch, round(x - patch.width / 2), shadow_top)
            lay(made, picture(number, of), round(x - told["axis"]), top)
        return made.convert("RGB")

    def strip(image):
        return image.crop((0, rows[0], screen[0], rows[1]))

    silent = os.path.join(out, "run-slide-preview-silent.mp4")
    seconds = frames / rate
    written = []
    if not (reuse and os.path.isfile(silent)):
        encoder = subprocess.Popen(
            ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % screen, "-r", str(rate),
             "-i", "-", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "17", "-preset", "slow", "-movflags", "+faststart", silent],
            stdin=subprocess.PIPE)
        quarter = rate // 4
        tile = (round(360 * small), round(360 * small * (rows[1] - rows[0]) / screen[0]))
        sheets = {"": [], "-brake": []}
        for index in range(frames):
            made = frame_of(index)
            encoder.stdin.write(made.tobytes())
            number = shown[index - LEAD] if LEAD <= index < LEAD + len(shown) else None
            small_one = strip(made).resize(tile, Image.LANCZOS)
            if index % quarter == 0:
                sheets[""].append((index, number, small_one))
            if number is not None and brake + BRAKE_AROUND[0] <= number <= brake + BRAKE_AROUND[1]:
                sheets["-brake"].append((index, number, small_one))
            if number == brake + STILL_AFTER:
                strip(made).save(os.path.join(out, "run-slide-lights-on-dark.png"))
                written.append("run-slide-lights-on-dark.png")
        encoder.stdin.close()
        if encoder.wait() != 0:
            raise SystemExit("ffmpeg could not write " + silent)
        written.insert(0, os.path.basename(silent))

        names = {part["first"]: part["name"] for part in told["parts"]}
        for ending, tiles in sheets.items():
            columns = 6 if ending else 5
            sheet = Image.new("RGB", (columns * tile[0], math.ceil(len(tiles) / columns) * tile[1]), PAGE)
            pen = ImageDraw.Draw(sheet)
            for i, (index, number, shown_one) in enumerate(tiles):
                x, y = (i % columns) * tile[0], (i // columns) * tile[1]
                sheet.paste(shown_one, (x, y))
                pen.rectangle((x, y, x + tile[0] - 1, y + tile[1] - 1), outline=(70, 70, 70))
                what = "" if number is None else "   " + next(name for begun, name in sorted(names.items(), reverse=True) if begun <= number)
                pen.text((x + 6, y + 5), "%d   %.2f s%s%s" % (index, index / rate, what, "   BRAKE LIGHTS ON" if number == brake else ""),
                         fill=(255, 255, 255))
            sheet.save(os.path.join(out, "run-slide-sheet%s.png" % ending))
            written.append("run-slide-sheet%s.png" % ending)

        look = "crop=%d:%d:0:%d,scale=%d:-2:flags=lanczos" % (screen[0], rows[1] - rows[0], rows[0], min(GIF_WIDTH, screen[0]))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", silent, "-filter_complex",
                        "[0:v]%s,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4,setpts=N/25/TB" % look,
                        "-r", "25", os.path.join(out, "run-slide-preview.gif")], check=True)
        written.append("run-slide-preview.gif")

        # The stills that need more than the film's own frames.
        def has(of, number):
            return os.path.isfile(os.path.join(drawn, of, "%04d.png" % number))

        still, on_spot = brake + STILL_AFTER, next(p["first"] for p in told["parts"] if p["name"] == "BrakeSlide") - SPOT_BEFORE
        if has("with-plain", still):
            strip(frame_of(at[still], "with-plain", WHITE)).save(os.path.join(out, "run-slide-lights-on-white.png"))
            written.append("run-slide-lights-on-white.png")
        ways = (("with", "with the smoke"), ("without", "without it"), ("alone", "the smoke alone"))
        if all(has(of, number) for of, _ in ways for number in (on_spot, still)):
            grounds = [("", PAGE)]
            if all(has(of + "-plain", number) for of, _ in ways for number in (on_spot, still)):
                grounds.append(("-plain", WHITE))
            cell = (round(540 * small), round(540 * small * (rows[1] - rows[0]) / screen[0]))
            three = Image.new("RGB", (3 * cell[0], 2 * len(grounds) * cell[1]), PAGE)
            pen = ImageDraw.Draw(three)
            for g, (ending, page) in enumerate(grounds):
                for r, number in enumerate((on_spot, still)):
                    for c, (of, what) in enumerate(ways):
                        x, y = c * cell[0], (2 * g + r) * cell[1]
                        three.paste(strip(frame_of(at[number], of + ending, page)).resize(cell, Image.LANCZOS), (x, y))
                        pen.rectangle((x, y, x + cell[0] - 1, y + cell[1] - 1), outline=(110, 110, 110))
                        pen.text((x + 8, y + 6), "frame %d   %s" % (at[number], what), fill=(255, 255, 255) if page == PAGE else (0, 0, 0))
            three.save(os.path.join(out, "run-slide-smoke-three-ways.png"))
            written.append("run-slide-smoke-three-ways.png")

    # The sounds: each is cut and faded into a file of its own, and that is laid into the film.
    brake_at, gone_at, spot_at = at[brake] / rate, (at[gone] if gone in at else frames - EMPTY) / rate, at[spot] / rate
    laid, sounds = [], []
    keep = os.path.abspath(said["--trimmed"]) if said["--trimmed"] else tempfile.mkdtemp()
    os.makedirs(keep, exist_ok=True)
    for flag, starts, ends, fade_in, fade_out, what in (
            ("--squeal", brake_at, gone_at + SQUEAL_AFTER, EDGE, SQUEAL_OUT, "the squeal"),
            ("--spin", spot_at, brake_at + SPIN_OUT / 2, SPIN_IN, SPIN_OUT, "the run on the spot")):
        if not said[flag]:
            continue
        path, gain = sound_and_gain(said[flag])
        begun, top = begins(heard(path))
        before = min(CUT_BEFORE, begun / RATE)
        trimmed = os.path.join(keep, os.path.splitext(os.path.basename(path))[0] + "-trimmed.wav")
        length = cut(path, trimmed, begun / RATE - before, ends - starts + before, fade_in, fade_out)
        sounds.append((trimmed, starts - before, gain))
        laid.append("%s: %s (loudest sample %.0f %% of full) begins %.3f s into its file; that is laid on %.3f s of the film, frame %d; "
                    "it is %.3f s long as cut and has ended by %.3f s%s"
                    % (what, os.path.basename(path), 100 * top, begun / RATE, starts, round(starts * rate), length,
                       starts - before + length, ", %+g dB" % gain if gain else ""))
    if sounds and said["--name"]:
        film = os.path.join(out, "run-slide-preview-%s.mp4" % said["--name"])
        command = ["ffmpeg", "-y", "-loglevel", "error", "-i", silent]
        graph = []
        for i, (path, starts, gain) in enumerate(sounds, 1):
            command += ["-i", path]
            graph.append("[%d:a]adelay=delays=%dS:all=1,volume=%.2fdB[s%d]" % (i, round(starts * RATE), gain, i))
        # The sounds are added as they are, three decibels quieter together, which leaves room
        # for what packing the film's sound adds to a peak.
        graph.append("".join("[s%d]" % i for i in range(1, len(sounds) + 1))
                     + "amix=inputs=%d:normalize=0:duration=longest,volume=-3dB,apad,atrim=duration=%.4f[a]" % (len(sounds), seconds))
        subprocess.run(command + ["-filter_complex", ";".join(graph), "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac",
                                  "-b:a", "192k", "-ar", str(RATE), "-movflags", "+faststart", film], check=True)
        written.insert(0, os.path.basename(film))
    elif sounds:
        print("  (a film with sound is written only with --name)")

    print("the film: %d x %d pixels, %d frames, %.2f seconds; frames %d to %d of the whole, after %d frames of the empty page"
          % (*screen, frames, seconds, first, last, LEAD))
    print("  he gets to the spot in frame %d, %.3f s in; the brake lights come on in frame %d, %.3f s in; "
          "the last of him has left the screen in frame %d, %.3f s in; the last smoke goes in frame %d"
          % (at[spot], spot_at, at[brake], brake_at, round(gone_at * rate), gone_at, at[last]))
    if cut_off:
        print("  SMOKE IS CUT OFF at the picture's left edge while that edge is on the screen, in %d frames from frame %d of the "
              "whole: make ROOM in render_run_slide.py wider to the left" % (len(cut_off), cut_off[0]))
    for line in laid:
        print("  " + line)
    if not laid:
        print("  no sound")
    for name in written:
        print("  %s: %d bytes" % (name, os.path.getsize(os.path.join(out, name))))


if __name__ == "__main__":
    main()

# pack_greeting.py - what the scripts that pack the mascot's pictures share: where the app's
# pictures go, how long a frame is shown, how a tool is run, and how a folder of frames becomes
# one animated picture. Plain Python, not run in Blender. It packs nothing by itself.
#
# UNTIL 2026-10-11 IT PACKED THE WAVE'S TWO PICTURES, mascot_walk.webp and
# mascot_turn_and_wave.webp, from the frames render_greeting.py drew: the wave in which the
# mascot came in from the right and faded away. That wave is no longer in the app. The wave it
# has now is packed by pack_wave2.py, the moonwalk by pack_moonwalk.py and the party by
# pack_party.py, and each of them reads this file for its functions, as pack_clips.py does.
#
# It needs cwebp and webpmux (Homebrew: "brew install webp"). Each frame of an animated picture
# is packed by cwebp and the frames are joined by webpmux; img2webp would do both in one step,
# but it cannot be told to store the see-through channel more coarsely, and that channel is two
# fifths of every frame.

import os
import subprocess
import tempfile


def here():
    """This script's folder."""
    return os.path.dirname(os.path.abspath(__file__))


APP_PICTURES = os.path.normpath(os.path.join(here(), "..", "..", "..", "app", "src", "main", "res", "drawable-nodpi"))
# 24 frames a second, in whole milliseconds, which is all the file can say.
FRAME_MS = 42
# Colour at 60 of 100 and the see-through channel at 50, the lowest at which the edges, the M and
# the dials looked like the frames on a phone. -sharp_yuv keeps the green from bleeding into the
# white gloves; without -exact, cwebp gives the see-through pixels beside an edge that edge's
# colour, so no dark or light fringe is drawn over the app's dark page or its lime tile.
CWEBP = ["-q", "60", "-alpha_q", "50", "-sharp_yuv", "-af", "-m", "6"]


def frames_in(folder):
    frames = sorted(name for name in os.listdir(folder) if name.endswith(".png"))
    if not frames:
        raise SystemExit("No frames in %s" % folder)
    return frames


def run(command):
    """Runs a tool. What it says is shown only if it fails."""
    done = subprocess.run(command, capture_output=True, text=True)
    if done.returncode != 0:
        raise SystemExit("%s failed:\n%s" % (command[0], done.stderr))


def pack_animated(folder, out, plays=1, shown=FRAME_MS, settings=CWEBP):
    """One animated picture out of a folder of frames, each shown for FRAME_MS, played once.
    Every frame is the whole picture and replaces the one before it, so none depends on another.
    pack_clips.py packs its pictures with it and may ask for another number of plays (0 is for
    ever), another time for a frame, or other settings."""
    frames = frames_in(folder)
    with tempfile.TemporaryDirectory() as packed:
        join = ["webpmux"]
        for name in frames:
            frame = os.path.join(packed, name[:-4] + ".webp")
            run(["cwebp", "-quiet", *settings, os.path.join(folder, name), "-o", frame])
            join += ["-frame", frame, "+%d+0+0+0-b" % shown]
        # "-loop 1" is once; 0 is for ever.
        run(join + ["-loop", str(plays), "-bgcolor", "0,0,0,0", "-o", out])
    return "%d frames of %d ms, %s" % (len(frames), shown, "plays once" if plays == 1 else "starts again for ever")

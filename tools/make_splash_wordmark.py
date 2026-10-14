#!/usr/bin/env python3
"""The words "Trip Tracker" under the face on Android's opening screen.

    python3 tools/make_splash_wordmark.py

Android's opening screen (the splash screen) shows the app's icon in the middle and, if the app
gives one, a "branding image" near the foot of the screen, in a box of 200 by 80 dp. Shawn
asked for the face there "with the text under it Trip Tracker" (2026-10-13). A vector drawing
cannot hold letters, so the words are drawn as pictures, one for each screen density, in the
brand's headline type: Archivo at weight 800 and width 112, in Bone on the page's dark.

Google Chrome draws them, because it sets the typeface's kerning ("T" and "r" tucked together)
where Pillow without its text-layout library does not; Pillow then makes the smaller sizes from
the largest. Hand-run, like tools/make_site_pictures.py: not part of the build. It needs Google
Chrome in /Applications and Pillow.
"""

import subprocess
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
FONT = RES / "font/archivo.ttf"
NAME = "splash_wordmark.png"
WORDS = "Trip Tracker"
BONE = "#FFF4E8"
# The box Android gives the branding image, in dp, and the words' size in it.
BOX = (200, 80)
SIZE = 26
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
DENSITIES = (("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0))


def draw_largest(folder):
    page = folder / "words.html"
    page.write_text(f"""<!doctype html><meta charset="utf-8"><style>
@font-face {{ font-family: Archivo; src: url("{FONT.as_uri()}"); font-weight: 100 900; font-stretch: 62% 125%; }}
html, body {{ margin: 0; background: transparent; }}
div {{ width: {BOX[0]}px; height: {BOX[1]}px; display: flex; align-items: center; justify-content: center;
      font: 800 {SIZE}px Archivo; font-stretch: 112%; letter-spacing: -0.01em; color: {BONE};
      font-kerning: normal; white-space: nowrap; }}
</style><div>{WORDS}</div>""")
    shot = folder / "words.png"
    subprocess.run(
        [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=4",
         "--default-background-color=00000000", f"--window-size={BOX[0]},{BOX[1]}",
         f"--screenshot={shot}", page.as_uri()],
        check=True, capture_output=True,
    )
    return Image.open(shot).convert("RGBA")


def main():
    with tempfile.TemporaryDirectory() as made:
        largest = draw_largest(Path(made))
    width, height = BOX[0] * 4, BOX[1] * 4
    if largest.size != (width, height):
        raise SystemExit(f"Chrome drew {largest.size}, not {(width, height)}")
    if largest.getpixel((0, 0))[3] != 0:
        raise SystemExit("The background is not see-through: this Chrome ignored --default-background-color.")
    for bucket, scale in DENSITIES:
        folder = RES / f"drawable-{bucket}"
        folder.mkdir(exist_ok=True)
        size = (round(BOX[0] * scale), round(BOX[1] * scale))
        picture = largest if scale == 4.0 else largest.resize(size, Image.LANCZOS)
        picture.save(folder / NAME, optimize=True)
        print(f"drawable-{bucket}/{NAME}: {size[0]} x {size[1]}, {(folder / NAME).stat().st_size} bytes")


if __name__ == "__main__":
    main()

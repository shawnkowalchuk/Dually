#!/usr/bin/env python3
"""Draws the website's two pictures that carry the mark (docs/APP_ENCYCLOPEDIA.md, "Website"):

    website/icon-512.png   the mark on a full square of 512 by 512, for the Google Play
                           listing, which rounds the corners itself
    website/og-image.png   the picture a shared link shows, 1,200 by 630

The mark is a dark D on Hi-Vis Orange, the owner's design of 2026-10-13. Its shape, its two
colours and the corner of its square are the ones in website/mark.svg, and the picture's
colours are the names in website/styles.css: change them together. The words are set in the
website's own typefaces, read from website/fonts/.

Run by hand after a change to the mark, the colours, the typefaces or the words:

    python3 tools/make_site_pictures.py            # writes both into website/
    python3 tools/make_site_pictures.py <folder>   # writes both there instead, to look first

It needs Pillow (python3 -m pip install Pillow), which nothing else in this repository needs:
it is no part of the build and CI never runs it. Each picture is drawn at three times its
size and scaled down, so that its edges are clean.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "website"
FONTS = SITE / "fonts"

# The names in website/styles.css.
PAGE = "#15120F"
AMBER = "#FFB648"
INK = "#1A1410"
ACCENT = "#FF5A12"
# The mark's own dark, as in website/mark.svg.
MARK_DARK = "#1E1A17"

# The mark as website/mark.svg has it: a square of 150 with a corner of 34, and the D of the
# path "M-36 -38H0A36 38 0 0 1 0 38H-36Z M-18 -20H0A18 20 0 0 1 0 20H-18Z" at 1.1 times its
# size about the middle.
MARK_SIDE = 150
MARK_CORNER = 34
MARK_SCALE = 1.1
D_OUTER = (36, 38)
D_INNER = (18, 20)

OVERSAMPLE = 3

HEADLINE = ("Fire up the truck.", "Dually's already", "counting.")
UNDER_IT = ("Free for Android. Starts a trip when", "your phone connects to the truck.")
ADDRESS = "dually.top"


def half_round(draw, cx, cy, half_width, half_height, fill):
    """One outline of the D: a box to the left of the middle and half an ellipse to its right."""
    draw.rectangle((cx - half_width, cy - half_height, cx, cy + half_height), fill=fill)
    draw.ellipse(
        (cx - half_width, cy - half_height, cx + half_width, cy + half_height), fill=fill
    )


def draw_mark(image, left, top, side, rounded):
    draw = ImageDraw.Draw(image)
    corner = side * MARK_CORNER / MARK_SIDE if rounded else 0
    draw.rounded_rectangle((left, top, left + side, top + side), radius=corner, fill=ACCENT)
    unit = side * MARK_SCALE / MARK_SIDE
    cx, cy = left + side / 2, top + side / 2
    half_round(draw, cx, cy, D_OUTER[0] * unit, D_OUTER[1] * unit, MARK_DARK)
    half_round(draw, cx, cy, D_INNER[0] * unit, D_INNER[1] * unit, ACCENT)


def archivo(size, weight, width):
    font = ImageFont.truetype(str(FONTS / "archivo.ttf"), size)
    # The file's axes are its weight, then its width.
    font.set_variation_by_axes([weight, width])
    return font


def mono(size):
    return ImageFont.truetype(str(FONTS / "ibm-plex-mono-semibold.ttf"), size)


def write(draw, left, baseline, text, font, fill, spacing=0.0):
    """Sets a line letter by letter, `spacing` of the font's size apart, and says how wide
    it came out. Pillow sets a whole line without the font's own pair spacing here, so
    nothing is lost by going letter by letter, and the letters' distance can be chosen."""
    x = left
    for letter in text:
        draw.text((x, baseline), letter, font=font, fill=fill, anchor="ls")
        x += font.getlength(letter) + spacing * font.size
    return x - left - spacing * font.size


def icon():
    side = 512 * OVERSAMPLE
    image = Image.new("RGB", (side, side), ACCENT)
    draw_mark(image, 0, 0, side, rounded=False)
    return image.resize((512, 512), Image.LANCZOS)


def link_picture():
    k = OVERSAMPLE
    image = Image.new("RGB", (1200 * k, 630 * k), PAGE)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((40 * k, 40 * k, 1160 * k, 590 * k), radius=36 * k, fill=AMBER)

    # The top line, as on the site: the mark, the wordmark, and what the app is.
    draw_mark(image, 96 * k, 88 * k, 64 * k, rounded=True)
    wordmark = archivo(42 * k, 800, 125)
    end = 176 * k + write(draw, 176 * k, 135 * k, "Dually", wordmark, INK, -0.01)
    label = mono(17 * k)
    label_left = end + 22 * k
    label_width = write(draw, label_left + 14 * k, 128 * k, "TRIP TRACKER", label, INK, 0.12)
    draw.rounded_rectangle(
        (label_left, 102 * k, label_left + label_width + 28 * k, 138 * k),
        radius=18 * k,
        outline=INK,
        width=2 * k,
    )

    headline = archivo(70 * k, 800, 112)
    for row, line in enumerate(HEADLINE):
        write(draw, 96 * k, (250 + row * 74) * k, line, headline, INK, -0.02)

    under = archivo(28 * k, 400, 100)
    for row, line in enumerate(UNDER_IT):
        write(draw, 96 * k, (456 + row * 40) * k, line, under, INK)

    write(draw, 96 * k, 552 * k, ADDRESS, mono(22 * k), INK, 0.04)

    # The mark again, large, at the right.
    draw_mark(image, 792 * k, 171 * k, 288 * k, rounded=True)
    return image.resize((1200, 630), Image.LANCZOS)


def main(argv):
    if len(argv) > 2:
        print("Usage: python3 tools/make_site_pictures.py [folder]", file=sys.stderr)
        return 2
    folder = Path(argv[1]) if len(argv) == 2 else SITE
    folder.mkdir(parents=True, exist_ok=True)
    for name, picture in (("icon-512.png", icon()), ("og-image.png", link_picture())):
        picture.save(folder / name, optimize=True)
        print(f"Wrote {folder / name}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

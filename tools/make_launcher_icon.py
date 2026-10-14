#!/usr/bin/env python3
"""The launcher icon's face, written from the brand design's numbers.

    python3 tools/make_launcher_icon.py            writes app/src/main/res/drawable/ic_launcher_foreground.xml
    python3 tools/make_launcher_icon.py --check    fails if that file is not what this script writes
    python3 tools/make_launcher_icon.py --svg F    also writes the same drawing as an SVG, to look at

The icon is the design's "Icon A, Face" (Shawn, 2026-10-13: "what about using this for the app
icon"): the mascot's two dials for eyes and his grin, on the mark's orange. The design draws it
in a square of 150; a launcher shows the middle 72 of the icon's 108, so one unit of the design
is 0.48 here and the design's square is exactly what a launcher with a square shape shows.
Everything of the face lies within 28 of the icon's middle, inside the 33 that every launcher
shape leaves uncut.

Android's vector drawings have no dashed strokes, which the design draws its dials' marks with,
so each mark is written here as a line of its own. The mouth is the letter of the app's mark
(the D's outer outline), turned a quarter turn, as the mascot's grin is.

Hand-run, like tools/make_site_pictures.py: not part of the build. It needs nothing but Python.
"""

import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"

# The design's square, and where it lies on the icon's canvas of 108.
DESIGN = 150.0
SHOWN = 72.0
SCALE = SHOWN / DESIGN
OFFSET = (108.0 - SHOWN) / 2

# The scale of a dial runs three quarters of the way round, from lower left to lower right.
SCALE_STARTS, SCALE_SWEEPS = 135.0, 270.0

# centre, bezel, face, marks' radius, marks' length, mark's width in degrees, how many marks,
# red zone (radius, width, degrees from the scale's end) or None, needle (angle, tail, tip,
# width), hub
DIALS = (
    dict(c=(56.0, 56.0), bezel=31.0, face=26.0, marks_r=20.0, marks_len=5.0, mark_deg=2.4, marks=10,
         red=(22.5, 2.0, 45.0), needle=(225.0, 4.0, 18.0, 3.0), hub=3.5),
    dict(c=(102.0, 64.0), bezel=20.0, face=16.0, marks_r=12.0, marks_len=3.5, mark_deg=2.4, marks=9,
         red=None, needle=(300.0, 3.0, 11.0, 2.2), hub=2.5),
)

# The mouth: the mark's letter, its outer outline only, in the letter's own units (72 by 76),
# half size, turned a quarter turn clockwise so that its flat side is on top.
LETTER_OUTLINE = "M-36,-38H0A36,38 0 0 1 0,38H-36Z"
MOUTH_AT, MOUTH_SCALE, MOUTH_TURN = (78.0, 112.0), 0.5, 90
MOUTH_LINE = 5.0
TONGUE = dict(cx=18.0, cy=0.0, rx=13.0, ry=21.0)
TEETH = dict(x=-33.0, y=-30.0, w=9.0, h=60.0, r=3.0)


def n(value):
    """A number as the file writes it: at most two decimals, no trailing noughts."""
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def at(centre, radius, degrees):
    """A point on a circle; degrees run clockwise from three o'clock, as the design's do."""
    angle = math.radians(degrees)
    return centre[0] + radius * math.cos(angle), centre[1] + radius * math.sin(angle)


def circle(centre, radius):
    x, y = centre
    return f"M{n(x - radius)},{n(y)}a{n(radius)},{n(radius)} 0 1 0 {n(2 * radius)},0a{n(radius)},{n(radius)} 0 1 0 {n(-2 * radius)},0Z"


def ellipse(cx, cy, rx, ry):
    return f"M{n(cx - rx)},{n(cy)}a{n(rx)},{n(ry)} 0 1 0 {n(2 * rx)},0a{n(rx)},{n(ry)} 0 1 0 {n(-2 * rx)},0Z"


def rounded(x, y, w, h, r):
    return (f"M{n(x + r)},{n(y)}h{n(w - 2 * r)}a{n(r)},{n(r)} 0 0 1 {n(r)},{n(r)}v{n(h - 2 * r)}"
            f"a{n(r)},{n(r)} 0 0 1 {n(-r)},{n(r)}h{n(-(w - 2 * r))}a{n(r)},{n(r)} 0 0 1 {n(-r)},{n(-r)}"
            f"v{n(-(h - 2 * r))}a{n(r)},{n(r)} 0 0 1 {n(r)},{n(-r)}Z")


def shapes():
    """Every shape of the face, in the order it is drawn: (what, colour, path, stroke or None).
    A stroke is (width, round ends)."""
    drawn = []
    for dial in DIALS:
        c = dial["c"]
        drawn.append(("fill", "bezel", circle(c, dial["bezel"]), None))
        drawn.append(("fill", "dial", circle(c, dial["face"]), None))
        inner, outer = dial["marks_r"] - dial["marks_len"] / 2, dial["marks_r"] + dial["marks_len"] / 2
        width = 2 * math.pi * dial["marks_r"] * dial["mark_deg"] / 360
        step = SCALE_SWEEPS / (dial["marks"] - 1)
        marks = ""
        for index in range(dial["marks"]):
            # The design's dash begins at the angle and runs on for the mark's width.
            degrees = SCALE_STARTS + index * step + dial["mark_deg"] / 2
            (x0, y0), (x1, y1) = at(c, inner, degrees), at(c, outer, degrees)
            marks += f"M{n(x0)},{n(y0)}L{n(x1)},{n(y1)}"
        drawn.append(("line", "mark", marks, (width, False)))
        if dial["red"]:
            radius, wide, sweep = dial["red"]
            end = SCALE_STARTS + SCALE_SWEEPS
            (x0, y0), (x1, y1) = at(c, radius, end - sweep), at(c, radius, end)
            drawn.append(("line", "redline", f"M{n(x0)},{n(y0)}A{n(radius)},{n(radius)} 0 0 1 {n(x1)},{n(y1)}", (wide, False)))
        degrees, tail, tip, wide = dial["needle"]
        (x0, y0), (x1, y1) = at(c, -tail, degrees), at(c, tip, degrees)
        drawn.append(("line", "needle", f"M{n(x0)},{n(y0)}L{n(x1)},{n(y1)}", (wide, True)))
        drawn.append(("fill", "bezel", circle(c, dial["hub"]), None))
    return drawn


def mouth():
    """The mouth's shapes in the letter's own units: (what, colour, path, stroke or None)."""
    return [
        ("both", "mouth", LETTER_OUTLINE, (MOUTH_LINE, False)),
        ("fill", "tongue", ellipse(**TONGUE), None),
        ("fill", "teeth", rounded(TEETH["x"], TEETH["y"], TEETH["w"], TEETH["h"], TEETH["r"]), None),
    ]


COLOURS = {
    "bezel": ("milo_icon_bezel", "#E6E2DE"),
    "dial": ("milo_icon_dial", "#141210"),
    "mark": ("milo_icon_dial_mark", "#FFFFFF"),
    "redline": ("milo_icon_redline", "#FF3D2E"),
    "needle": ("milo_icon_needle", "#FF4A1C"),
    "mouth": ("milo_icon_mouth", "#2A1712"),
    "mouth_line": ("milo_mark_letter", "#1E1A17"),
    "tongue": ("milo_icon_tongue", "#F2546B"),
    "teeth": ("milo_icon_teeth", "#FFFFFF"),
}


def android_path(what, colour, path, stroke, indent):
    lines = [f"{indent}<path"]
    if what in ("fill", "both"):
        lines.append(f'{indent}    android:fillColor="@color/{COLOURS[colour][0]}"')
    if what in ("line", "both"):
        width, round_ends = stroke
        line_colour = "mouth_line" if what == "both" else colour
        lines.append(f'{indent}    android:strokeColor="@color/{COLOURS[line_colour][0]}"')
        lines.append(f'{indent}    android:strokeWidth="{n(width)}"')
        if round_ends:
            lines.append(f'{indent}    android:strokeLineCap="round"')
        if what == "both":
            lines.append(f'{indent}    android:strokeLineJoin="round"')
    lines.append(f'{indent}    android:pathData="{path}" />')
    return "\n".join(lines)


def android():
    head = '''<?xml version="1.0" encoding="utf-8"?>
<!--
    The face on the app's icon (since 2026-10-13, ADR-010): the brand design's "Icon A, Face",
    the mascot's two dials and his grin. WRITTEN BY tools/make_launcher_icon.py, which holds the
    design's numbers and says how they come here: change it there and run it, not here.

    The design's square of 150 is the middle 72 of this canvas of 108, which is what a launcher
    with a square shape shows; all of the face lies inside the central 66 that every launcher
    shape leaves uncut. The mouth is the letter of the app's mark, its outer outline, turned a
    quarter turn (LauncherIconTest holds it to the letter's path). The colours are in
    res/values/colors.xml, because a launcher draws this outside the app.

    The layer for "themed icons" is not this drawing but the letter alone
    (ic_launcher_monochrome.xml): one colour cannot show a face.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="{scale}"
        android:scaleY="{scale}"
        android:translateX="{offset}"
        android:translateY="{offset}">
'''.format(scale=n(SCALE), offset=n(OFFSET))
    body = "\n".join(android_path(*shape, indent="        ") for shape in shapes())
    grin = '''
        <group
            android:rotation="{turn}"
            android:scaleX="{scale}"
            android:scaleY="{scale}"
            android:translateX="{x}"
            android:translateY="{y}">
'''.format(turn=MOUTH_TURN, scale=n(MOUTH_SCALE), x=n(MOUTH_AT[0]), y=n(MOUTH_AT[1]))
    grin += "\n".join(android_path(*shape, indent="            ") for shape in mouth())
    return head + body + grin + "\n        </group>\n    </group>\n</vector>\n"


def svg_path(what, colour, path, stroke):
    parts = [f'd="{path}"']
    parts.append(f'fill="{COLOURS[colour][1]}"' if what in ("fill", "both") else 'fill="none"')
    if what in ("line", "both"):
        width, round_ends = stroke
        line_colour = "mouth_line" if what == "both" else colour
        parts.append(f'stroke="{COLOURS[line_colour][1]}" stroke-width="{n(width)}"')
        if round_ends:
            parts.append('stroke-linecap="round"')
        if what == "both":
            parts.append('stroke-linejoin="round"')
    return "<path " + " ".join(parts) + "/>"


def svg():
    """The whole icon, ground and face, on its canvas of 108, as an SVG to look at."""
    face = "".join(svg_path(*shape) for shape in shapes())
    grin = "".join(svg_path(*shape) for shape in mouth())
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">'
        '<rect width="108" height="108" fill="#FF5A12"/>'
        f'<g transform="translate({n(OFFSET)} {n(OFFSET)}) scale({n(SCALE)})">{face}'
        f'<g transform="translate({n(MOUTH_AT[0])} {n(MOUTH_AT[1])}) rotate({MOUTH_TURN}) scale({n(MOUTH_SCALE)})">{grin}</g>'
        "</g></svg>\n"
    )


def main():
    wanted = android()
    if "--svg" in sys.argv:
        Path(sys.argv[sys.argv.index("--svg") + 1]).write_text(svg())
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text() != wanted:
            sys.exit(f"{OUT.relative_to(ROOT)} is not what tools/make_launcher_icon.py writes: run the script.")
        print("The launcher icon's face is what the script writes.")
        return
    OUT.write_text(wanted)
    print(f"Wrote {OUT.relative_to(ROOT)}.")


if __name__ == "__main__":
    main()

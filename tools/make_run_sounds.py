#!/usr/bin/env python3
"""Synthesises the two sounds of the mascot's run across the Report screen
(docs/APP_ENCYCLOPEDIA.md, "The run after a report is sent"):

- res/raw/report_run_whirr.wav: wheels whirring up, for the two seconds he runs on the spot. A
  low buzz that climbs, with rubber hissing over it.
- res/raw/report_run_squeal.wav: a car going sideways, for the slide. Two high notes that beat
  against each other and slide down, rough, with hiss under them.

They are made from nothing but arithmetic: sine waves, and a noise that is counted out, not
drawn by chance. They are not copied from, sampled from or derived from any recording, and no
sound generator made them.

THEY ARE THE SOUNDS OF THE FILM SHAWN APPROVED on 2026-10-11, the third of three he was shown
with sounds made this way, and cut as that film cut them (design/mascot/scripts/
preview_run_slide.py): the whirr from where it begins in what whirr() makes, faded in; each
faded over its last hundredth of a second; the whirr ten decibels under the squeal.

The output is committed, so the build does not run this script. Run it again only to change a
sound:

    python3 tools/make_run_sounds.py

It needs nothing but the Python 3 standard library, and it produces the same bytes every time,
so an unchanged script gives unchanged files. The three other built-in sounds are made by
make_trip_start_chirp.py and make_party_pop.py, which this script does not touch.
"""

import math
import struct
import wave
from pathlib import Path

RAW = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"

# As the party's pop: these are noise and high notes, and most of a squeal lies above what the
# chirp's rate can hold.
SAMPLE_RATE = 44_100

# The loudest sample of each sound as it is first made, of full scale. The film's sounds were
# made at this, and cut and laid at the levels below.
MADE_AT = 0.8

# How loud the squeal is, of what was made: what the film had in each of its two channels. Its
# loudest sample is then at 40 % of full scale, under the 55 % of the chirp, the chime and the
# pop: it is a held note, not a click, and is played at the volume the phone gives its own
# screen sounds. TO MAKE BOTH LOUDER OR QUIETER ON THE PHONE, CHANGE THIS ONE NUMBER.
LEVEL = 0.5

# The whirr lies this many decibels under the squeal, as in the film.
WHIRR_UNDER_DB = 10.0

# Where the film cut the whirr: it found the sound to begin 20 thousandths of a second into
# what whirr() makes (the first moment within 20 decibels of its loudest), and kept 6 before
# that. The squeal begins at once and is kept whole.
WHIRR_FROM_SECONDS = 0.014

# The film's fades, in seconds: in, for the squeal and for the whirr; and out, at the end.
SQUEAL_IN, WHIRR_IN, OUT = 0.004, 0.08, 0.012


class Noise:
    """White noise from a counted sequence, so that every run gives the same sound."""

    def __init__(self, seed):
        self.state = seed

    def next(self):
        self.state = (1_664_525 * self.state + 1_013_904_223) % 4_294_967_296
        return self.state / 2_147_483_648 - 1.0


class Wander:
    """Noise with its top taken off: a value that drifts about nought, for a pitch that is
    not steady."""

    def __init__(self, seed, hz):
        self.noise = Noise(seed)
        self.k = 1 - math.exp(-2 * math.pi * hz / SAMPLE_RATE)
        self.value = 0.0
        # A slow drift is small: bring it back to about one in size.
        self.gain = 1 / math.sqrt(self.k / (2 - self.k))

    def next(self):
        self.value += self.k * (self.noise.next() - self.value)
        return self.value * self.gain * 0.5


def shape(index, count, attack, release):
    """Nought to one over `attack` seconds, one to nought over the last `release`."""
    time = index / SAMPLE_RATE
    left = (count - index) / SAMPLE_RATE
    value = 1.0
    if time < attack:
        value *= 0.5 - 0.5 * math.cos(math.pi * time / attack)
    if left < release:
        value *= 0.5 - 0.5 * math.cos(math.pi * left / release)
    return value


def squeal():
    """Rubber going sideways: two notes that beat against each other and a third above them,
    sliding down as he slows. Their pitch strays and their loudness shakes (stick and slip),
    and plain noise between about 2 and 6 kHz lies under them."""
    seconds, release = 1.25, 0.35
    voices = [(940, 800, 1.0), (1_010, 845, 0.9), (1_480, 1_190, 0.35)]  # from, to, how loud
    jitter, rough, hiss = 0.05, 0.7, 0.35
    count = int(seconds * SAMPLE_RATE)
    stray = Wander(301, 45.0)
    shake = Wander(302, 70.0)
    noise = Noise(303)
    low = 0.0
    slow = 0.0
    phases = [0.0] * len(voices)
    out = []
    for index in range(count):
        position = index / count
        wobble = 1.0 + jitter * stray.next()
        value = 0.0
        for number, (start_hz, end_hz, level) in enumerate(voices):
            frequency = start_hz * (end_hz / start_hz) ** position * wobble
            phases[number] += 2 * math.pi * frequency / SAMPLE_RATE
            phase = phases[number]
            # A little of the notes above it makes it a screech and not a whistle.
            value += level * (
                math.sin(phase) + 0.45 * math.sin(2 * phase) + 0.22 * math.sin(3 * phase)
            )
        value *= 1.0 + rough * shake.next()
        sample = noise.next()
        low += 0.55 * (sample - low)
        slow += 0.25 * (low - slow)
        value += hiss * (low - slow) * 3.0
        out.append(value * shape(index, count, 0.012, release))
    return out


def whirr():
    """Wheels spinning up: a low buzz that climbs, with rubber hissing over it."""
    seconds = 1.9
    count = int(seconds * SAMPLE_RATE)
    noise = Noise(401)
    stray = Wander(402, 30.0)
    low = 0.0
    slow = 0.0
    phase = 0.0
    out = []
    for index in range(count):
        position = index / count
        frequency = 95 * (240 / 95) ** min(1.0, position / 0.7) * (1 + 0.03 * stray.next())
        phase += 2 * math.pi * frequency / SAMPLE_RATE
        # A buzz: the note and several above it, falling off.
        buzz = sum(math.sin(k * phase) / k for k in range(1, 9))
        # The tread going round: the loudness beats faster as the wheels speed up.
        beat = 0.75 + 0.25 * math.sin(2 * math.pi * (9 + 9 * position) * index / SAMPLE_RATE)
        sample = noise.next()
        low += 0.5 * (sample - low)
        slow += 0.2 * (low - slow)
        hiss = (low - slow) * 2.2 * (0.4 + 0.6 * position)
        out.append((0.6 * buzz * beat + hiss) * shape(index, count, 0.08, 0.12))
    return out


def as_made(samples):
    """A sound as the film's script was handed it: whole numbers of 16 bits, the loudest at
    MADE_AT of full scale."""
    scale = MADE_AT / max(abs(value) for value in samples)
    return [int(32_767 * scale * value) for value in samples]


def cut(made, from_seconds, fade_in, gain):
    """A sound from `from_seconds` on, faded in over `fade_in` seconds and out over OUT, each
    in a straight line as the film's were, and `gain` times as loud."""
    kept = made[round(from_seconds * SAMPLE_RATE):]
    rising, falling = round(fade_in * SAMPLE_RATE), round(OUT * SAMPLE_RATE)
    out = []
    for index, value in enumerate(kept):
        left = len(kept) - index
        fade = min(1.0, index / rising) * min(1.0, left / falling)
        out.append(round(value * gain * fade))
    return out


def write(name, samples):
    path = RAW / name
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(b"".join(struct.pack("<h", value) for value in samples))
    loudest = max(abs(value) for value in samples) / 32_768
    seconds = len(samples) / SAMPLE_RATE
    print(f"{path}: {seconds:.3f} s, loudest sample at {loudest:.0%} of full scale")


def main():
    under = 10 ** (-WHIRR_UNDER_DB / 20)
    write("report_run_whirr.wav", cut(as_made(whirr()), WHIRR_FROM_SECONDS, WHIRR_IN, LEVEL * under))
    write("report_run_squeal.wav", cut(as_made(squeal()), 0.0, SQUEAL_IN, LEVEL))


if __name__ == "__main__":
    main()

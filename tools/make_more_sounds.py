#!/usr/bin/env python3
"""Synthesises ten of MilO's twelve built-in sounds (docs/APP_ENCYCLOPEDIA.md, "Connect and
trip-start sounds"), the ones added on 2026-10-13. Each is a file in res/raw/ named by the list
SOUNDS at the end of this script, and made by the one function that list names beside it.

Six short ones for the moment MilO connects to the truck (a seatbelt chime, a key fob, a turn
signal, a counter bell, a coin, a sonar ping) and four livelier ones for the moment the trip is
under way (a horn tap, an engine rev, a burnout, a race start). They are kinds of sound, and no
one maker's or game's own tune.

They are made from nothing but arithmetic: sine waves, and a noise that is counted out, not
drawn by chance. They are not copied from, sampled from or derived from any recording, and no
sound generator made them.

The output is committed, so the build does not run this script. TO CHANGE ONE SOUND, EDIT ITS
FUNCTION AND RUN THE SCRIPT AGAIN:

    python3 tools/make_more_sounds.py

To hear a change before it goes into the app, write the ten files somewhere else first:

    python3 tools/make_more_sounds.py <folder to write into> [peak]

It needs nothing but the Python 3 standard library, and it produces the same bytes every time,
so an unchanged script gives unchanged files: only the file of the function that was edited
changes. The chirp and the chime are made by make_trip_start_chirp.py, the party's pop by
make_party_pop.py and the run's two sounds by make_run_sounds.py; this script touches none of
them.

To add a sound or take one away, this list is one of three places: the app's own list is
`BuiltInSound` (data/settings/BuiltInSounds.kt), and `BuiltInSoundFilesTest` fails until the
two lists and the files in res/raw/ agree.
"""

import math
import struct
import sys
import wave
from pathlib import Path

RAW = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"

# The loudest sample of every sound, of full scale: what the chirp, the chime and the party's
# pop have. The phone's speaker distorts at full scale, and these play at the alarm volume.
PEAK = 0.55

# Every sound is made at this rate, the rate of the party's pop.
RATE = 44_100

# How a sound is written (SOUNDS says which). WHOLE keeps every sample: for a sound with noise
# in it (a click, an engine, a tyre), whose top a lower rate cannot hold. HALVED keeps every
# second sample, which gives a file at 22.05 kHz, the rate of the chirp and the chime, and half
# the size: for a sound that is notes alone. Nothing in those six lies above 8.4 kHz, and a file
# at that rate holds everything up to 11 kHz, so what is left out was not in the sound.
WHOLE = 1
HALVED = 2


class Noise:
    """White noise from a counted sequence, so that every run gives the same sound."""

    def __init__(self, seed):
        self.state = seed

    def next(self):
        self.state = (1_664_525 * self.state + 1_013_904_223) % 4_294_967_296
        return self.state / 2_147_483_648 - 1.0


class Wander:
    """Noise with its top taken off: a value that drifts about nought."""

    def __init__(self, seed, hz):
        self.noise = Noise(seed)
        self.k = 1 - math.exp(-2 * math.pi * hz / RATE)
        self.value = 0.0
        self.gain = 1 / math.sqrt(self.k / (2 - self.k))

    def next(self):
        self.value += self.k * (self.noise.next() - self.value)
        return self.value * self.gain * 0.5


def empty(seconds):
    return [0.0] * int(seconds * RATE)


def add(into, samples, at_seconds, gain=1.0):
    start = int(at_seconds * RATE)
    for index, value in enumerate(samples):
        if start + index < len(into):
            into[start + index] += gain * value


def edges(index, count, attack, release):
    """Nought to one over `attack` seconds, one to nought over the last `release`."""
    time = index / RATE
    left = (count - index) / RATE
    value = 1.0
    if time < attack:
        value *= 0.5 - 0.5 * math.cos(math.pi * time / attack)
    if left < release:
        value *= 0.5 - 0.5 * math.cos(math.pi * left / release)
    return value


def struck(seconds, partials, attack=0.003):
    """Something struck that rings on: each partial is (hz, level, seconds to die to a third)."""
    count = int(seconds * RATE)
    out = []
    for index in range(count):
        time = index / RATE
        value = 0.0
        for hz, level, dies in partials:
            value += level * math.exp(-time / dies) * math.sin(2 * math.pi * hz * time)
        out.append(value * edges(index, count, attack, 0.02))
    return out


def held(seconds, hz_start, hz_end, harmonics, attack=0.008, release=0.03):
    """A held note that may slide: harmonics is a list of (multiple, level)."""
    count = int(seconds * RATE)
    out = []
    phase = 0.0
    for index in range(count):
        position = index / count
        phase += 2 * math.pi * hz_start * (hz_end / hz_start) ** position / RATE
        value = sum(level * math.sin(multiple * phase) for multiple, level in harmonics)
        out.append(value * edges(index, count, attack, release))
    return out


def click(seed, rings_hz, dies=0.012, seconds=0.06):
    """A relay's click: a tick of noise and a small ring after it."""
    noise = Noise(seed)
    count = int(seconds * RATE)
    out = []
    for index in range(count):
        time = index / RATE
        tick = noise.next() * math.exp(-time / 0.0012)
        ring = math.exp(-time / dies) * math.sin(2 * math.pi * rings_hz * time)
        out.append((0.8 * tick + ring) * edges(index, count, 0.0003, 0.01))
    return out


# ---- For the moment MilO connects ------------------------------------------------------------


def seatbelt_chime():
    """Three soft dings, as a car reminds its driver."""
    out = empty(1.55)
    ding = struck(0.6, [(932, 1.0, 0.17), (1_864, 0.28, 0.10), (2_796, 0.08, 0.06)], attack=0.006)
    for number in range(3):
        add(out, ding, number * 0.46)
    return out


def key_fob():
    """Two quick high chirps, as a truck answers its key."""
    out = empty(0.36)
    chirp = held(0.065, 2_650, 2_350, [(1, 1.0), (2, 0.25), (3, 0.3)], attack=0.003, release=0.012)
    add(out, chirp, 0.0)
    add(out, chirp, 0.155)
    return out


def turn_signal():
    """Tick, tock, tick, tock: the relay behind the dashboard."""
    out = empty(1.25)
    for number in range(4):
        tock = number % 2 == 1
        add(out, click(31 + number, 1_150 if tock else 1_750), number * 0.36, 0.85 if tock else 1.0)
    return out


def counter_bell():
    """One ding of the bell on a shop's counter."""
    out = empty(1.3)
    add(out, struck(1.3, [(2_090, 1.0, 0.42), (5_770, 0.45, 0.20), (9_300, 0.18, 0.09), (1_045, 0.12, 0.25)]), 0.0)
    add(out, click(41, 3_200, dies=0.004, seconds=0.03), 0.0, 0.5)
    return out


def coin():
    """A bright blip of two notes, the second a fifth above the first."""
    out = empty(0.5)
    soft = [(1, 1.0), (3, 0.11), (5, 0.04)]
    add(out, held(0.07, 880, 880, soft, attack=0.002, release=0.01), 0.0)
    second = held(0.42, 1_319, 1_319, soft, attack=0.002, release=0.02)
    for index in range(len(second)):
        second[index] *= math.exp(-index / RATE / 0.13)
    add(out, second, 0.07)
    return out


def sonar_ping():
    """One ping and two fainter ones coming back."""
    out = empty(1.55)
    ping = struck(1.0, [(1_320, 1.0, 0.30), (2_640, 0.10, 0.12)], attack=0.004)
    add(out, ping, 0.0)
    add(out, ping, 0.24, 0.30)
    add(out, ping, 0.48, 0.12)
    return out


# ---- For the moment the trip is under way ----------------------------------------------------


def horn_tap():
    """Honk-honk: two notes at once, twice."""
    out = empty(0.62)
    rich = [(k, 1.0 / k ** 0.8) for k in range(1, 13)]
    honk = held(0.21, 370, 370, rich, attack=0.012, release=0.035)
    upper = held(0.21, 466, 466, rich, attack=0.012, release=0.035)
    both = [a + 0.9 * b for a, b in zip(honk, upper)]
    add(out, both, 0.0)
    add(out, both, 0.33)
    return out


def engine_rev():
    """Vroom: an engine run up and let fall back. A cartoon's engine, not a recording's."""
    seconds = 1.45
    count = int(seconds * RATE)
    noise = Noise(71)
    stray = Wander(72, 25.0)
    low = 0.0
    phase = 0.0
    out = []
    for index in range(count):
        position = index / count
        # Up quickly, held a moment, down more slowly.
        if position < 0.38:
            revs = 48 + (175 - 48) * math.sin(math.pi / 2 * position / 0.38)
        else:
            revs = 62 + (175 - 62) * math.exp(-(position - 0.38) / 0.22)
        revs *= 1 + 0.02 * stray.next()
        phase += 2 * math.pi * revs / RATE
        # A phone's speaker gives little below 300 Hz, so the notes above carry the sound.
        tone = sum(math.sin(k * phase) / k ** 0.6 for k in range(1, 25)) * 0.5
        # Each firing is a small thump: the note's own rate, heard as roughness.
        thump = 0.65 + 0.35 * math.sin(phase / 2) ** 2
        low += 0.12 * (noise.next() - low)
        loud = 0.35 + 0.65 * min(1.0, (revs - 48) / 100)
        out.append((0.75 * tone * thump + 1.6 * low * loud) * loud * edges(index, count, 0.03, 0.22))
    return out


def burnout():
    """Wheels spinning up, then the squeal as they bite: the sounds of the run after a report."""
    out = empty(2.05)
    # The whirr.
    seconds = 1.15
    count = int(seconds * RATE)
    noise = Noise(401)
    stray = Wander(402, 30.0)
    low = slow = 0.0
    phase = 0.0
    whirr = []
    for index in range(count):
        position = index / count
        frequency = 95 * (240 / 95) ** min(1.0, position / 0.7) * (1 + 0.03 * stray.next())
        phase += 2 * math.pi * frequency / RATE
        buzz = sum(math.sin(k * phase) / k for k in range(1, 9))
        beat = 0.75 + 0.25 * math.sin(2 * math.pi * (9 + 9 * position) * index / RATE)
        sample = noise.next()
        low += 0.5 * (sample - low)
        slow += 0.2 * (low - slow)
        hiss = (low - slow) * 2.2 * (0.4 + 0.6 * position)
        whirr.append((0.6 * buzz * beat + hiss) * edges(index, count, 0.08, 0.12))
    add(out, whirr, 0.0, 0.32)
    # The squeal.
    seconds = 0.95
    count = int(seconds * RATE)
    stray = Wander(301, 45.0)
    shake = Wander(302, 70.0)
    noise = Noise(303)
    low = slow = 0.0
    voices = [(940, 800, 1.0), (1_010, 845, 0.9), (1_480, 1_190, 0.35)]
    phases = [0.0] * len(voices)
    squeal = []
    for index in range(count):
        position = index / count
        wobble = 1.0 + 0.05 * stray.next()
        value = 0.0
        for number, (start_hz, end_hz, level) in enumerate(voices):
            phases[number] += 2 * math.pi * start_hz * (end_hz / start_hz) ** position * wobble / RATE
            angle = phases[number]
            value += level * (math.sin(angle) + 0.45 * math.sin(2 * angle) + 0.22 * math.sin(3 * angle))
        value *= 1.0 + 0.7 * shake.next()
        sample = noise.next()
        low += 0.55 * (sample - low)
        slow += 0.25 * (low - slow)
        value += 0.35 * (low - slow) * 3.0
        squeal.append(value * edges(index, count, 0.012, 0.3))
    add(out, squeal, 1.08, 0.36)
    return out


def race_start():
    """Three low beeps and a high one: ready, ready, ready, go."""
    out = empty(1.85)
    lamp = [(1, 1.0), (3, 0.22), (5, 0.08)]
    low = held(0.16, 523, 523, lamp, attack=0.005, release=0.02)
    for number in range(3):
        add(out, low, number * 0.40)
    add(out, held(0.6, 1_047, 1_047, lamp, attack=0.005, release=0.12), 1.20)
    return out


# Each sound: the name of its file in res/raw/ (without ".wav"), the function that makes it, and
# how it is written.
SOUNDS = [
    ("sound_seatbelt_chime", seatbelt_chime, HALVED),
    ("sound_key_fob", key_fob, HALVED),
    ("sound_turn_signal", turn_signal, WHOLE),
    ("sound_counter_bell", counter_bell, WHOLE),
    ("sound_coin", coin, HALVED),
    ("sound_sonar_ping", sonar_ping, HALVED),
    ("sound_horn_tap", horn_tap, HALVED),
    ("sound_engine_rev", engine_rev, WHOLE),
    ("sound_burnout", burnout, WHOLE),
    ("sound_race_start", race_start, HALVED),
]


def write(path, samples, peak, written):
    loudest = max(abs(value) for value in samples)
    scale = peak / loudest
    # Brought to its level first and thinned afterwards: a halved file holds exactly every
    # second sample of the whole one.
    frames = [int(32_767 * scale * value) for value in samples][::written]
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE // written)
        out.writeframes(b"".join(struct.pack("<h", frame) for frame in frames))
    square = sum(value * value for value in samples) / len(samples)
    level = 20 * math.log10(scale * math.sqrt(square))
    print(
        f"{path.name}: {len(samples) / RATE:.2f} s at {RATE // written} Hz, "
        f"average {level:.1f} dBFS, {path.stat().st_size} bytes"
    )


def main():
    folder = Path(sys.argv[1]) if len(sys.argv) > 1 else RAW
    peak = float(sys.argv[2]) if len(sys.argv) > 2 else PEAK
    folder.mkdir(parents=True, exist_ok=True)
    for name, make, written in SOUNDS:
        write(folder / f"{name}.wav", make(), peak, written)


if __name__ == "__main__":
    main()

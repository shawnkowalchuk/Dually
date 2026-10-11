#!/usr/bin/env python3
"""Synthesises the pop of MilO's one-year party (docs/APP_ENCYCLOPEDIA.md, "The one-year
party"):

- res/raw/party_pop.wav: a party popper going off. A crack with a little thump under it, then
  paper hissing down, with a few ticks in it.

It is made from nothing but arithmetic: sine waves, and a noise that is counted out, not drawn
by chance. It is not copied from, sampled from or derived from any recording, and no sound
generator made it.

The output is committed, so the build does not run this script. Run it again only to change the
sound:

    python3 tools/make_party_pop.py

It needs nothing but the Python 3 standard library, and it produces the same bytes every time,
so an unchanged script gives an unchanged file. The two other built-in sounds are made by
make_trip_start_chirp.py, which this script does not touch.
"""

import math
import struct
import wave
from pathlib import Path

RAW = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"

# Twice the rate of the chirp and the chime. They are tones below 4 kHz; a crack and hissing
# paper are noise, and most of what makes them sound like paper lies above that.
SAMPLE_RATE = 44_100

# The loudest sample, of full scale: what the chirp and the chime have. The phone's speaker
# distorts at full scale, and this plays at the alarm volume Shawn has set.
PEAK = 0.55


class Noise:
    """White noise from a counted sequence, so that every run gives the same sound."""

    def __init__(self, seed):
        self.state = seed

    def next(self):
        self.state = (1_664_525 * self.state + 1_013_904_223) % 4_294_967_296
        return self.state / 2_147_483_648 - 1.0


def empty(seconds):
    return [0.0] * int(seconds * SAMPLE_RATE)


def add(into, samples, at_seconds, gain=1.0):
    start = int(at_seconds * SAMPLE_RATE)
    for index, value in enumerate(samples):
        if start + index < len(into):
            into[start + index] += gain * value


def sweep(seconds, start_hz, end_hz, decay_seconds, attack_seconds=0.002):
    """A sine gliding from start_hz to end_hz that dies away."""
    count = int(seconds * SAMPLE_RATE)
    out = []
    phase = 0.0
    for index in range(count):
        position = index / count
        frequency = start_hz * (end_hz / start_hz) ** position
        phase += 2 * math.pi * frequency / SAMPLE_RATE
        time = index / SAMPLE_RATE
        envelope = math.exp(-time / decay_seconds)
        if time < attack_seconds:
            envelope *= time / attack_seconds
        tail = (count - index) / SAMPLE_RATE
        if tail < 0.004:
            envelope *= tail / 0.004
        out.append(envelope * math.sin(phase))
    return out


def burst(seconds, decay_seconds, seed, low_pass=None, high_pass=None, attack_seconds=0.001):
    """Noise that dies away, with its top or its bottom taken off."""
    noise = Noise(seed)
    count = int(seconds * SAMPLE_RATE)
    out = []
    low = 0.0
    slow = 0.0
    low_k = 1.0 if low_pass is None else 1 - math.exp(-2 * math.pi * low_pass / SAMPLE_RATE)
    high_k = 0.0 if high_pass is None else 1 - math.exp(-2 * math.pi * high_pass / SAMPLE_RATE)
    for index in range(count):
        value = noise.next()
        low += low_k * (value - low)
        value = low
        if high_pass is not None:
            slow += high_k * (value - slow)
            value -= slow
        time = index / SAMPLE_RATE
        envelope = math.exp(-time / decay_seconds)
        if time < attack_seconds:
            envelope *= time / attack_seconds
        tail = (count - index) / SAMPLE_RATE
        if tail < 0.004:
            envelope *= tail / 0.004
        out.append(envelope * value)
    return out


def sprinkle(seconds, count, seed, from_hz=2_400, to_hz=5_200):
    """Tiny high ticks that thin out: paper coming down."""
    noise = Noise(seed)
    out = empty(seconds)
    for number in range(count):
        # More of them early than late.
        when = seconds * 0.9 * (number / count) ** 1.6 + 0.004 * (noise.next() + 1)
        pitch = from_hz + (to_hz - from_hz) * (noise.next() + 1) / 2
        loud = (1 - number / count) ** 1.3 * (0.6 + 0.4 * (noise.next() + 1) / 2)
        add(out, sweep(0.012, pitch, pitch * 0.92, 0.004, attack_seconds=0.0005), when, loud)
    return out


def pop():
    """The popper: a crack with a little thump, then paper hissing down. The crack is the
    sound's first and loudest moment, so whoever plays it on a frame plays the bang on it."""
    out = empty(1.0)
    add(out, burst(0.12, 0.022, seed=21, low_pass=7_000), 0.0, 1.0)
    add(out, sweep(0.11, 190, 70, 0.035), 0.0, 0.8)
    hiss = burst(0.85, 0.20, seed=22, high_pass=3_200, attack_seconds=0.03)
    flutter = Noise(23)
    level = 1.0
    for index in range(len(hiss)):
        if index % (SAMPLE_RATE // 45) == 0:
            level = 0.45 + 0.55 * (flutter.next() + 1) / 2
        hiss[index] *= level
    add(out, hiss, 0.03, 0.30)
    add(out, sprinkle(0.8, 12, seed=24), 0.1, 0.10)
    return out


def write(name, samples):
    """Writes the sound with its loudest sample at PEAK."""
    output_path = RAW / name
    loudest = max(abs(value) for value in samples)
    scale = PEAK / loudest
    frames = b"".join(struct.pack("<h", int(32_767 * scale * value)) for value in samples)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(output_path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(frames)
    where = max(range(len(samples)), key=lambda index: abs(samples[index])) / SAMPLE_RATE
    seconds = len(samples) / SAMPLE_RATE
    print(f"{output_path.name}: {seconds:.2f} s, loudest {where * 1000:.1f} ms in, {output_path.stat().st_size} bytes")


def main():
    write("party_pop.wav", pop())


if __name__ == "__main__":
    main()

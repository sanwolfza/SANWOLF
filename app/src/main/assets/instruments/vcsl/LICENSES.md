# Bundled multisample instruments — licences

All audio in this folder is derived from the **VCSL – Versilian Community Sample Library**
(https://github.com/sgossner/VCSL), by Versilian Studios LLC / Samuel Gossner.

## Licence: CC0 1.0 Universal (public domain dedication)

- The repository's `LICENSE` file is the full **Creative Commons CC0 1.0 Universal** legal code
  (https://github.com/sgossner/VCSL/blob/master/LICENSE).
- The README states: *"This collection is under a Creative Commons 0 license. Essentially it's Public Domain -
  you can do whatever you want with these sounds (even make commercial software), no royalties, no credit,
  no special terms."*
- Attribution is not required; we credit Versilian Studios voluntarily.
- CC0 text: https://creativecommons.org/publicdomain/zero/1.0/legalcode

Checked 2026-10-11 against the `master` branch. None of the folders used here contains its own licence
or info file overriding CC0 (the only per-folder licence notes in the repo are the excluded Pipe Organ's
`Info.txt` plus technical notes such as `NORMALIZED.txt` and `TX81Z/Origin.txt`, which add no terms).

## Excluded: Pipe Organ

`Aerophones/Edge-blown Aerophones/Pipe Organ/` carries its own `Info.txt`: *"Organ sampled by Simon Dalzell of
Ivy Audio … Bearer is granted right to redistribute this sample set by Versilian Studios LLC. Credit to both
original author and Versilian Studios is encouraged."* That is a separate redistribution grant, not CC0,
so **nothing from the Pipe Organ is included**.

## Instruments included (all CC0 1.0)

| id | Name | Category | VCSL source folder | Layer used | Samples |
|---|---|---|---|---|---|
| steinway_grand | Steinway Grand Piano | Keys | `Chordophones/Zithers/Grand Piano, Steinway B/Sus` | Sus, Close mic, vl3 (of vl2–vl4) | 28 (A#0–F#7) |
| fm_epiano | FM Electric Piano (Yamaha TX81Z) | Keys | `Electrophones/TX81Z/FM Piano` | vl2 (of 3) | 19 (C1–C7) |
| marimba | Marimba | Mallets | `Idiophones/Struck Idiophones/Marimba` | med | 10 (F2–C7) |
| vibraphone | Vibraphone | Mallets | `Idiophones/Struck Idiophones/Vibraphone/Soft Mallets` | v2 | 11 (F3–E6) |
| kalimba | Kalimba (Kenya) | Plucked | `Idiophones/Plucked Idiophones/Kalimba, Kenya` | vl3 (only layer) | 9 (B3–B5) |
| balafon | Balafon | World | `Idiophones/Struck Idiophones/Balafon/Traditional Mallet` | vl2 (of 3) | 6 (C#4–F6) |
| harp | Concert Harp | Plucked | `Chordophones/Composite Chordophones/Concert Harp` | mf | 19 (B1–B6) |

## Modifications (also released CC0)

Converted to 44.1 kHz / 16-bit / mono WAV (stereo summed), leading silence trimmed (2 ms pre-roll),
tails cut at −60 dB or capped (4 s keys, 3 s others) with a cosine fade-out, peak-normalised to −1 dBFS
with TPDF dither. Note names in our filenames are the **sounding** pitch; VCSL labels marimba, vibraphone,
balafon and kalimba one octave lower. `manifest.json` keeps the original VCSL file path for every sample.

Credit line for an app's About screen (optional under CC0):
"Instrument samples from VCSL – Versilian Community Sample Library by Versilian Studios (CC0 1.0)."

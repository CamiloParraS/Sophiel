# Context

- **Parent**: the person who sets up protection, knows the PIN, and sets sensitivity.
- **Child**: the person using the protected phone; cannot change settings without the PIN.
- **Protection**: the running state in which the screen is captured and analysed.
- **Unlock**: the short window after the Parent enters the PIN in which Stop and Settings open without asking again. It ends after about 2 minutes without a PIN-gated action, or as soon as the Parent leaves the app or the screen turns off.
- **Preset**: a named performance/precision tradeoff. Three exist: **Light** (judges the whole frame), **Balanced** (judges a grid of tiles), and **Precise** (masks the boxes a detector finds; experimental, Android 14+, D35).
- **Tile**: one cell of the grid that Balanced classifies on its own.
- **Mask**: the covering drawn over a flagged tile (or the whole frame in Light).
- **Probe**: briefly removing one mask to check whether the content under it changed.
- **Masking episode**: the screen going from no masks to at least one, whatever the preset; the unit the Log counts. Probes don't end it, and masks that return within a few seconds of the screen clearing continue it.
- **Sensitivity**: the Parent-set score threshold above which a frame or tile is flagged.
- **Log entry**: a local record kept 7 days, scalars only, never pixels. Kinds: masked, protection on, protection off (with reason), unanalyzable.
- **Unanalyzable**: a screen that returns only black frames (`FLAG_SECURE`), so it can't be judged.

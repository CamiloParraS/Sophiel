# Context

- **Parent**: the person who sets up protection, knows the PIN, and sets sensitivity.
- **Child**: the person using the protected phone; cannot change settings without the PIN.
- **Protection**: the running state in which the screen is captured and analysed.
- **Preset**: a named performance/precision tradeoff. Two exist: **Light** (judges the whole frame) and **Balanced** (judges a grid of tiles).
- **Tile**: one cell of the grid that Balanced classifies on its own.
- **Mask**: the covering drawn over a flagged tile (or the whole frame in Light).
- **Reveal**: undoing a mask; requires the Parent's PIN.
- **Sensitivity**: the Parent-set score threshold above which a frame or tile is flagged.
- **Log entry**: a local record kept 7 days, scalars only, never pixels. Kinds: masked, protection on, protection off (with reason), unanalyzable.
- **Unanalyzable**: a screen that returns only black frames (`FLAG_SECURE`), so it can't be judged.

# 07: Measure and settle on both devices

**What to build:** Replace the estimates with measurements. Per-tile cost, per-frame sweep time and probe exposure are measured on Device A and Device B, then the grid size and the tuning values are chosen. The ~70 ms per tile on Device A in the design is an estimate, never measured; a 2×3 sweep over about 400 ms means falling back to 2×2. Also check whether content straddling a tile boundary is missed, which decides the whole-frame safety net.

**Blocked by:** 05, 06

**Status:** ready-for-human

- [ ] Per-tile and per-frame sweep timings (median of at least 3 runs, ambient and battery noted) are recorded for both devices, for Light and Balanced.
- [ ] Time from "frame available" to "first flagged tile masked" is recorded, since per-tile emission should make it shorter than the full sweep.
- [ ] Probe exposure in milliseconds (median and worst case) is recorded with the debug mask on.
- [ ] The grid is settled: 2×3, or 2×2 if the sweep exceeds about 400 ms on Device A; the choice is applied in the preset.
- [ ] Straddling check: a handful of mid-band demo images placed across a tile boundary. If any are missed that the whole frame catches, the whole-frame safety net is turned on (one extra whole-frame classification; when the whole frame flags but no tile does, mask the tiles that passed the skin gate). Otherwise it stays off.
- [ ] The skin-gate minimum ratio and luma floor are retuned on real tiles, including ordinary app screens (settings, chat, maps).
- [ ] Starting values for Strict, Normal and Relaxed are chosen and applied.
- [ ] Results and every choice go into `docs/DECISIONS.md`.
- [ ] `ParityTest` still passes.

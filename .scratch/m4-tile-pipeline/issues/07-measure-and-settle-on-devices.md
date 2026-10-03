# 07: Measure and settle on both devices

**What to build:** Replace the estimates with measurements. Per-tile cost, per-frame sweep time and probe exposure are measured on Device A and Device B, then the grid size and the tuning values are chosen. The ~70 ms per tile on Device A in the design is an estimate, never measured; a 2×3 sweep over about 400 ms means falling back to 2×2. Also check whether content straddling a tile boundary is missed, which decides the whole-frame safety net.

**Blocked by:** 05, 06

**Status:** ready-for-human

- [x] Per-tile and per-frame sweep timings (median of at least 3 runs, ambient and battery noted) are recorded for both devices, for Light and Balanced.
- [x] Time from "frame available" to "first flagged tile masked" is recorded, since per-tile emission should make it shorter than the full sweep.
- [x] Probe exposure in milliseconds (median and worst case) is recorded with the debug mask on.
- [x] The grid is settled: 2×3, or 2×2 if the sweep exceeds about 400 ms on Device A; the choice is applied in the preset.
- [x] Straddling check: a handful of mid-band demo images placed across a tile boundary. If any are missed that the whole frame catches, the whole-frame safety net is turned on (one extra whole-frame classification; when the whole frame flags but no tile does, mask the tiles that passed the skin gate). Otherwise it stays off.
- [x] The skin-gate minimum ratio and luma floor are retuned on real tiles, including ordinary app screens (settings, chat, maps).
- [x] Starting values for Strict, Normal and Relaxed are chosen and applied.
- [x] Results and every choice go into `docs/DECISIONS.md`.
- [x] `ParityTest` still passes.

## Comments

2026-10-02 (agent): measurements in D26, from `app/src/androidTest/.../TileBenchmark.kt` run on both devices. Grid stays 2x3; safety net off (whole frame never caught what tiles missed); first flagged tile ~150 ms vs ~250 ms sweep. D25 (human decision) replaced the global probe trigger with a neighbour-based one.

Left for the human:
- ~~Probe exposure under D25~~ done 2026-10-03: A median 138 / max 265 ms, B median 121 / max 168 ms, no timeouts (D26, `2b631f0`).
- **Skin-gate retune:** needs labelled real screens (settings, chat, maps). Data so far: settings 6/6 gated, launcher 0/6 gated but harmless.
- **Strict / Relaxed values:** need labelled score data; still 0.55 / 0.85 placeholders.

2026-10-03 (human): skin gate kept unchanged; Strict/Normal/Relaxed kept at 0.55/0.70/0.85. Recorded in D26. Ticket done.

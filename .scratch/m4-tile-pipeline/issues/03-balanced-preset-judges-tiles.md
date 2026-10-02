# 03: Balanced preset judges tiles

**What to build:** Choosing Balanced cuts the cropped frame (system bars already removed) into a 2×3 grid and judges each tile on its own: skin gate first, classifier only for tiles that pass. The Test Feed shows a temporary per-tile readout (verdict, score, gated, cache hit) so the behaviour is visible without capture or an overlay.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] A tile grid computes tile rectangles for any frame size, including odd dimensions, with no gaps or overlaps (unit test for Light 1×1 and Balanced 2×3).
- [ ] Each tile gets its own skin-gate decision and, if it passes, its own classification; a frame with flagged content in one tile and a plain UI in the others flags only that tile.
- [ ] The Test Feed shows the per-tile readout for Balanced and a single tile for Light, updating each tile as its result arrives.
- [ ] Landscape frames use the swapped grid (3 columns × 2 rows).
- [ ] Per-frame latency and per-tile gated/cache-hit flags are reported.
- [ ] A plain settings screenshot gates every tile without running the classifier.
- [ ] JVM tests and `ParityTest` pass; `:app:assembleDebug` succeeds.

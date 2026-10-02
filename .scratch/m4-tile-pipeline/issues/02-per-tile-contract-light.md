# 02: Per-tile contract, with Light running on it

**What to build:** The detection contract speaks in tiles instead of one score per frame (SPEC §3.3). A preset says how many tiles a frame has. Each tile's result is emitted as soon as that tile is judged, so a mask can go up without waiting for the rest of the sweep. Light is a single tile and must produce the same scores as today's whole-frame detection; this is the prefactor that makes Balanced and the tracker easy. The policy becomes a stateless score-to-severity mapping: its engage/release counters move into the tile tracker (ticket 04), so this ticket must not build per-tile hysteresis. The verdict cache stays shared across tiles. The existing callers (live capture and the Test Feed) move to the new contract and still work.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] A tile result carries index, severity, score, gated, cache hit, hash and its own latency; a preset type defines Light as 1×1 and Balanced as 2 columns × 3 rows (rows and columns swap in landscape).
- [ ] Analysing a frame emits tile results one by one as each finishes, not as a batch at the end.
- [ ] With Light, the single tile's score matches what the old whole-frame path gave for the same input.
- [ ] The policy holds no state between frames (unit test: the same score always maps to the same severity).
- [ ] The verdict cache is shared across tiles and keyed by hash.
- [ ] Live capture and the Test Feed work from the user's point of view. Between this ticket and ticket 06 there is no engage hysteresis; that is acceptable because nothing is masked yet.
- [ ] Existing JVM tests (updated where they asserted the old hysteresis), the leak test and `ParityTest` pass; `:app:assembleDebug` succeeds; no `INTERNET` permission.

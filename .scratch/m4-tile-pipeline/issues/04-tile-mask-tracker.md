# 04: Tile mask tracker (state machine, tests first)

**What to build:** Pure logic with no Android types that decides, per tile, whether it is CLEAR, MASKED, PROBING or REVEALED (SPEC §3.4), written test-first. It exists because our own mask gets captured: a masked tile's captured score is meaningless, so the tracker holds the last verdict, remembers the hash of the content it masked, and only uncovers a tile when a probe is warranted. The tracker owns all timing; the policy is a stateless mapping (ticket 02).

**Blocked by:** 02

**Status:** ready-for-agent

- [x] A tile goes CLEAR → MASKED after its score reaches the threshold for the engage count (starting at 2 frames) and records the content hash as locked. There is no release count: a tile leaves MASKED only through a probe.
- [x] While MASKED or REVEALED, the tile's incoming score is ignored.
- [x] Balanced: a probe starts only when a frame arrives AND (at least half the CLEAR tiles' hashes changed since the previous frame OR about 2 s have passed since the last probe).
- [x] Light (no CLEAR tiles): a probe starts on the ~2 s timer only, when frames arrive.
- [x] Never more than one probe per second per tile; a static screen (no frames) never probes.
- [x] Probe validity: while the captured tile still shows our mask, the probe keeps waiting; after about 300 ms without the mask clearing, the tile returns to MASKED. The tracker receives a "still shows the mask" flag per tile as input; it does not inspect pixels itself.
- [x] Once the probe frame is valid: if its hash equals the locked hash (exact match only), the tile returns to MASKED without classification; if it differs, the tile is classified, re-masking with the new hash at or above threshold and going CLEAR below it.
- [x] Reveal: a reveal request moves every MASKED tile to REVEALED for 5 s, then to PROBING.
- [x] Pause: while paused (a Sophiel screen is in the foreground), states are frozen and incoming frames are ignored; resuming continues from the frozen states.
- [x] Reset: a grid change (rotation or preset change) resets every tile.
- [x] A new masking episode (CLEAR → MASKED) is reported distinctly from a re-mask after a probe, so the log can count episodes.
- [x] Time is injected (fake clock in tests); no wall-clock calls inside the logic.

## Comments

Implemented in `safecore/.../core/tile/TileMaskTracker.kt`, tests in `TileMaskTrackerTest` (11, all transitions). Notes for ticket 06:

- **Driving it:** `onTile(now, verdict, showsMask)` per tile as it arrives, then `endFrame(now)`. `reveal`, `pause`/`resume`, `reset(tileCount)`; `tracker[i]` reads a state. `onTile` returns true only for a new episode (CLEAR → MASKED).
- **Probe timer is per tile** (since the tile was masked or last probed), not one global "since the last probe": a global timer would probe a tile in the frame right after it engaged.
- **Probe timeout needs a frame.** The 300 ms re-mask runs in `endFrame`. If the screen goes static after the mask is lifted, no frame arrives and the tile stays uncovered; the driver must cover that case.
- **Hash-locked probes still classify:** the tracker sees verdicts after the Detector, so "no classification" relies on the verdict cache hit. Pass locked hashes to the Detector only if probe cost shows up in ticket 07.

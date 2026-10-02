# Tile-grid masking without a feedback loop  `wayfinder:research`
Assignee: 
Blocked by: none
Status: CLOSED

## Resolution

Findings: `docs/wayfinder/research/02-tile-feedback-loop.md` on branch `research/tile-feedback-loop` (commit f907ef1).
- Doesn't work: a `FLAG_SECURE` overlay (inferred to capture as black, so the same loop); single-app capture (Device A is API 33, one app only).
- Approach: per-tile state machine CLEAR -> MASKED -> PROBING. While masked, ignore the captured score and keep the last verdict.
- Release: big dHash change of the pixelated tile, at least half the unmasked tiles changing, or ~2 s TTL. A probe unmasks one captured frame, at least 1 s apart. Needs a pixelate/blur mask, not opaque.
- Cost: ~70 ms/tile on Device A (ESTIMATED, not measured). 2x3 ~420 ms worst case, 3x3 ~630 ms. Skin gate usually leaves 1-2 tiles. Recommend 2 columns x 3 rows, fall back to 2x2 if a measured sweep exceeds ~400 ms.
- Caveats: the FLAG_SECURE behaviour is from memory/D18, re-check before citing. A probe briefly exposes the pixels underneath: a product decision (see ticket 03).

## Question

The Balanced preset classifies a grid of tiles and masks only flagged ones. The mask overlay is itself captured by MediaProjection, so a masked tile looks blurred, scores low, and unmasks (flicker). Read SPEC M4 and D19, and the Android docs on MediaProjection and overlay capture. What are the viable ways to avoid this loop (hold masks for N frames, remember last verdict per tile, `FLAG_SECURE`-style overlay exclusion, etc.), and what does per-tile inference cost on Device A (~MobileNetV2 per tile at the current fps)? Recommend a grid size and approach.

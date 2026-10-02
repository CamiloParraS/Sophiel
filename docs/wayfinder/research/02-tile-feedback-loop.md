# Research 02: tile-grid masking without a feedback loop

Ticket: `docs/wayfinder/tickets/02-tile-feedback-loop.md`. Status: answered; one choice is deferred to ticket 03 (see Open).

## Sources and confidence

| Claim | Source | Confidence |
| --- | --- | --- |
| Full-display capture mirrors the composited display including our own overlay windows. | Project: SPEC §4.2 and D18 (observed with the debug pill). The Android MediaProjection page (developer.android.com/media/grow/media-projection) does not address overlays either way. | Observed on-device |
| Android 14+ lets the user share a single app; that capture excludes status bar, nav bar, notifications and other system UI. An app can opt out via `createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())`; OEMs may override. | developer.android.com/media/grow/media-projection (fetched) | Primary doc |
| `FLAG_SECURE` "treats the content of the window as secure, preventing it from appearing in screenshots or from being viewed on non-secure displays." | `WindowManager.LayoutParams#FLAG_SECURE` reference. Quoted from memory: the page fetch returned only nav chrome. Re-check before citing in the report. | Primary doc, not re-fetched |
| A capturing app sees a secure window as black, not as "absent". | D18: banking app yields an all-black frame (`BlackFrameDetector`). | Observed |
| Per-classification latency: Device B p50 43 ms, p95 50 ms (hash, gate and inference). Device A is ~20-30 ms slower. | DECISIONS D19 (classifier stress), D1 (Device A offset) | Measured B; A is an offset, not a direct measurement |
| Pipeline: dHash, then verdict cache (exact-hash match), then skin gate, then classifier (one `Interpreter`, single-thread dispatcher). Policy runs every frame. | `safecore/.../DetectionPipeline.kt` | Code |
| `FrameThrottle` floor is 80 ms; one frame in flight, the rest dropped. | D19, `ProjectionService` | Code |

## Why the loop happens (restated)

The scrim is in the next captured frame. A masked tile scores SAFE, hysteresis releases, the content returns, and the tile is flagged again: strobe. Any fix must decide what a tile's captured pixels mean while that tile is masked.

## Options

1. **Exclude our overlay from capture with `FLAG_SECURE`.** Rejected. A secure window is rendered black to the capturer, not omitted. The masked tile would capture as black (SAFE), which is the same loop with a black scrim, and it would trip the black-frame detector. No platform flag makes a window "visible to the user but not to MediaProjection" for an ordinary app. (`setHideOverlayWindows` is the opposite direction: it lets an app hide *other* overlays from itself.)
2. **Single-app capture.** Rejected as the fix. It avoids the loop, but Device A is API 33 (no such option) and it covers one app only (D21, SPEC M4).
3. **Hold masks for N frames (timer).** Simple. Cost: it unmasks even if the content is still there, so it re-flags after N frames; it re-exposes content every N frames unless N is long. Needs a "probe" to learn anything.
4. **Remember last verdict per tile (freeze while masked).** Core of the fix. While tile *t* is masked, ignore its captured score. Keep the mask until a release condition fires. Pure state machine, testable in `:safecore` with no Android types.
5. **Release condition from unmasked evidence.** Unmasked tiles keep producing real pixels. If most unmasked tiles' dHash changed (scroll, app switch), assume the masked region's content changed too and release and re-score. Needs at least one unmasked tile, so it needs a TTL fallback.
6. **Change detection on the masked tile's own capture.** With a pixelation or blur mask (not opaque), the captured tile still changes when the content underneath scrolls (coarse structure survives). A large dHash change of the masked tile against its previous masked hash is the release trigger; this matches the suggestion already in SPEC M4. With an opaque mask this signal is zero.
7. **Probe frame.** Release the mask for one captured frame, score the real pixels, re-mask if still flagged. The only way to get ground truth under the mask. Cost: content is visible for at least one capture period (about 80-100 ms plus compositor latency). Unavoidable on full-display capture; mitigate by probing rarely.
8. **Accessibility `takeScreenshot` or other capture paths.** Out of scope: new permission class, rate limits, not in SPEC §1.2.

## Recommended approach

Per-tile state machine: `CLEAR -> MASKED(hash_at_mask, since) -> PROBING -> CLEAR | MASKED`.

- A tile flags when its score is at or above the Parent's sensitivity threshold (reuse `PolicyEngine`).
- While `MASKED`: do not run the classifier on that tile. Keep the last verdict.
- `MASKED -> PROBING` when either (a) the masked tile's dHash differs from `hash_at_mask` by a large distance (needs a pixelation mask, option 6), (b) at least half of the unmasked tiles changed (option 5), or (c) a TTL of about 2 s elapsed with no other signal (option 3, fallback only).
- `PROBING` hides the tile's mask for one captured frame, scores it, and returns to `MASKED` (still flagged) or `CLEAR`. Enforce a minimum 1 s between probes of the same tile to bound exposure.
- Cache per-tile verdicts by exact tile hash (consistent with the `fix(cache)` commit that dropped Hamming 5).

## Per-tile inference cost on Device A

Estimate, not a measurement: Device B p50 43 ms plus 20-30 ms gives about 65-75 ms per classification on Device A (use 70 ms). One `Interpreter` on one thread, so tiles run sequentially. Batching would need re-conversion and CPU batching gains are small, so not recommended.

| Grid (cols x rows, portrait) | Tiles | Worst-case full sweep on A | Notes |
| --- | --- | --- | --- |
| 2x2 | 4 | ~280 ms | Coarse; a mask covers 25% of screen |
| 2x3 | 6 | ~420 ms | Recommended |
| 3x3 | 9 | ~630 ms | Misses SPEC M4 V1's 400 ms target in worst case |
| 3x4 | 12 | ~840 ms | Too slow |

The skin gate runs per tile before the classifier. On typical screens most tiles gate out (D19: thumbnails on white measured 0.013 skin ratio), so a real sweep is usually one or two classifications. The worst case is an explicit full-screen photo where every tile is skin; order tiles by skin ratio descending so the likeliest flagged tiles are masked first.

Each tile is squashed to 224x224 (D19 keeps squash). A 2x3 tile of a 1080x2400 screen is about 540x800, so it is downscaled less than a whole frame, which should help small-content recall. Not measured.

## Recommendation

2 columns x 3 rows (6 tiles), per-tile verdict hold with probe-on-change, pixelation mask. On Device A, measure real per-tile latency (70 ms is an extrapolation) and the worst-case sweep; drop to 2x2 if the sweep exceeds ~400 ms.

## Open (hand to ticket 03 and the human)

- **Probe exposure is a product decision.** Any full-display approach must show the underlying pixels for at least one capture period to re-evaluate a masked tile. Ticket 03 should decide whether that is acceptable, or whether TTL-only release (no probes, longer-lived masks, more false holds) is preferred.
- The mask must be pixelation or blur for option 6 to work. If ticket 03 picks an opaque scrim, release logic rests on options 5 and 3.
- Light preset (whole frame) has the same loop; model it as a 1x1 grid with the same hold logic.
- One on-device test would close option 1 with evidence instead of inference: a `FLAG_SECURE` overlay should capture as black, not omitted.

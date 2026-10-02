# Censor treatment and reveal flow  `wayfinder:prototype`
Assignee: Camilo Parra (claimed 2026-10-01)
Blocked by: none
Status: CLOSED

## Resolution

Prototype: `docs/wayfinder/prototypes/03-censor-treatment.prototype.html` (uncommitted, throwaway; fold nothing into main).
- Look: **Variant C, solid block with a lock icon and "Hidden by Sophiel" label.** Chip optional.
- Reveal: PIN-gated (already agreed in the grilling round).
- Probe: kept, but made rare and cheap with a **hash lock**. Remember the exact hash of the content that was blocked. At a probe, if the revealed tile hashes identically, re-mask on the next frame with no classification. Exact match only (near-miss matching was rejected in `c7eff9c`). Probes are triggered only when frames arrive and surrounding tiles change, not on a timer.
- Consequence for ticket 02: the solid block gives no hash-change release signal, so release relies on probe + hash lock, plus the ~2 s TTL fallback. The research's "pixelate/blur only" constraint no longer applies, but its assumption that a pixelated mask changes under the hash was never verified.
- Unresolved risk: first probe after a change exposes the pixels for at least one captured frame. Needs on-device measurement; becomes a verification item in the new SPEC.
- Stretch only: a softer "suggestive" tier (pixelate/blur at mid scores) instead of blocking.

## Question

What should a masked region look like (pixelate, blur, solid block) and who can undo it? Build 2-3 rough Compose/overlay treatments the human can react to. Starting defaults: pixelate, reveal requires the parent PIN, a small unobtrusive "protected" chip.

Constraint from the tile research: the mask must stay pixelate/blur (not opaque) so the tile can be released by hash change, and a periodic probe briefly exposes the pixels beneath for at least one capture period. Decide whether that exposure is acceptable.

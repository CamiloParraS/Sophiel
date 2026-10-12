# 10: Does screen share survive screen-off on A?

Type: task
Status: resolved
Blocked by: 03

## Question

HITL device check on Device A (API 33). D18 ends screen share on every screen-off because on B the projection died anyway and left a dead session. If A's projection survives, the hybrid (ticket 03) could keep A on the fast capture mode all day after one consent, instead of falling to accessibility at every screen-off. The human expects it dies.

Throwaway build with the `ACTION_SCREEN_OFF` teardown disabled (pause frames instead). With and without a secure lock on A: start screen share, screen off for ~10 s and ~2 min, unlock. Record: `MediaProjection.Callback.onStop()` fired or not, `dumpsys media_projection`, whether frames arrive after unlock, whether masking works, any crash.

Resolved when the answer says "survives" (then: below API 35, pause at screen-off, amending D18) or "dies" (D18 stands; LIMITATIONS: A runs on accessibility most of the day).

## Answer

**Survives.** Human device check on A, reported 2026-10-12: with the screen-off teardown disabled, screen share on A is still alive after screen-off and unlock. The per-run details the question listed (secure lock or not, ~10 s vs ~2 min, `onStop()`, `dumpsys media_projection`, masking after unlock) were not recorded; the SPEC's verification item re-checks them on A.

- **D18 amended:** below API 35, screen-off pauses frames instead of ending screen share; frames resume at screen-on with the same session. On API 35+ (B) the teardown stays, as D18 found.
- **Ticket 03 consequences:** on A, the capture mode stays screen share across screen-off, so the hybrid falls to accessibility only when the share really ends (chip, system, reboot). The screen-share FGS notification stays during screen-off on A. The planned LIMITATIONS line "A runs on accessibility most of the day" is dropped; "A's accessibility fallback exposes 2-3x more" stays, for after a reboot or a stopped share.

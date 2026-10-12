# 10: Does screen share survive screen-off on A?

Type: task
Status: open
Blocked by: 03

## Question

HITL device check on Device A (API 33). D18 ends screen share on every screen-off because on B the projection died anyway and left a dead session. If A's projection survives, the hybrid (ticket 03) could keep A on the fast capture mode all day after one consent, instead of falling to accessibility at every screen-off. The human expects it dies.

Throwaway build with the `ACTION_SCREEN_OFF` teardown disabled (pause frames instead). With and without a secure lock on A: start screen share, screen off for ~10 s and ~2 min, unlock. Record: `MediaProjection.Callback.onStop()` fired or not, `dumpsys media_projection`, whether frames arrive after unlock, whether masking works, any crash.

Resolved when the answer says "survives" (then: below API 35, pause at screen-off, amending D18) or "dies" (D18 stands; LIMITATIONS: A runs on accessibility most of the day).

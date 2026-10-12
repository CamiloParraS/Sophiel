# 12: Screen share pauses at screen-off below API 35

**What to build:** On devices below API 35 (A), screen-off no longer ends the screen share: frames are acquired and closed unread, and resume at screen-on on the same session, so there is no consent dialog after unlocking. API 35+ keeps today's teardown (amends D18, per ticket 10).

**Blocked by:** 11

**Status:** done

- [x] Below API 35, screen-off pauses frames; every acquired `Image` is still closed
- [x] Screen-on resumes the same share; `dumpsys media_projection` shows the session
- [x] API 35+ behaviour unchanged
- [x] Switch off: M6 behaviour (share ends at screen-off). When the share ends with the switch off, `CaptureModeMachine` clears `protecting`: write that to `ProtectionStore` too, or a stale `true` resumes protection without a Start (ticket 11 note)
- [x] Checked on A with SPEC V1a steps (screen off ~10 s and ~2 min, secure lock; test video masked after unlock)
- [x] Ends buildable

## Notes (built 2026-10-12)

- Pauses only when the switch is on **and** the accessibility service is bound (SPEC §4.7: "Service unbound with the switch on: the share ends at screen-off as before").
- While paused the tile tracker is paused too (the ticket 09 own-screen pause), so probe and D51 still-screen timers don't act on a screen-off with no frames; an open unanalyzable stretch ends at screen-off.
- Frames resume at `ACTION_SCREEN_ON`, i.e. on the keyguard. In the V1a run, check whether A captures the secure keyguard black: if so every unlock writes an unanalyzable stretch.
- `dumpsys media_projection` and the "· compartir pantalla" line are for the device run; logcat shows `screen off; pausing frames (D55)` / `screen on; frames resume`.

## Device check (A, 2026-10-12, human)

Secure lock and Always On Display on. Screen off 7 s to 3 min 34 s (longest on the final build: 2 min 54 s): every run logged `pausing frames (D55)` then `frames resume`, never a teardown; the test video was masked 0.4 s after screen-on; `dumpsys media_projection` showed `(dev.sophiel) TYPE_SCREEN_CAPTURE`; Status showed protection on; PIN Stop removed the masks. Found and fixed during the check: masks stayed visible on Always On Display while paused (00b1a9e hides them while the screen is off).

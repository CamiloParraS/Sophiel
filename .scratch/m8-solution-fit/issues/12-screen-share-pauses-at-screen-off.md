# 12: Screen share pauses at screen-off below API 35

**What to build:** On devices below API 35 (A), screen-off no longer ends the screen share: frames are acquired and closed unread, and resume at screen-on on the same session, so there is no consent dialog after unlocking. API 35+ keeps today's teardown (amends D18, per ticket 10).

**Blocked by:** 11

**Status:** ready-for-agent

- [ ] Below API 35, screen-off pauses frames; every acquired `Image` is still closed
- [ ] Screen-on resumes the same share; `dumpsys media_projection` shows the session
- [ ] API 35+ behaviour unchanged
- [ ] Switch off: M6 behaviour (share ends at screen-off). When the share ends with the switch off, `CaptureModeMachine` clears `protecting`: write that to `ProtectionStore` too, or a stale `true` resumes protection without a Start (ticket 11 note)
- [ ] Checked on A with SPEC V1a steps (screen off ~10 s and ~2 min, secure lock; test video masked after unlock)
- [ ] Ends buildable

# 26: Trusted apps

**What to build:** The Apps screen gains "De confianza" (D58), with a one-line warning that trusting a browser lets anything through. While only trusted app windows are on screen, masks drop at once and inference stops: on screen share frames are acquired and closed unread (the VirtualDisplay stays), on accessibility the shots stop. A split screen or PiP counts as trusted only if every app window is. Scanning resumes on the next window change to a non-trusted app. Trusted apps write nothing to the Log.

**Blocked by:** 25

**Status:** built 2026-10-15, device check on A pending

- [x] "Is everything in front trusted" decided from the window list, pure logic, JVM-tested (split screen, PiP, launcher, system UI)
- [x] Masks drop (built; logcat "trusted app in front: scanning stops" / "scanning resumes", not yet seen on device) and inference stops on both capture modes; logcat shows it stopping and resuming
- [x] Excluded apps cannot be trusted
- [ ] Checked on A: a trusted gallery shows no masks; split screen with one non-trusted app is still scanned
- [x] Ends buildable

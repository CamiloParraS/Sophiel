# 26: Trusted apps

**What to build:** The Apps screen gains "De confianza" (D58), with a one-line warning that trusting a browser lets anything through. While only trusted app windows are on screen, masks drop at once and inference stops: on screen share frames are acquired and closed unread (the VirtualDisplay stays), on accessibility the shots stop. A split screen or PiP counts as trusted only if every app window is. Scanning resumes on the next window change to a non-trusted app. Trusted apps write nothing to the Log.

**Blocked by:** 25

**Status:** ready-for-agent

- [ ] "Is everything in front trusted" decided from the window list, pure logic, JVM-tested (split screen, PiP, launcher, system UI)
- [ ] Masks drop and inference stops on both capture modes; logcat shows it stopping and resuming
- [ ] Excluded apps cannot be trusted
- [ ] Checked on A: a trusted gallery shows no masks; split screen with one non-trusted app is still scanned
- [ ] Ends buildable

# 01: M3.5 gate: re-verify capture on device

**What to build:** Nothing to code. The rescope PR is merged into `main` (the other branches are already fully merged), then the human re-runs the capture checks on a real phone, because the code-quality pass (D20) and the resume notification (D21) were never tested on a device. SPEC §5 says tile work does not start until this passes.

**Blocked by:** None (can start immediately). Prerequisite: the rescope PR is merged and a device is attached.

**Status:** ready-for-human

- [ ] The rescope PR is merged into `main` (it carries the cache fix and the resume notification).
- [ ] M3 V1–V7 pass on Device A and Device B (permission flow, denied overlay, cancelled consent, rotation, status-bar stop, 10-minute run, secure-app "protected content").
- [ ] D21 check passes: start protection, screen off, unlock, the "Paused — tap to resume" notification appears, tap it, fresh consent, RUNNING. Stopping from the app shows no such notification.
- [ ] Any failure is reported (use `/diagnosing-bugs`), not worked around; M4 stays blocked until fixed.

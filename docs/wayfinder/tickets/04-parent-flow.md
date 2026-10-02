# Parent flow and screens  `wayfinder:grilling`
Assignee: Camilo Parra (claimed 2026-10-01)
Blocked by: none
Status: CLOSED

## Resolution

Screens: **Status** (open to it, no PIN), **Setup wizard**, **PIN unlock**, **Settings**, **Log**, hidden **Debug menu**.
- Status: protection on/off, active preset, "Settings (parent)". Starting needs no PIN. Weakening protection (stop, change preset/sensitivity, reveal a mask, view log) needs the PIN. Unlock lasts ~2 min.
- Wizard order: create 4-6 digit PIN (confirm) -> overlay permission -> notification permission -> preset + sensitivity -> start (capture consent). No PIN recovery: reset = clear data/reinstall; say so in the wizard and the limitations.
- Stopping: no Stop action in the notification; in-app stop needs the PIN. The system screen-share indicator can still end it (unblockable, documented limitation). "Paused, tap to resume" (D21) stays. The earlier Stop was a debug aid.
- Sensitivity: Strict / Normal / Relaxed, mapped to thresholds in code. Preset is a separate Light/Balanced toggle with a one-line trade-off. Raw slider only in the debug menu.
- Log (local, scalars only, no frames, no app names): entries for MASKED (time, tiles masked, score band), PROTECTION_ON, PROTECTION_OFF (with reason: user stop / screen off / system ended), UNANALYZABLE (FLAG_SECURE black frame). **Retained for 7 days, rolling prune** (replaces the 200-entry cap). "Clear log" behind the PIN. Summary at the top ("N masks today"). Gap when the app is killed or the phone reboots is inferred on next start, since no OFF entry can be written.
- Debug menu (Test Feed, debug pill, raw slider): unlocked by tapping the version label 7 times + PIN.
- Platform limits are known and documented (FLAG_SECURE, single-use consent, Android 15+ secure lock end, D16 notification hiding); the UI surfaces them instead of hiding them.

## Question

What are the screens and flows? At minimum: first-run setup (PIN, consent, overlay permission), start/stop protection, sensitivity setting, preset choice (Light/Balanced), PIN-gated settings, and a local blocked-events log. Decide the screen list, what the child sees vs. what the parent sees, and what the log records (scalars only: no frames).

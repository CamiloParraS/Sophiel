# 07: Escalation and per-app rules

Type: grilling
Status: resolved
Blocked by: 06

## Question

Decided at charting: (a) escalation — N masking episodes in one app within M minutes blocks that app for X minutes; (b) per-app rules — always blocked, and trusted (never scanned). Schedules stay in the fog. Decide (gap 4):

- N / M / X: Parent-set, or tied to Strict / Normal / Relaxed defaults? Can the Parent turn escalation off?
- What "blocked" looks like and does: sent Home + a full-screen "Bloqueada por Sophiel · vuelve en 12 min" screen; can the Parent lift it with the PIN early?
- Trusted apps: does the pipeline skip capture/inference entirely while one is in front (battery win), and what about the masks already up when switching to one?
- Picking apps: list of launchable apps (`QUERY_ALL_PACKAGES`), search, where it lives in Settings (behind the door, D38).
- Can Sophiel, the launcher or system UI be blocked/trusted? (No for Sophiel and Settings, presumably.)
- New Log kinds: app blocked (by escalation / by rule).

## Answer

Grilled with the human 2026-10-12. Terms in `CONTEXT.md`: **Escalation** (new), **Blocked app** and **Trusted app** (sharpened), Log entry kinds gain app blocked + blocked open.

- **Escalation:** one fixed rule, no tuning: **3 masked episodes with the same Episode app within a rolling 10 min → that app blocked 15 min.** One switch "Bloquear apps que muestran contenido explícito repetidamente", default on. Unanalyzable stretches never count (FLAG_SECURE apps would be blocked for being secure); blank app, trusted apps and excluded apps never count. The block fires at once, even with the app in front (Home + block screen). The count restarts after a block ends. Not tied to Cuidado (it already decides what counts as a hit).
- **Enforcement:** accessibility service only. `typeWindowStateChanged` from **all** packages (the guard's `packageNames` filter from ticket 05 is dropped; still state changes only, no content events). Blocked app comes to the front → `GLOBAL_ACTION_HOME` + Sophiel's block screen. Without the service: no blocking and no trusting; the Apps rows show disabled with "Necesita el servicio de accesibilidad", and Status says so. No `UsageStats` polling fallback.
- **When enforced:** only while Protection is on, and also during an Unlock (an Unlock is for settings, not apps). Escalation end times are wall-clock and survive reboot (the service rebinds at first unlock and re-checks). A PIN Stop clears active escalation blocks.
- **Block screen** (full-screen Sophiel activity): app icon + label; escalation: "Bloqueada por Sophiel · vuelve en N min"; rule: "El Padre bloqueó esta app". "Ir al inicio" button; Back goes Home. Escalation blocks have a small "Desbloquear con PIN" (existing PIN pad, D38 door): a correct PIN ends the block and resets the count. Rule blocks have no PIN button; the Parent changes the rule in Settings. No temporary allowance.
- **Trusted app in front:** masks drop immediately and inference stops. Screen share: frames are acquired and closed unread (VirtualDisplay stays up, no consent churn). Accessibility mode: screenshots stop. Split screen / PiP: trusted only if every app window is trusted. Scanning resumes on the next window change to a non-trusted app. Picker warns in one line that trusting a browser lets anything through.
- **Picker:** new "Apps" row in Settings (already behind the door, no new gate). One screen: escalation switch at top, search field, launchable apps sorted by label (changed ones pinned first), each Normal / Bloqueada / De confianza. App list from ticket 06's `<queries>` launcher intent, no `QUERY_ALL_PACKAGES`. **Excluded** from both lists and from escalation: Sophiel, Settings, the default launcher, the default dialer (emergency calls).
- **Log kinds:** "App bloqueada" (escalation): time + app + minutes, one per block. "Apertura bloqueada": time + app + reason (rule / escalation), same 60 s merge as Tamper attempt. Trusted apps write nothing. Their look in the Log and report is ticket 08's; so is whether Status shows active blocks.
- **Limitations** (LIMITATIONS/§7): a blocked app already in picture-in-picture keeps playing until closed (`GLOBAL_ACTION_HOME` doesn't close PiP; masking still covers it); a blocked app's notifications aren't blocked; plus the guard's tap race, safe mode, adb, turning the service off via the volume-key shortcut.
- **Prior art (from the grilling, not researched):** Digital Wellbeing / Family Link suspend apps via `setPackagesSuspended()`, which needs `SUSPEND_APPS` (system) or Device Owner/Profile Owner, both out of reach. Third-party blockers (ActionDash, StayFocusd, AppBlock) use the same accessibility + Home + block-screen approach as here. Daily usage-time limits are not in this map.
- **For the SPEC write-up:** service config changes from ticket 05's (no `packageNames`); new prefs: escalation switch, per-app rule map, escalation end times; new block-screen activity; D39 Log kinds gain the two above; no new permission, manifest diff is the activity only.

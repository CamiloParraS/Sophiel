# 07: Escalation and per-app rules

Type: grilling
Status: open
Blocked by: 06

## Question

Decided at charting: (a) escalation — N masking episodes in one app within M minutes blocks that app for X minutes; (b) per-app rules — always blocked, and trusted (never scanned). Schedules stay in the fog. Decide (gap 4):

- N / M / X: Parent-set, or tied to Strict / Normal / Relaxed defaults? Can the Parent turn escalation off?
- What "blocked" looks like and does: sent Home + a full-screen "Bloqueada por Sophiel · vuelve en 12 min" screen; can the Parent lift it with the PIN early?
- Trusted apps: does the pipeline skip capture/inference entirely while one is in front (battery win), and what about the masks already up when switching to one?
- Picking apps: list of launchable apps (`QUERY_ALL_PACKAGES`), search, where it lives in Settings (behind the door, D38).
- Can Sophiel, the launcher or system UI be blocked/trusted? (No for Sophiel and Settings, presumably.)
- New Log kinds: app blocked (by escalation / by rule).

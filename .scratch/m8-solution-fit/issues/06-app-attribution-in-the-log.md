# 06: App attribution in the Log

Type: grilling
Status: open
Blocked by: 04

## Question

Decided at charting: record which app was on screen (package + label, never window text), amending D39 ("no app names") and D32 ("no events"). Decide:

- How the foreground app is chosen per masking episode (from ticket 04's facts): split screen, PiP, launcher, our own app, unknown.
- Does a change of app split a masking episode (D39), or is an episode tagged with the first / every app it touched?
- Log schema change (`log.csv` columns, migration of an existing file or just start fresh), and which other kinds carry an app (unanalyzable, tamper attempt).
- Log screen: per-app breakdown ("Instagram · 7 hoy") and what the summary line becomes.
- Privacy line for LIMITATIONS/README: the Log now names apps; still no pixels, no text.

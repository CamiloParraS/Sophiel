# 06: App attribution in the Log

Type: grilling
Status: resolved
Blocked by: 04

## Question

Decided at charting: record which app was on screen (package + label, never window text), amending D39 ("no app names") and D32 ("no events"). Decide:

- How the foreground app is chosen per masking episode (from ticket 04's facts): split screen, PiP, launcher, our own app, unknown.
- Does a change of app split a masking episode (D39), or is an episode tagged with the first / every app it touched?
- Log schema change (`log.csv` columns, migration of an existing file or just start fresh), and which other kinds carry an app (unanalyzable, tamper attempt).
- Log screen: per-app breakdown ("Instagram · 7 hoy") and what the summary line becomes.
- Privacy line for LIMITATIONS/README: the Log now names apps; still no pixels, no text.

## Answer

Grilled with the human 2026-10-12. Term in `CONTEXT.md`: **Episode app** (new); Log entry kinds list gains tamper attempt.

- **Source:** at episode start, the topmost `TYPE_APPLICATION` window under the highest-scoring masked tile (`MaskWindowService` window list, the lookup `appWindowShot` already does); package from `window.root.packageName` (one field, never text, titles or nodes beyond the root). Works on A (API 33): listing windows needs no API 34. No events (D32 untouched).
- **No accessibility service** (D46 start): app field blank, Log reads "App desconocida". No `UsageStatsManager` fallback.
- **Edge cases:** split screen / PiP resolved by the strongest tile; no app window under it (system UI only) → blank; launcher counts as an app with its own label; Sophiel itself → blank.
- **Episodes not split by app change:** an episode keeps its first app (`ponytail:` comment naming the ceiling: a mask run that carries across an app switch within 3 s is counted against the first app). D39 unchanged otherwise; Precise needs no special case.
- **Schema:** package appended as the last field of `MASKED` and `UNANALYZABLE` (stamped at stretch start, D45). Labels resolved at read time via `PackageManager` (follow the phone's language, no commas in the CSV); uninstalled app shows its package. Old lines without the field read as blank: no migration. ON/OFF/Tamper attempt carry no app.
- **Visibility:** `<queries>` for the MAIN/LAUNCHER intent, not `QUERY_ALL_PACKAGES`; ticket 07's app picker reuses it.
- **Log screen (06's share only):** each masked/unanalyzable row shows the app label; summary line "7 hoy · más en Instagram (4)". Per-app breakdown and trends are ticket 08's.
- **Privacy line** (LIMITATIONS/README): "The Log names the app that was on screen when something was hidden: the app's name, never what was in it. No pixels, no text, no window titles. It stays on the phone for 7 days."
- **For the SPEC write-up:** D39 amended ("no app names" → episode app); `EventLog` doc comment "no app names" goes; manifest diff is the `<queries>` block only.

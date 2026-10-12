# 08: Log upgrades and the shared report

Type: prototype
Status: resolved
Blocked by: 05, 06, 07

## Question

Decided at charting: both (b) a richer Log screen and (a) a PIN-gated "Send report" that builds a summary and opens the Android share sheet (Sophiel never touches the network; the receiving app sends it). Prototype what they look like and contain (gap 3):

- Report period (today / 7 days), contents (masks per day, per app, off-periods and reasons, tamper attempts, blocks), and format: plain text (works in any chat), or an image/PDF card (needs a FileProvider; the file holds scalars only, never pixels from capture).
- Log screen: trends (per-day bars), per-app breakdown, tamper and block entries; what stays from D45/M6.
- Whether Status shows active blocks ("Instagram bloqueada · 12 min"); ticket 07 only stores block end times.
- Spanish first, English second (D43).

Prototype asset: a rough HTML/Compose mock of the report and Log screen, linked here.

## Answer

Prototype `mockups/log-report.html`, kept on branch `proto/m8-log-report` (794a92c), not main. Human picked 2026-10-12:

- **Log screen = A's dashboard + C's day grouping.** Top: summary chips (tapados · más en <app>, intentos, bloqueos), 7-day bar chart of masked episodes per day, "Por app" list with bars (masked count per app, 7 days). Below: the Todo / Tapados / Otros filter and rows grouped under day headers with the day's total ("Hoy · 8 tapados"). Rows keep time, reason and kind chip; new kinds (App bloqueada, Apertura bloqueada, Intento de apagar) use the same row. No per-app drill-down screen (B dropped). D44/D45 otherwise stand.
- **Report:** "Informe" button on the Log header, behind the existing PIN door. Bottom sheet with a Hoy / 7 días selector, a plain-text preview, and "Compartir…" opening the Android share sheet via `ACTION_SEND` text/plain. Text only: no FileProvider, no image card (future work). Contents: period, date, counts of tapados / sin revisar / protection off (times) / tamper attempts / apps bloqueadas, top 3 apps by masked count, per-day counts for 7 días, and the line "Solo cifras. Sophiel no guarda imágenes." No device name (Sophiel has none); no new permission, C1 intact.
- **Status (S1):** one orange banner line per active escalation block, "Instagram bloqueada · 12 min", counting down; rule blocks are not shown (permanent, visible in Settings "Apps"). Status reads the stored block end times.
- **For the SPEC write-up:** Spanish first, English in `values-en/` (D43); report text comes from string resources; "Hoy" = since local midnight; the report counts only what is still in the 7-day Log.

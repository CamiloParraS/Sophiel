# 08: Log upgrades and the shared report

Type: prototype
Status: open
Blocked by: 05, 06, 07

## Question

Decided at charting: both (b) a richer Log screen and (a) a PIN-gated "Send report" that builds a summary and opens the Android share sheet (Sophiel never touches the network; the receiving app sends it). Prototype what they look like and contain (gap 3):

- Report period (today / 7 days), contents (masks per day, per app, off-periods and reasons, tamper attempts, blocks), and format: plain text (works in any chat), or an image/PDF card (needs a FileProvider; the file holds scalars only, never pixels from capture).
- Log screen: trends (per-day bars), per-app breakdown, tamper and block entries; what stays from D45/M6.
- Spanish first, English second (D43).

Prototype asset: a rough HTML/Compose mock of the report and Log screen, linked here.

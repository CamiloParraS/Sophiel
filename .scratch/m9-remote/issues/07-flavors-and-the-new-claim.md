# 07: Flavors and the claim that replaces C1

Type: grilling
Status: resolved
Blocked by: 01, 03, 04

## Question

Pin the build split and what Sophiel now promises.

- `offline` / `remote` flavors: which manifest, dependencies and code each carries; role choice and Pairing absent from `offline`; default install target for Device A.
- Verification: `aapt` must still print nothing for `offline`; what checks `remote` (only `INTERNET` added, no other new permission)?
- The new privacy claim for `remote` (server stores only what ticket 04 lists, cannot read messages, keeps no Log) and the §7 limitations (offline Child phone can't answer, Doze latency from 02, server sees metadata).
- `CONTEXT.md`'s Report entry ("Sophiel itself never sends anything") holds for `offline` only: reword.
- The main manifest removes `INTERNET` today; `remote` adds it back plus `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (ticket 02). Google code scanner metrics (ticket 03): accept and disclose, or the CAMERA-permission fallback.

## Answer

Human decisions, 2026-10-21 (two grilling rounds).

- **Split:** one flavor dimension `net`: `offline` / `remote`, same `applicationId` `dev.sophiel` (one installed at a time). Main manifest names neither network permission; `src/offline/AndroidManifest.xml` keeps the `tools:node="remove"` lines for `INTERNET` and `ACCESS_NETWORK_STATE`; `src/remote/AndroidManifest.xml` adds `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Code:** OkHttp, ZXing, code scanner are `remoteImplementation`. All Remote code (role choice, login, Parent screens, Pairing, socket) in `src/remote/kotlin`; `main` reaches it through one same-named object, empty in `src/offline/kotlin`. `offline` opens straight into today's app, no role choice.
- **Default target:** `installRemoteDebug` on Device A. Hard rule 6 becomes: `assembleOfflineDebug` and `assembleRemoteDebug` both build, and the default install launches.
- **Checks:** `offline`: today's `aapt … | grep -i internet` prints nothing. `remote`: new V-item + SPEC §8 command diffing `aapt dump permissions` of both APKs; the diff must be exactly the three permissions above.
- **Code scanner metrics:** accept and disclose (no CAMERA fallback).
- **C1 rewording:** "The `offline` flavor MUST NOT declare `INTERNET` in any manifest (debug and test included). The `remote` flavor adds exactly `INTERNET`, `ACCESS_NETWORK_STATE` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, nothing else (D76)." CLAUDE.md hard rule 1 to match.
- **`remote` privacy claim** (SPEC, `docs/LIMITATIONS.md`, and one short in-app line on Parent sign-up and on the Child phone's Pairing screen before the QR): "In the Remote build, the server keeps only accounts (email, password hash), sessions and pairings. It passes messages along without being able to read them (end-to-end encrypted with a key that never leaves the two phones), and never stores the Log, app lists, phone names or commands. No frame or screen content ever leaves the Child phone. The Parent phone's QR scanner (Google) sends anonymous usage data to Google."
- **Accessibility service off (amends ticket 02):** the socket is also held whenever the process is alive for another reason (screen-share protection's FGS, or the app open). In that state the Child phone answers Log fetch and Stop; Start is rejected (ticket 06); app rules are saved and take effect when the service returns. Every reply carries an "accesibilidad apagada" flag; the Parent card shows it and disables Start. Service off and protection off = process dead = unreachable.
- **New §7 items:** (1) with the accessibility service off, the Child phone answers only while protection runs on screen share or Sophiel is open, and then only Log, Stop and app rules; otherwise unreachable, as is a phone with no connection; (2) up to ~5 min to reach the phone after a silent disconnect; (3) server and Fly's proxy see IPs, timing and which account talks to which pairing, never contents; (4) no account recovery; (5) remote Start needs accessibility mode; (6) the Parent phone needs Google Play services to scan; (7) limitation 26 becomes "In `offline`, Sophiel sends nothing; in `remote`, only the encrypted Log goes to paired Parent phones."
- **Glossary (done):** `CONTEXT.md` Report reworded ("never sends the Report itself"); added **Offline build** and **Remote build**; Parent/Child phone are Remote build installs.

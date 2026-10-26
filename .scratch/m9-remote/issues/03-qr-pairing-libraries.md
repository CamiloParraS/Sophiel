# 03: Showing and scanning the Pairing QR with the fewest dependencies

Type: research
Status: resolved
Blocked by: none
Research: branch `research/m9-qr` (b7239a2), `.scratch/m9-remote/research/03-qr.md`

## Question

The Child phone shows a QR (pairing id + 128-bit key); the Parent phone scans it. What is the lightest way to do both in Compose? Primary sources.

- Generate: ZXing `core` alone (size, Kotlin use, render to a Compose `ImageBitmap`) vs any alternative.
- Scan: Google code scanner (`play-services-code-scanner`, no CAMERA permission, needs Play services, module download), ML Kit bundled barcode + CameraX (CAMERA permission, size), ZXing embedded. Which works on A and B with least code and no new permission?
- Does any option pull `INTERNET` or other permissions into the merged manifest (it must stay out of the `offline` flavor)? Can the dependency be added to the `remote` flavor only?
- Recommend one pair (generate + scan).

## Answer

**Generate:** ZXing `core` 3.5.4: a plain Java JAR, no manifest, so no permissions; `QRCodeWriter().encode()` gives a `BitMatrix` drawn on a Compose `Canvas`.
**Scan:** Google code scanner `play-services-code-scanner` 16.1.0: no CAMERA permission, ~5 lines (`GmsBarcodeScanning.getClient().startScan()`), works on A and B, needs Play services; add the `barcode_ui` meta-data so the module installs with the app.
**Catch:** the scanner pulls `transport-backend-cct`, which merges `INTERNET` + `ACCESS_NETWORK_STATE` and sends ML Kit usage metrics to Google (terms require telling users). So both deps are `remoteImplementation`, scanner calls live in `src/remote/kotlin`, and the `remote` privacy text (ticket 07) must disclose it. Do not strip INTERNET with `tools:node="remove"`.
**Fallback:** zxing-android-embedded 4.3.0 (CAMERA, no INTERNET, no Play services); maintenance and Android 14 unverified. ML Kit bundled + CameraX rejected (CAMERA, ~2.4 MB, still INTERNET).
**Open, for the build:** `aapt` on both flavor APKs, APK size after R8, first scan with no network before the module is installed.

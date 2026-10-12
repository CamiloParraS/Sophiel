# 01: Can whole-display accessibility screenshots replace or back up MediaProjection?

Type: research
Status: resolved
Blocked by: none
Research: branch `research/a11y-screenshot-capture`, `.scratch/m8-solution-fit/research/01-accessibility-screenshot-capture.md`

## Question

Gather the platform facts for using `AccessibilityService.takeScreenshot(displayId, executor, callback)` (whole display, API 30+) as a capture source, so ticket 03 can decide hybrid vs replace (human leans hybrid: MediaProjection when started, screenshots as fallback). Primary sources: AOSP / developer.android.com, AOSP source for the rate limit.

- Minimum API, required service config (`canTakeScreenshot`), and whether it works on Device A (API 33, Samsung One UI 5) and B (Android 14+).
- Rate limit: exact interval (believed 333 ms, `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`), per-service or global, and whether it differs from `takeScreenshotOfWindow` (D35 already uses that; does the limit share a budget with it?).
- Output: `HardwareBuffer` + `ColorSpace`; cost to get a software `Bitmap` at our 360 px short side (copy, downscale), and whether it can feed the existing tile pipeline without a frame ever being persisted (C3).
- What it captures: does it include our own mask windows (accessibility overlay and 0.79 app overlay) — the feedback loop — and status/nav bars? `FLAG_SECURE` windows (black? error?). Lock screen / keyguard.
- Lifecycle: no consent dialog, no screen-share chip? Works right after boot once the service rebinds (no user interaction)? Works while screen is locked/off? Any foreground-service requirement?
- Android 15/16 changes (sensitive-content protection during sharing, D16) that touch accessibility screenshots.
- Prior art: does any open-source app (e.g. accessibility-based screen readers or blockers) use it as a periodic capture loop, and what rate do they reach?

Output: a findings file linked from this ticket, with a one-paragraph "feasible on A? on B? expected fps and latency" verdict.

## Answer

Findings: branch `research/a11y-screenshot-capture` (99377ae), `.scratch/m8-solution-fit/research/01-accessibility-screenshot-capture.md`. AOSP 13-16 + CTS; Samsung behaviour inferred, see its "Unverified" list.

- **Works on A and B.** API 30+, `canTakeScreenshot` already declared in `mask_window_service.xml`. No consent dialog, no screen-share chip, no foreground service, no D16 redaction. The service rebinds by itself after the first unlock after a reboot.
- **Rate: 333 ms per service** (a shot must reach system_server at least 334 ms after the previous request). Separate from `takeScreenshotOfWindow`'s per-window budget, so D34/D35 window shots don't compete. Practical ~2.5 fps (400 ms spacing), ceiling ~3 fps.
- **Output:** full-resolution `HardwareBuffer` (sRGB or Display P3, ~10 MB on A); getting a 360 px bitmap costs a GPU-to-CPU copy plus a scale. In memory only, so C3 holds as long as the buffer is closed. On API 33/34 system_server takes the shot synchronously on its main thread.
- **Our masks are in the shot** (accessibility window and the 0.79 overlay); no public way to exclude a window, so probing stays as today. `FLAG_SECURE` comes out black with no error, so the black-frame check still works. On Android 15+, while MediaProjection runs, sensitive-content apps come out black in accessibility shots too.
- **Latency estimate 250-600 ms from content to mask**; probe exposure on A ~350-450 ms vs 61-110 ms today. B avoids most of it with window-shot peeks. **Supports hybrid** (MediaProjection when started, screenshots as fallback).
- No open-source app uses it as a periodic capture loop. Nothing was measured on device: ticket 02 measures shot latency and copy cost on A.

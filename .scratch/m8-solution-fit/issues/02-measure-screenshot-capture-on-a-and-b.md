# 02: Measure accessibility-screenshot capture on A and B

Type: task
Status: resolved
Blocked by: 01

## Question

Before choosing the capture backend, get real numbers. A throwaway spike branch (`spike/a11y-capture`) where `MaskWindowService` loops `takeScreenshot` into the existing tile pipeline, with timing logs. The human runs it on A and B (HITL: device runs).

Record, per device: achieved screenshots/s, screenshot-call-to-bitmap ms, frame-to-mask ms (same content as the M5/D36 runs), probe exposure median/worst, whether masks appear in the screenshot, behaviour after a reboot with no app opened, behaviour on a `FLAG_SECURE` app. Compare against the D36 / D50 MediaProjection numbers.

Resolved when the numbers are in this ticket's Answer. Skip or shrink if ticket 01 shows it can't work on A.

## Answer

Spike: branch `spike/a11y-capture` at `65fe40a`, never to be merged. `MaskWindowService` starts a `CaptureSession` with no projection as soon as it binds; that session takes a display shot every 400 ms, copies it to a software bitmap, scales and crops it to the capture size, and feeds the unchanged `decideFrame`/`onFrame` tile path. Human runs on 2026-10-11 with the test video (`Movies/SophielTest`), Sophiel never opened, Start never pressed. A: Balanced + Relaxed. B: **Thorough + Strict** (not the D36 settings). Logs were summarised with a throwaway script.

**Run 1: probe timeout 300 ms (as shipped), no peeks. It does not work.** A probe lifts the mask and waits at most 300 ms (`PROBE_VALID_MS`) for a frame, but the next shot is up to 400 ms away. So 630 of 634 probes on B and 24 of 24 on A timed out and re-masked. Every masked tile blinked open for 300 ms about every 2 s (the human saw "a lot of flicker"), and on B the launcher stayed fully masked ("stuck").

**Run 2: probe timeout 700 ms (spike), peek under mask on for B.** From 21:00, ~5 min per device, 8 rotations each, no failures, no crashes. The human called the flicker "minimal and understandable".

| | A (probes) | B (peeks) | MediaProjection, D50 (A / B) |
|---|---|---|---|
| Shots/s achieved | 2.49 (gap 402 ms median, 420 worst) | 2.49 (402 / 414) | continuous |
| Shot call → buffer, median / p90 / worst | 41 / 57 / 131 ms | 25 / 43 / 83 ms | — |
| Full-res copy (readback), median / worst | 48 / 88 ms | 44 / 97 ms | — |
| Scale + crop to 360 px, median / worst | 27 / 86 ms | 20 / 60 ms | — |
| Frame → mask, median / worst | 217 / 389 ms (n=13) | 212 / 430 ms (n=45) | B 70 / 168 (perf fixes) |
| Still-screen confirm (D51), median / worst | 354 / 386 ms | 370 / 471 ms | ~210 ms after the sweep |
| Probe exposure, median / p90 / worst | **436 / 519 / 565 ms** (n=66; 40 → CLEAR, 26 → MASKED) | 463 ms (n=9, one peek lifted to probes) | **61 / 88**, **66 / 101** |
| After rotation (probe within 2.5 s) | 376-520 ms, n=36 | peeks, plus one batch of 9 probes at 408-465 ms | 86/214, 81/159 |
| Peeks (judged under the mask, nothing exposed) | n/a (API 33) | 171, median / p90 / worst 131 / 377 / 603 ms | — |
| Shot failures | 0 | 0 (run 1: 4 × error 3, rate limit) | — |

Content-to-mask latency is frame → mask plus a 0-400 ms wait for the next shot, so ~0.2-0.8 s. MediaProjection's is ~0.1-0.25 s.

Facts:
- **Masks appear in the shot**, as research said. Run 2's probe frames showed our mask 10 times on A.
- **Colour space:** A returns sRGB, B returns **Display P3**. The spike passed the P3 bitmap straight through `copy()`, and nothing looked wrong. Whether P3 skews scores was not checked.
- **Screen off:** shots still succeed but come back black (`protected content` every time), and Android timers stretch the loop to a shot every ~1.5-2.7 s. Stop the loop on screen off.
- **The service turned off once, on A** (20:15:01, run 1), right after a rotation, with Bixby Vision and then Samsung Account opening. The human thinks they caused it. It did not happen again in ~23 min of run 2 on A.
- Shot round-trip on B (Android 16, async capture) is no slower than on A (13, synchronous on system_server's main thread).
- **Cost on A:** the copy + scale (~75 ms median) replaces MediaProjection's 360 px decode, at 2.5 frames/s instead of up to 12.5/s, so less CPU overall (not measured).

Verdict for ticket 03:
- The screenshot backend works without consent on both devices. It needs a **probe timeout longer than the shot interval** (700 ms worked).
- On **B, with peeks, it is close to MediaProjection.** Masked content stays hidden, at the cost of ~3× slower first masking.
- On **A it exposes ~0.45 s per probe**, about 7× MediaProjection.
- That supports the hybrid: MediaProjection when the Parent starts it, screenshots when it isn't running (after reboot, no consent). On A, the screenshot fallback is noticeably weaker; LIMITATIONS should say so.

- **Reboot (human, both devices):** after a reboot and an unlock, with Sophiel never opened, the masks come back on the test video. The boot log was not read: adb dropped with the reboot.
- **`FLAG_SECURE`: not tested on purpose.** CTS guarantees a black shot, and the black-frame path is the same one MediaProjection uses (both runs logged `protected content` frames). Make it an M8 verification item.

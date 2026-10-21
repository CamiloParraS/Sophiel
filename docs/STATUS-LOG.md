# STATUS-LOG.md

Archived "Current state" history from CLAUDE.md (as of 2026-10-18). Not loaded by default; CLAUDE.md keeps only the live summary.

```
CURRENT MILESTONE:  M8 — Solution fit, SPEC WRITTEN and reviewed 2026-10-12 (D53: freeze lifted
                    for M8 only, ~2 weeks; then UI polish, then M7). SPEC §5 "M8 — Solution fit" + new §4.7
                    capture modes; decisions D54-D60. Planning map done: .scratch/m8-solution-fit/map.md
                    (all 10 tickets resolved). Build order: 1 capture modes (hybrid, D54/D55), 2 tamper guard
                    (D56), 3 episode app in the Log (D57), 4 escalation + per-app rules (D58), 5 Log dashboard
                    + text report (D59). Stretch: schedules (D60, unspecified).
                    M6 done 2026-10-11 (D52). M5 done 2026-10-05 (D36).
STATUS:             M5 verified on A and B (D36): V1-V5 pass; probe exposure median/worst
                    A 146/306 ms, B 85/188 ms; after rotation A 220/304, B 129/210.
                    Release Start gate checked on B only. M5 decisions: D27-D36.
                    Scope additions in M5: D32 opaque accessibility mask window, D34 peek under
                    mask (debug chip, Parent setting in M6), D35 Precise preset (B, Android 14+).
                    Ticket 12 also fixed VerdictCache caching dHash 0 (black tiles inherited
                    an EXPLICIT verdict).
BLOCKED ON:         Nothing.
NEXT:               M8 deliverable 1 (capture modes) done: ticket 19 closed 2026-10-14. D61 (built, checked
                    on B): a stretch found after a reboot with accessibility off starts at the boot.
                    Deliverables 2-5 are tickets 20-31 in .scratch/m8-solution-fit/issues/, one deliverable
                    at a time, each ending in a device check: 20-23 guard, 24 episode app, 25-28
                    escalation + rules, 29-31 Log + report + M8 sign-off. 20 done (B captures). 21 done
                    (accessibility page guard, ui/BlockActivity; checked on A 2026-10-14). 22 built, 23 (guard
                    verification, V4) done 2026-10-15. 24 (episode app, D57) done 2026-10-15 (V5 passed).
                    Deliverable 4 (D58): 25 done 2026-10-15 (rule blocks, Apps screen, Apertura bloqueada;
                    checked on A). 26 trusted apps built, 27 escalation done (checked on A 2026-10-15). 28 = V6 passed on A and B
                    (2026-10-15); deliverable 4 done. Next: deliverable 5 (Log dashboard + report, tickets 29-31). After M8: UI polish, then M7: soak on A and B, feel numbers per preset, LIMITATIONS.md (from SPEC §7), README,
                    demo runbook + video. M7 has no tickets yet. M6 history below, map at
                    .scratch/m6-parent-app/map.md (decisions D38-D45).
                    Build tickets done: 09 settings, 10 cover path, 12 PinStore + unlock, 13 event log,
                    15 theme/type/parts/strings, 16 PIN pad and the door, 17 Status, 18 Log (checked on A; Status "Ver en Historial" opens it behind the door). 11 done (checked on A 2026-10-11:
                    service-on swap seamless, service-off flickers, SPEC §7 limitation 22). V2 (Log) checked. D46: release starts without the accessibility service (0.79 overlay, Status
                    warns). 19 done (checked on A and B). D47: debug-pill switch in the debug menu
                    (pill still debug-signed only). D48: Thorough 3x3 preset ("Reforzado").
                    20 wizard done (V3 passed on A and B). D49: debug builds can rerun the wizard
                    from the debug menu; D47 amended: debug pill off by default. 21 = M6
                    verification done (D52).
                    Perf fixes (2026-10-07, branch perf/frame-path): masks draw before the Log write, Log appends
                    no longer read the file (pruned at start), reused tile input buffer and padded frame
                    bitmap. Checked on B (same content, before/after): frameToMaskMs median/worst
                    101/303 -> 70/168 ms; probe exposure unchanged (median 123 -> 126). NOT
                    inference-bound: on A a probe tile judges in 4-6 ms (mostly cache hits); ~75-80 ms
                    of exposure is waiting for a frame: throttle/busy lane dropped the lift frame.
                    Fixed by holding frames while a probe waits (same content, both devices):
                    probe timeouts A 21/53 -> 0/44, B 25/66 -> 0/51; exposure median A 246 -> 90,
                    B 163 -> 81 ms; worst A 310 -> 252, B 359 -> 229. Then (35bcd28) skip inference on
                    probe tiles still masked + stop CLEAR tiles when a probe starts mid-frame: A probe
                    judging p90 107 -> 57 ms; B (same content) exposure med 81 -> 107, wait med 71 -> 87, cause
                    not found (same model load; more probes and EXPLICIT verdicts in less time);
                    mid-frame stop fired 1x (A), 3x (B). No timeouts, no crashes. Then D50: a waiting probe
                    skips the 80 ms throttle (ea2d89f): probe exposure A 61/88, B 66/101 ms; after
                    rotation A 86/214, B 81/159. All on branch perf/frame-path, not merged.
                    D51 (437de5d): a still screen confirms a tile flagged once (Gallery on A stayed
                    exposed 3.7-7.8 s until a tap). Checked on A and B: masks ~210 ms after the sweep,
                    no tap; all 10 still-confirmed masks re-masked at their first probe (none false).
                    Last run added the test video, so its exposure numbers aren't comparable.
                    Branch review (Standards + Spec) fixes in 700f827, checked on A and B: no timeouts,
                    no crashes, Gallery masked without a tap, still-confirmed masks all correct.
                    A (same content): frameToMaskMs worst 494 -> 335 ms, but n=6/8, inconclusive;
                    exposure unchanged (103 -> 110). VerdictCache hit 60-66% of tiles on A: keep it.
                    Interpreter threads (TileBenchmark#threadCost, run with am instrument so the app
                    is not uninstalled): default, 1, 2, 4 all equal - B 38-39, A 50-51 ms/tile.
                    Crop+hash+gate is 0-1 ms/tile on B, so single-sampling tiles was dropped.
                    FEATURE FREEZE end of week 3 (end of M6), then M7.
2026-10-21         UI polish: design A ported (D75). Geist, Esmeralda, state card + bento on Status, light headers, Ajustes tiles,
                    wizard top and heroes, flat-ink block screen. Built and installed on A; Status seen, PIN sheet seen.
                    Not seen on a device: Ajustes, Apps, Historial, wizard, block screen. Not ported: see D75 "Not ported yet".
```

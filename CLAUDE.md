# CLAUDE.md

Agent entrypoint for **Sophiel**. `docs/SPEC.md` is the source of truth; where this file and SPEC disagree, SPEC wins.
Read this file, then only the slices the pointers below name. SPEC (~15k tokens) and DECISIONS (~30k) are never read whole.

---

## Current state

> Update at the end of every work session. Keep it to this block; history goes to `docs/STATUS-LOG.md`.

```
MILESTONE:  M8 Solution fit (freeze lifted for M8 only, D53). Then UI polish, then M9 Remote (specified in SPEC §5, D76-D85; map `.scratch/m9-remote/map.md`), then M7.
DONE:       M5 (D36), M6 (D52), M8 deliverables 1-4: capture modes, tamper guard, episode app, escalation + rules (V6 passed A and B).
NEXT:       M8 deliverable 5: Log dashboard + text report + M8 sign-off (tickets 29-31, D59).
IN FLIGHT:  branch feat/ui-mockup-v2, design A ported and committed (D75: Geist, Esmeralda, state card + bento, light headers). Status and PIN seen on Device A; Ajustes, Apps, Historial, wizard, block screen not yet seen.
BLOCKED ON: Nothing.
```

Tickets: `.scratch/m8-solution-fit/issues/` (map at `.scratch/m8-solution-fit/map.md`). Older history: `docs/STATUS-LOG.md`.

---

## Where to read (pointers)

| Task                                                        | Read                                                                           |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Any task                                                    | `CONTEXT.md` (glossary), SPEC §0 (operating rules)                              |
| Capture, projection, overlay, permissions, accessibility    | SPEC §4 in full, before editing                                                |
| Current milestone work                                      | that milestone's section in SPEC §5 (M8 is "M8 — Solution fit") + its ticket    |
| Inference, gate, tile state machine, presets (`:safecore`)  | SPEC §3.3-3.6                                                                  |
| Scope question ("is this allowed?")                         | SPEC §1.2 (in) and §1.3 (out)                                                  |
| Shipping claims, limitations                                | SPEC §7                                                                        |
| Why something is the way it is, or before changing a rule   | `grep -n "^## D" docs/DECISIONS.md` for titles, then read only the matching entry |
| Commands, adb, paths                                        | SPEC §8                                                                        |

Settled decisions are final: cite the D-number, and ask before reopening one.

## Code map

`:safecore` (UI-free JVM/Android lib): model, preprocessor, skin gate, dHash, `TileMaskTracker`, `PolicyEngine`. SPEC §3.
`app/src/main/kotlin/dev/sophiel/`:

| Package     | Holds                                                                                          |
| ----------- | ---------------------------------------------------------------------------------------------- |
| `capture/`  | MediaProjection + foreground service, frame source/throttle, mask windows, capture-mode machine (SPEC §4) |
| `guard/`    | Tamper-guard pages (accessibility settings, etc.) (D56)                                        |
| `pin/`      | PIN store, flow, unlock                                                                        |
| `settings/` | Settings repo, protection store, per-app rules, escalation (D58)                               |
| `log/`      | `EventLog` (scalars only, rule 3)                                                              |
| `ui/`       | Compose screens; `*Model.kt` holds each screen's state and logic; `theme/`, `Parts.kt`, `Controls.kt` shared |
| `feed/`     | Test feed, benchmark, NudeNet experiment (debug tooling)                                       |

UI strings: `app/src/main/res/values/strings.xml` (es) and `values-en/`. Tests: `app/src/test/kotlin/...` mirrors the main tree.

---

## Hard rules

Absolute; breaking one is a defect even if the code works.

| #   | Rule                                                                                                                           |
| --- | ------------------------------------------------------------------------------------------------------------------------------ |
| 1   | **No `INTERNET` in the `offline` flavor**, in any manifest, debug and test included. `remote` (M9) adds exactly `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (C1, D83). Verifiable product claims, not preferences. |
| 2   | **No model training or fine-tuning.** Pre-trained weights only.                                                                |
| 3   | **No captured frame is ever persisted.** Frames stay in memory. Only scalars (hashes, scores, timings) may be written to disk. |
| 4   | **No image data is ever committed to git.** `eval/images/` is gitignored. Commit labels and metrics, never pixels.             |
| 5   | **Kotlin only in the app.** No Java sources. Coroutines + Flow for async; no RxJava. The relay in `server/` is TypeScript on Bun (D84). |
| 6   | **Every milestone ends buildable.** `./gradlew :app:assembleOfflineDebug :app:assembleRemoteDebug` and `:app:installRemoteDebug` must produce a launchable app before you stop working. |
| 7   | **`:safecore` stays UI-free.** It must not reference `MediaProjection`, `WindowManager`, or any Compose symbol.                |
| 8   | **No features outside `SPEC.md` §1.2.** If it isn't in scope, it isn't in scope. §1.3 lists what to refuse.                    |

---

## Traps that have already cost people days

Each is specified in detail in `SPEC.md`. Listed here because they fail _silently_ — no exception, no stack trace, just wrong behaviour.

- **Missing `image.close()`** → the `ImageReader` stalls permanently after exactly 2 frames. No error is thrown. If capture dies after two frames, this is why.
- **Ignoring `rowStride` padding** → a diagonally skewed bitmap that still looks like a plausible image in the debugger. §4.5.
- **Preprocessing mismatch** → plausible-looking but meaningless scores. Check channel order, normalization range, interpolation, tensor layout. §M1.
- **Starting `MediaProjection` before the foreground service** → `SecurityException` on API 34+. The ordering in §4.4 is load-bearing.
- **Recreating the `VirtualDisplay` on rotation** → `SecurityException`. Use `resize()` + `setSurface()`.
- **Reusing a consent `Intent`** → `SecurityException`. Request fresh every session; there is no "remember this choice".
- **Omitting `FLAG_NOT_FOCUSABLE`** on the overlay → steals input from every other app; the device feels bricked.
- **Compressed `.tflite` in assets** → the model can't be memory-mapped and fails to load. `noCompress += "tflite"`.

---

## Stop and ask the human

- The M1 parity gate fails (on-device output differs from the Python reference by more than `1e-2`). Diagnose `Preprocessor.kt` per SPEC M1; keep the tolerance and the milestone order.
- A permission flow fails on a physical device in a way SPEC §4 doesn't cover.
- A milestone exceeds 2x its time budget.
- You want to add something outside scope, or remove something in scope.
- Dependency resolution has fought you for more than 30 minutes.

State the blocker clearly; that counts as a successful outcome.

Milestones are strictly sequential: M(n+1) starts after every verification item in M(n) passes. Ask before skipping or reordering.

## Device roles (D1)

- **Device A (budget)**: primary development device and default `installRemoteDebug` target. Performance problems surface here first.
- **Device B (flagship)**: demo and headline numbers.

## Commands

`./gradlew :app:installRemoteDebug` (Device A; both flavors must assemble: `:app:assembleOfflineDebug :app:assembleRemoteDebug`). JVM: `./gradlew :app:testOfflineDebugUnitTest :app:testRemoteDebugUnitTest`. `./gradlew :safecore:test` (JVM). `adb logcat -s Sophiel:D`. Everything else: SPEC §8.
Verify the no-INTERNET claim (must print nothing): `aapt dump permissions app/build/outputs/apk/offline/debug/app-offline-debug.apk | grep -i internet` (plus the flavor diff in SPEC §8).

## Commit convention

Conventional Commits: `<type>[scope]: <description>`, optional body and footers, `!` or a `BREAKING CHANGE:` footer for breaking changes.

## Feature freeze

M8 lifted the freeze (D53); it returns when M8 ends. After that, only bug fixes for failing verification items and feel-measurement instrumentation are acceptable. If you are adding a feature after the freeze, stop and ask.

## Agent skills

Issue tracker: local markdown under `.scratch/<feature>/` (`docs/agents/issue-tracker.md`). Triage labels: `docs/agents/triage-labels.md`. Domain docs: single `CONTEXT.md`, decisions in `docs/DECISIONS.md` (`docs/agents/domain.md`).

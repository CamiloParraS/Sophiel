# CLAUDE.md

Agent entrypoint for **Sophiel**. Read this fully, then read `./docs/SPEC.md`.

`SPEC.md` is the source of truth. This file is a summary and a pointer — where the two disagree, `SPEC.md` wins.

---

## Current state

> **Update this block at the end of every work session. It is the first thing you and I both read.**

```
CURRENT MILESTONE:  M6 — Parent app (in progress, branch feat/parent-app). M5 done 2026-10-05 (D36).
STATUS:             M5 verified on A and B (D36): V1-V5 pass; probe exposure median/worst
                    A 146/306 ms, B 85/188 ms; after rotation A 220/304, B 129/210.
                    Release Start gate checked on B only. M5 decisions: D27-D36.
                    Scope additions in M5: D32 opaque accessibility mask window, D34 peek under
                    mask (debug chip, Parent setting in M6), D35 Precise preset (B, Android 14+).
                    Ticket 12 also fixed VerdictCache caching dHash 0 (black tiles inherited
                    an EXPLICIT verdict).
BLOCKED ON:         Nothing.
NEXT:               M6 Parent app, map at .scratch/m6-parent-app/map.md (decisions D38-D45).
                    Build tickets done: 09 settings, 10 cover path, 12 PinStore + unlock, 13 event log,
                    15 theme/type/parts/strings (device check left), 16 PIN pad and the door, 17 Status
                    (checked on A; B and the Precise fallback line left), 18 Log (checked on A; Status "Ver en Historial" opens it behind the door). 11 code done (device check on A
                    left). D46: release starts without the accessibility service (0.79 overlay, Status
                    warns). 19 done (checked on A and B). D47: debug-pill switch in the debug menu
                    (pill still debug-signed only). D48: Thorough 3x3 preset ("Reforzado").
                    20 wizard done (V3 passed on A and B). D49: debug builds can rerun the wizard
                    from the debug menu; D47 amended: debug pill off by default. Next: 21 = M6
                    verification (human, both devices).
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
                    A (same content): frameToMaskMs worst 494 -> 335 ms, but n=6/8, inconclusive;
                    exposure unchanged (103 -> 110). VerdictCache hit 60-66% of tiles on A: keep it.
                    Interpreter threads (TileBenchmark#threadCost, run with am instrument so the app
                    is not uninstalled): default, 1, 2, 4 all equal - B 38-39, A 50-51 ms/tile.
                    Crop+hash+gate is 0-1 ms/tile on B, so single-sampling tiles was dropped.
                    FEATURE FREEZE end of week 3 (end of M6), then M7.
```

Milestones are **strictly sequential**. Do not start M(n+1) until every verification item in M(n) passes. If you believe a milestone should be skipped or reordered, stop and ask.

---

## Before you write code

1. Read `SPEC.md` §0 (operating rules) and the section for the current milestone.
2. Read `docs/DECISIONS.md` — it records why things are the way they are. Do not relitigate a settled decision without asking.
3. If the task touches permissions, capture, or the overlay, read `SPEC.md` §4 in full first. That section is the highest-risk area of the project and it is written to be followed literally.

---

## Hard rules

These are absolute. Violating any of them is a defect, even if the code works.

| #   | Rule                                                                                                                           |
| --- | ------------------------------------------------------------------------------------------------------------------------------ |
| 1   | **No `INTERNET` permission**, in any manifest, including debug and test. This is a verifiable product claim, not a preference. |
| 2   | **No model training or fine-tuning.** Pre-trained weights only.                                                                |
| 3   | **No captured frame is ever persisted.** Frames stay in memory. Only scalars (hashes, scores, timings) may be written to disk. |
| 4   | **No image data is ever committed to git.** `eval/images/` is gitignored. Commit labels and metrics, never pixels.             |
| 5   | **Kotlin only.** No Java sources. Coroutines + Flow for async; no RxJava.                                                      |
| 6   | **Every milestone ends buildable.** `./gradlew :app:installDebug` must produce a launchable app before you stop working.       |
| 7   | **`:safecore` stays UI-free.** It must not reference `MediaProjection`, `WindowManager`, or any Compose symbol.                |
| 8   | **No features outside `SPEC.md` §1.2.** If it isn't in scope, it isn't in scope. §1.3 lists what to refuse.                    |

---

## Stop and ask the human

Do not improvise around these:

- **The M1 parity gate fails** — on-device model output doesn't match the Python reference within `1e-2`. Do not add a fudge factor, do not lower the tolerance, do not proceed to M2. The cause is almost always `Preprocessor.kt`; see `SPEC.md` M1 for the diagnostic order.
- A permission flow fails on a physical device in a way `SPEC.md` §4 doesn't cover.
- A milestone exceeds **2× its stated time budget**.
- You want to add something not in scope, or remove something that is.
- Dependency resolution has been fighting you for **more than 30 minutes**.

Stating a blocker clearly is a successful outcome. Silently working around one is not.

---

## Device roles

Do not swap these. They're recorded in `docs/DECISIONS.md`.

- **Device A (budget)** — primary development device. Default target for `installDebug`. Performance problems must surface here first.
- **Device B (flagship)** — demo and headline numbers only.

Both devices appear in the M5 success criteria at different thresholds. See `SPEC.md` §1.4.

---

## Commands

```bash
# Build + install (default: Device A)
./gradlew :app:installDebug

# Verify the no-INTERNET claim — MUST return nothing
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk | grep -i internet

# Tests
./gradlew :safecore:test              # JVM: gate, hash, policy
./gradlew :safecore:connectedAndroidTest   # device: M1 parity gate

# Diagnostics
adb logcat -s Sophiel:D
adb shell dumpsys media_projection
adb shell dumpsys activity services dev.sophiel | grep -i foreground
```

Full command reference: `SPEC.md` §8.

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

## Commit convention

The commit message should be structured as follows:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

The commit contains the following structural elements, to communicate intent to the consumers of your library:

fix: a commit of the type fix patches a bug in your codebase (this correlates with PATCH in Semantic Versioning).
feat: a commit of the type feat introduces a new feature to the codebase (this correlates with MINOR in Semantic Versioning).
BREAKING CHANGE: a commit that has a footer BREAKING CHANGE:, or appends a ! after the type/scope, introduces a breaking API change (correlating with MAJOR in Semantic Versioning). A BREAKING CHANGE can be part of commits of any type.
types other than fix: and feat: are allowed, for example @commitlint/config-conventional (based on the Angular convention) recommends build:, chore:, ci:, docs:, style:, refactor:, perf:, test:, and others.
footers other than BREAKING CHANGE: <description> may be provided and follow a convention similar to git trailer format.
Additional types are not mandated by the Conventional Commits specification, and have no implicit effect in Semantic Versioning (unless they include a BREAKING CHANGE). A scope may be provided to a commit’s type, to provide additional contextual information and is contained within parenthesis, e.g., feat(parser): add ability to parse arrays.

---

## Feature freeze

**End of M6 (week 3) is a hard feature freeze.** M7 is hardening and docs. After the freeze, the only acceptable code changes are bug fixes for failing verification items and instrumentation needed to measure feel (latency, CPU, memory, battery). If you find yourself adding a feature in week 4, stop.

---

## Agent skills

### Issue tracker

Issues and specs are local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` at the repo root. Decisions live in `docs/DECISIONS.md`. See `docs/agents/domain.md`.

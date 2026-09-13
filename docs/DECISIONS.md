# DECISIONS.md

Running log of decisions: what was chosen, what was rejected, and why. Do not
relitigate a settled entry without asking the human.

---

## D1 — Device roles (SPEC.md §1.4, CLAUDE.md)

- **Device A (budget)** — primary development device. Default target for
  `./gradlew :app:installDebug`. Performance problems must surface here first.
- **Device B (flagship)** — demo and headline numbers only.

Roles are fixed for the life of the project. Do not swap them.

> _Concrete device models to be filled in by the human before M3._

---

## D2 — No Hilt (SPEC.md §2.4)

Two modules and one pipeline object do not justify a DI framework or its build
cost. Use constructor injection and a single hand-rolled `AppContainer`.

---

## D3 — No NNAPI delegate (SPEC.md §2.4)

NNAPI was deprecated in Android 15. Use LiteRT's CPU path. Add the GPU delegate
only if M5 benchmarks show CPU missing the latency target (SPEC.md §1.4).

---

## D4 — No Compose inside the overlay window (SPEC.md §2.4)

Overlay `ComposeView`s require manually attaching `ViewTreeLifecycleOwner` and
`SavedStateRegistryOwner` — a reliable source of lost days. The overlay
(`MaskView`, M4) is a plain `View` subclass drawing on a `Canvas`. Compose is for
the normal in-app UI only.

---

## D5 — One NSFW model, not two (SPEC.md §6.8)

The reference project shipped two models (~26 MB). We ship one. Halves the
conversion risk, which is the M1 critical path.

---

## D6 — Toolchain versions deviate from SPEC.md §2.3

SPEC.md §2.3 lists a known-good version set (AGP 8.7.3, Kotlin 2.0.21,
compileSdk 36, composeBom 2024.12.01) and explicitly permits deviation:
_"These versions are a known-good starting set, not gospel... record the
resulting versions here."_

The repo was scaffolded by a current Android Studio with a newer, mutually
consistent toolchain that already syncs and builds on the dev machine.
Downgrading to the §2.3 set would be net new risk for no benefit. Kept as-is:

| Item        | SPEC.md §2.3   | Actual (this repo) |
| ----------- | -------------- | ------------------ |
| AGP         | 8.7.3          | 9.3.2              |
| Gradle      | (unspecified)  | 9.5.0              |
| Kotlin      | 2.0.21         | 2.2.10             |
| compileSdk  | 36             | 37                 |
| targetSdk   | 36             | 37                 |
| composeBom  | 2024.12.01     | 2026.02.01         |

Unchanged from SPEC: **`minSdk = 26`** (hard floor — `TYPE_APPLICATION_OVERLAY`
requires API 26).

LiteRT / Room / DataStore versions will be pinned when M1/M2/M4 introduce them.

---

## D7 — Package / application ID is `dev.sophiel` (SPEC.md §2.1)

The Studio scaffold used `com.example.sophiel`. Renamed to `dev.sophiel` in
M0 (cheapest point to do it) to match SPEC.md §2.1, §3.2, and every `adb` command
in SPEC.md §9 / CLAUDE.md. The git repo directory remains `sophiel`; the
Gradle `rootProject.name` remains `sophiel`. App display name: **Sophiel**.

---

## D8 — `:safecore` API contract stubbed in M0 (SPEC.md §3.4)

`Detector` / `Verdict` / `Severity` / `DetectorFactory` are committed in M0 as
the frozen public surface. `DetectorFactory.create` is `TODO()` until M2.

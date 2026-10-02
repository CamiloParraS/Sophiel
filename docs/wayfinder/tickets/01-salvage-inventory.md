# Salvage inventory  `wayfinder:research`
Assignee: 
Blocked by: none
Status: CLOSED

## Resolution

Findings: `docs/wayfinder/research/01-salvage-inventory.md` on branch `research/salvage-inventory` (commit 3632e51).
- KEEP: model, Preprocessor, parity/leak tests, the verified capture stack, manifest (no INTERNET).
- ADAPT: Detector/Verdict, DetectionPipeline, SkinGate, hash, cache, PolicyEngine (go per-tile); CaptureSession (seam for per-tile results + event log); MainActivity UI, AppContainer, Test Feed, debug pill.
- CUT: SPEC M5 bench/eval (unbuilt anyway), child tap-to-reveal, D9/D15 history.
- Contradicts new destination: SPEC §1.3, §6.6, §8.4, D12 note (whole-frame only); frozen §3.4 single-score Verdict; M4 tap-to-reveal and PIN-less threshold slider; M5 criteria; six-week plan.
- Risks: feedback loop worse with tiles (ticket 02); D20/D21 not re-verified on-device; per-tile cost and skin-gate minRatio untuned.

## Question

For every module and file in the repo (`:safecore`, capture, overlay, feed, debug tooling, DECISIONS D1-D21), is it KEEP, ADAPT or CUT for a parental-control app with per-region masking? Give a one-line reason each. Flag anything in `docs/SPEC.md` and `docs/DECISIONS.md` that contradicts the new destination.

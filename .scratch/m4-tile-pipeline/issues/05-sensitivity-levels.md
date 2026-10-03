# 05: Sensitivity levels

**What to build:** The Parent-facing sensitivity (Strict, Normal, Relaxed) maps to a score threshold in code and feeds the detector, replacing the single hard-coded default. The raw threshold stays available only for debug. Values are placeholders (Normal starts at the old 0.70) until ticket 07 tunes them on devices.

**Blocked by:** 02

**Status:** ready-for-agent

- [x] A sensitivity type with three levels maps to thresholds, ordered so Strict flags more than Normal, and Normal more than Relaxed (unit test).
- [x] The detector is created from a sensitivity level; the raw-threshold path remains for the debug menu.
- [x] The mapping's values live in one place, easy to retune.
- [x] The reserved SUGGESTIVE cutoff relationship (half of the explicit threshold, D11) is preserved.
- [x] JVM tests pass; `:app:assembleDebug` succeeds.

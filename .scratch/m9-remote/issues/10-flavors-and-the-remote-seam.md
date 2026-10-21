# 10: Flavors and the Remote seam

**What to build:** SPEC M9 deliverable 1 (D83). Split `:app` into flavor dimension `net`: `offline` / `remote`, same `applicationId`. Move the `tools:node="remove"` lines for `INTERNET` and `ACCESS_NETWORK_STATE` from `src/main` to `src/offline/AndroidManifest.xml`; `src/remote/AndroidManifest.xml` adds `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and the code scanner's `barcode_ui` meta-data. Add `dev.sophiel.remote.Remote` in both flavors (empty in `offline`) and route `main` through it at its four call sites (first-launch routing, Ajustes row, Status line, command entry), all no-ops for now. Add the `RELAY_URL` build-config field (SPEC §2) and the `remoteDebug` network security config (cleartext to `localhost` only). No Remote behaviour yet.

**Blocked by:** none

**Type:** AFK

**Status:** open

- [ ] `assembleOfflineDebug` and `assembleRemoteDebug` build; `installRemoteDebug` launches on A and behaves exactly as today
- [ ] `remoteImplementation` entries for OkHttp 5, ZXing core 3.5.4, code scanner 16.1.0 in `libs.versions.toml`
- [ ] With those deps in: `offline`'s `aapt … | grep -i internet` prints nothing; the flavor permission diff (SPEC §8) is exactly the three permissions
- [ ] `testOfflineDebugUnitTest` and `testRemoteDebugUnitTest` pass (every M4-M8 test)
- [ ] CLAUDE.md commands switched to the flavored tasks
- [ ] Ends buildable

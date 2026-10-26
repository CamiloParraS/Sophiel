# 02: Keeping the Child phone reachable in the background

Type: research
Status: resolved
Blocked by: none
Research: branch `research/m9-reachability` (fc2326b), `.scratch/m9-remote/research/02-reachability.md`

## Question

The Child phone must answer a Remote request with no one touching it, often with the screen off and Protection off (a remote Start). What keeps it reachable? Primary sources (developer.android.com, AOSP, Samsung docs); Device A is API 33 One UI 5, Device B Android 14+.

- Can a bound **AccessibilityService** hold a WebSocket open indefinitely? Does Doze / App Standby cut its network (the service is not a foreground service in accessibility capture mode, D54)? What about Samsung's sleeping apps (D42 has the Parent add Sophiel to "Never auto sleeping apps")?
- If Doze cuts it: what are the options and their costs? High-priority **FCM** (Firebase dependency, Google Play services, payload size, can the payload stay end-to-end encrypted?), a foreground service just for the socket (FGS type, API 34 rules, a permanent notification), `setAndAllowWhileIdle` alarms, WorkManager (15 min floor).
- WebSocket client on Android: no stdlib client exists; compare OkHttp (already transitive?) vs Ktor client: size, coroutine/Flow fit (hard rule 5). Check the app's current dependency tree for OkHttp.
- Recommend one approach and state its worst-case latency on A.

## Answer

**Approach:** the WebSocket lives in the app process the bound AccessibilityService (`MaskWindowService`) keeps alive, Protection on or off. The wizard adds a battery-optimization exemption step (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; Play allows it for safety apps) besides Samsung's "Never auto sleeping apps". The **server** pings; the phone reconnects on network change, screen on, and a 5-min `setAndAllowWhileIdle` check. Client: OkHttp 5 behind a small `callbackFlow`. No FCM, no extra foreground service (D54 holds).
**Why (AOSP source, not docs):** accessibility services are bound with flags that hand the app network access through Doze, App Standby and Android 15's background restriction. With the screen off the app drops a priority level and Doze ignores its wakelocks unless it is battery-exempt. Phone-side timers stop in deep sleep, so pings must come from the server.
**Worst case on A:** socket alive ~1-2 s (unmeasured); silently dead up to one 5-min check + reconnect (server can show "sin conexión" within ~2 min); service off or before first unlock after reboot: unreachable.
**Rejected:** high-priority FCM (Firebase, 4 KB, demoted within 7 days, Google sees metadata), FGS for the socket (breaks D54; `dataSync` capped 6 h/day on 15+), WorkManager (doesn't run in Doze).
**Facts for later tickets:** neither OkHttp nor Ktor is in the app today (OkHttp 5.5.0 ~1.3 MB); the main manifest currently *removes* `INTERNET`, so `remote` must add it back plus `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Five device checks listed in the research doc.

## Comments

- 2026-10-21: amended by [Flavors and the claim that replaces C1](07-flavors-and-the-new-claim.md): the socket is also held while the process lives for another reason (screen-share FGS, app open), answering Log, Stop and app rules with an "accesibilidad apagada" flag.

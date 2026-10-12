# 04: Accessibility facts for the settings guard, Device Admin and app attribution

Type: research
Status: resolved
Blocked by: none
Research: branch `research/guard-and-attribution`, `.scratch/m8-solution-fit/research/04-accessibility-guard-and-attribution.md`

## Question

Gather the platform facts tickets 05 and 06 decide on. Primary sources: developer.android.com, AOSP source, plus how open-source parental/blocker apps do it. Devices: A (API 33, Samsung One UI 5), B (Android 14+, Samsung).

**Settings guard (block turning Sophiel off):**
- Which accessibility events + window info (`TYPE_WINDOW_STATE_CHANGED`, `getWindows()`, `packageName`, `className`, node text) reliably identify: Sophiel's App info page (Force stop / Uninstall), the Accessibility > Sophiel toggle page, Device admin apps > Sophiel, the package-installer uninstall dialog, the launcher's "Uninstall" drag/long-press, Android 13+ Quick Settings "Active apps" (FGS task manager) Stop button, the screen-share chip "Stop sharing". Samsung One UI class names vs AOSP.
- Reliability of `performGlobalAction(GLOBAL_ACTION_HOME/BACK)` to escape them; can it race the user's tap?
- Event types / flags needed in the service config, and the cost (battery, event volume) of listening to window events globally.

**Device Admin:**
- What `DeviceAdminReceiver` still gives on API 33/34+ for a non-owner admin (uninstall needs deactivation first; `onDisableRequested` message); deprecated policies; any Samsung differences; how it combines with the guard.

**Boot:**
- Does an enabled accessibility service rebind on boot by itself, before unlock (direct boot) or after? `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` receiver limits for starting a foreground service on API 33/34+.

**App attribution (which app was on screen):**
- Best way to know the foreground app at the moment of a mask: last `TYPE_WINDOW_STATE_CHANGED` package vs `getWindows()` (split screen, PiP, overlays, keyboards, system UI) vs `UsageStatsManager`. Getting the app label (`QUERY_ALL_PACKAGES` / `<queries>` on API 30+).

Output: a findings file linked from this ticket, ending in a short table: guard target → detectable on A? on B? → how.

## Answer

Findings: branch `research/guard-and-attribution` (6c00302), `.scratch/m8-solution-fit/research/04-accessibility-guard-and-attribution.md`. AOSP 13-16, developer.android.com, prior art Mindful (GitHub) and TimeLimit (Codeberg). Nothing checked on A or B; every One UI name is marked unverified, with the adb command to confirm it.

- **Device Admin does most of the work (AOSP).** While Sophiel is an active admin: App info greys out Force stop and Clear storage; its Uninstall opens the deactivate page; every other uninstall path fails (launcher, uninstall dialog, Play, `adb uninstall`); Quick Settings "Active apps" hides Sophiel's Stop. The Child can always deactivate, but Sophiel gets `onDisableRequested` first, a tamper signal that needs no screen detection.
- **The guard still has to cover:** the deactivate page, Accessibility > Sophiel and its "Stop Sophiel?" dialog, the "Appear on top" permission page, and on Android 15+ the screen-share chip's stop dialog.
- **Detection:** match package + on-screen text "Sophiel" (or a known view id), not the class name; App info and the accessibility page both open in the generic `com.android.settings.SubSettings`. Deactivate page: class `DeviceAdminAdd` + view id `com.android.settings:id/admin_name`.
- **Race:** `performGlobalAction(HOME)` returns before Home shows, and the page is already drawn when the event arrives, so a fast tap can win. Only one-tap actions are exposed this way; the rest have a confirm dialog that fires its own event. Alternatives: launch our own blocking screen, or a touch-blocking window. Delay not measured.
- **Cost:** listen only to `TYPE_WINDOW_STATE_CHANGED`, filtered to Settings, installer and SystemUI packages; no content-change events.
- **Boot:** no boot receiver needed for the accessibility path: the system rebinds the service, and so starts the app, at the first unlock. MediaProjection cannot restart by itself: targetSdk is 37 (`app/build.gradle.kts`; SPEC §2 still says 36), and from targetSdk 35 a boot receiver may not start a `mediaProjection` FGS; consent is fresh anyway. Feeds ticket 03.
- **Attribution:** at mask time, take the topmost app window under the masked tile and read its package; `MaskWindowService.appWindowShot` already finds that window. Handles split screen and PiP, skips keyboard, system UI and our overlays, with no new permission and no events. Labels: `QUERY_ALL_PACKAGES` (simplest) or a `<queries>` launcher intent. `UsageStatsManager` and "last window event" are both worse.
- **Not guardable:** the accessibility shortcut, safe mode, adb, factory reset; only `onUnbind` afterwards, or a stated limitation.

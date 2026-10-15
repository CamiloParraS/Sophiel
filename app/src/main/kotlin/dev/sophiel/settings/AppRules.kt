package dev.sophiel.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import java.text.Collator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A Parent's per-app rule (D58). */
enum class AppRule { NORMAL, BLOCKED }

/** The per-app rules (D58): package -> rule, Normal ones not stored. Loaded once, held in memory, written through. */
class AppRules(context: Context) {
    private val prefs = context.getSharedPreferences("app_rules", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        prefs.all.mapNotNull { (pkg, v) -> AppRule.entries.find { it.name == v }?.let { pkg to it } }.toMap(),
    )
    val state: StateFlow<Map<String, AppRule>> = _state.asStateFlow()

    fun rule(pkg: String) = _state.value[pkg] ?: AppRule.NORMAL

    fun set(pkg: String, rule: AppRule) {
        _state.update { if (rule == AppRule.NORMAL) it - pkg else it + (pkg to rule) }
        prefs.edit().apply { if (rule == AppRule.NORMAL) remove(pkg) else putString(pkg, rule.name) }.apply()
    }
}

/** A launchable app, from the MAIN/LAUNCHER `<queries>` (D57). */
data class AppEntry(val pkg: String, val label: String)

/** The Apps screen's list, pure: no [excluded], matching [query], changed ones (in [rules]) first, then by label (accents and case as the phone's language sorts them). */
fun appList(apps: List<AppEntry>, rules: Map<String, AppRule>, excluded: Set<String>, query: String): List<AppEntry> {
    val q = query.trim()
    return apps.filter { it.pkg !in excluded && it.label.contains(q, ignoreCase = true) }
        .sortedWith(compareBy<AppEntry> { (rules[it.pkg] ?: AppRule.NORMAL) == AppRule.NORMAL }.thenBy(Collator.getInstance()) { it.label })
}

/**
 * D58: never listed, blocked or (later) escalated: Sophiel, Settings, the default launcher, the default dialer. Also the
 * keyboard: its windows send window-state events in every app, so a blocked one would send the phone Home anywhere.
 */
fun excludedApps(context: Context): Set<String> {
    val pm = context.packageManager
    fun resolve(intent: Intent) = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    return setOfNotNull(
        context.packageName,
        resolve(Intent(Settings.ACTION_SETTINGS)),
        resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/'),
    )
}

/** The launchable apps, one per package. Slow (labels): off the main thread. */
fun launchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .distinctBy { it.activityInfo.packageName }
        .map { AppEntry(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(pm).toString()) }
}

/** An app's label in the phone's language; an uninstalled or hidden app shows its package (D57). */
fun appLabel(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

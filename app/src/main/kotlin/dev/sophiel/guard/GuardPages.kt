package dev.sophiel.guard

import dev.sophiel.log.TamperTarget

/** A node a search for Sophiel's text or a page's view id returned: text, view id, checked; nothing else is read (D56). */
data class GuardNode(val text: String?, val viewId: String?, val checked: Boolean = false)

/**
 * The tamper guard's page matching (D56), pure: package + Sophiel's text on screen, never class names.
 * Fixtures are ticket 20's One UI captures (GuardPagesTest).
 */
object GuardPages {
    /** Windows whose node text is read at all. */
    private val SETTINGS = setOf("com.android.settings", "com.samsung.accessibility")
    private val INSTALLER = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")
    private const val SYSTEMUI = "com.android.systemui"

    fun reads(pkg: String) = pkg in SETTINGS || pkg in INSTALLER || pkg == SYSTEMUI

    /** View ids the service looks up besides the text search; they tell App info and Appear on top apart without words. */
    val IDS = listOf(FORCE_STOP, SWITCH, PERMISSION_TEXT)

    /**
     * [nodes]: those holding the app label or the service label, plus those with [IDS]. The service page shows
     * the service label as its title and on its main switch; the installed-services list shows it only as a row
     * title (`android:id/title`), which opens the page without turning anything off.
     */
    fun match(pkg: String, nodes: List<GuardNode>, serviceLabel: String): String? = when (pkg) {
        in SETTINGS -> settings(nodes, serviceLabel)
        in INSTALLER -> if (nodes.any { it.text == APP && (it.viewId?.endsWith(":id/app_label") == true || it.viewId == ALERT_TITLE) }) TamperTarget.UNINSTALL else null
        SYSTEMUI -> if (nodes.any { it.text == APP && it.viewId == ACTIVE_APP_LABEL }) TamperTarget.ACTIVE_APPS else null
        else -> null
    }

    private fun settings(nodes: List<GuardNode>, serviceLabel: String): String? = when {
        nodes.any { it.text == serviceLabel && it.viewId != ROW_TITLE } -> TamperTarget.ACCESSIBILITY
        nodes.none { it.text == APP && it.viewId == HEADER } -> null
        nodes.any { it.viewId == FORCE_STOP } -> TamperTarget.APP_INFO
        // Appear on top: guarded only while granted, so granting it is never blocked.
        nodes.any { it.viewId == PERMISSION_TEXT } && nodes.any { it.viewId == SWITCH && it.checked } -> TamperTarget.OVERLAY
        else -> null
    }

    private const val APP = "Sophiel" // the app label (app_name, not translated)
    private const val ALERT_TITLE = "android:id/alertTitle" // A (Android 11): the old dialog titles itself with the app name
    private const val ROW_TITLE = "android:id/title"
    private const val HEADER = "com.android.settings:id/entity_header_title"
    private const val FORCE_STOP = "com.android.settings:id/forcestop_button"
    private const val SWITCH = "android:id/switch_widget"
    private const val PERMISSION_TEXT = "com.android.settings:id/title"
    private const val ACTIVE_APP_LABEL = "com.android.systemui:id/fgs_manager_app_item_label"
}

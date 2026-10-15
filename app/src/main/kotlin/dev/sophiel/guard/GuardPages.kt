package dev.sophiel.guard

import dev.sophiel.log.TamperTarget

/** A node a search for Sophiel's text returned: its text and view id, nothing else is read (D56). */
data class GuardNode(val text: String?, val viewId: String?)

/**
 * The tamper guard's page matching (D56), pure: package + Sophiel's text on screen, never class names.
 * Fixtures are ticket 20's One UI captures (GuardPagesTest).
 */
object GuardPages {
    /** Windows whose node text is read at all. Ticket 22 adds the package installer and SystemUI. */
    private val SETTINGS = setOf("com.android.settings", "com.samsung.accessibility")

    fun reads(pkg: String) = pkg in SETTINGS

    /**
     * [nodes]: those whose text contains [serviceLabel]. The service page shows the label as its title and on
     * its main switch; the installed-services list shows it only as a row title (`android:id/title`), which
     * opens the page without turning anything off.
     */
    fun match(pkg: String, nodes: List<GuardNode>, serviceLabel: String): String? = when {
        !reads(pkg) -> null
        nodes.any { it.text == serviceLabel && it.viewId != ROW_TITLE } -> TamperTarget.ACCESSIBILITY
        else -> null
    }

    private const val ROW_TITLE = "android:id/title"
}

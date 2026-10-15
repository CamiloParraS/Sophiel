package dev.sophiel.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRulesTest {
    private val apps = listOf(
        AppEntry("com.whatsapp", "WhatsApp"), AppEntry("com.android.chrome", "Chrome"), AppEntry("dev.sophiel", "Sophiel"),
        AppEntry("com.instagram.android", "Instagram"), AppEntry("com.android.camera", "cámara"),
    )

    @Test fun `the list drops excluded apps, pins changed ones first, then sorts by label`() {
        val list = appList(apps, mapOf("com.whatsapp" to AppRule.BLOCKED), excluded = setOf("dev.sophiel"), query = "")
        assertEquals(listOf("WhatsApp", "cámara", "Chrome", "Instagram"), list.map { it.label })
    }

    @Test fun `search matches the label anywhere, any case`() {
        assertEquals(listOf("Instagram"), appList(apps, emptyMap(), emptySet(), " GRAM ").map { it.label })
        assertEquals(listOf("Chrome"), appList(apps, emptyMap(), emptySet(), "CHR").map { it.label })
    }
}

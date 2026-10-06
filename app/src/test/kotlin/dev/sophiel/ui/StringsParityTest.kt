package dev.sophiel.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** D43: Spanish is the default `values/`, English lives in `values-en/`; neither may miss a key or a format argument. */
class StringsParityTest {
    private fun load(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val out = sortedMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            out["${e.tagName}:${e.getAttribute("name")}"] = e.textContent
        }
        return out
    }

    private fun args(s: String) = Regex("""%(\d+\$)?[sdf]""").findAll(s).map { it.value }.sorted().toList()

    @Test
    fun englishHasEveryKeyWithTheSameArguments() {
        val es = load("values")
        val en = load("values-en")
        assertEquals(es.keys, en.keys)
        for (k in es.keys) assertEquals("format args of $k", args(es.getValue(k)), args(en.getValue(k)))
    }
}

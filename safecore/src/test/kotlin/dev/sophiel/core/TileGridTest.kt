package dev.sophiel.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TileGridTest {
    private fun assertPartition(preset: Preset, w: Int, h: Int) {
        val covered = Array(h) { IntArray(w) }
        repeat(preset.cols * preset.rows) { i ->
            val r = preset.tileRect(i, w, h)
            for (y in r.top until r.bottom) for (x in r.left until r.right) covered[y][x]++
        }
        assertEquals("$preset ${w}x$h", w * h, covered.sumOf { row -> row.count { it == 1 } })
    }

    @Test fun lightIsWholeFrame() = assertEquals(TileRect(0, 0, 7, 5), Preset.LIGHT.tileRect(0, 7, 5))

    @Test fun balancedSwapsInLandscape() {
        assertEquals(2 to 3, Preset.BALANCED.grid(360, 800))
        assertEquals(3 to 2, Preset.BALANCED.grid(800, 360))
        assertEquals(TileRect(100, 0, 200, 100), Preset.BALANCED.tileRect(1, 300, 200))
    }

    @Test fun tilesPartitionAnySize() {
        for (p in Preset.entries) for ((w, h) in listOf(360 to 800, 361 to 799, 7 to 5, 3 to 2)) assertPartition(p, w, h)
    }
}

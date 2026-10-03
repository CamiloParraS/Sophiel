package dev.sophiel.capture

import dev.sophiel.core.TileRect
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayControllerTest {
    @Test fun `mask edges round outward to whole pixels`() {
        // left/top floor, right/bottom ceil: covering slightly more is the safe direction.
        assertEquals(TileRect(101, 50, 500, 251), maskBounds(0.1015f, 0.0501f, 0.4999f, 0.2501f, 1000, 1000, 0, 0))
    }

    @Test fun `window origin is subtracted`() {
        assertEquals(TileRect(0, 204, 540, 1404), maskBounds(0f, 0.125f, 0.5f, 0.625f, 1080, 2400, 0, 96))
    }
}

package dev.sophiel.feed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sophiel.core.Detector
import dev.sophiel.core.DetectorFactory
import dev.sophiel.core.Verdict

private const val ASSET_DIR = "testfeed"

private data class TestFeedTile(val name: String, val bitmap: Bitmap)

/**
 * Permission-free exerciser for [Detector]: a scrollable list of bundled test
 * images, each showing its live verdict, score, gate state, and latency
 */
@Composable
fun TestFeedScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val detector = remember { DetectorFactory.create(context) }
    DisposableEffect(detector) { onDispose { detector.close() } }

    val tiles = remember { loadTestFeedTiles(context) }
    val verdicts = remember { mutableStateMapOf<String, Verdict>() }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(tiles, key = { it.name }) { tile ->
            LaunchedEffect(tile.name) {
                verdicts[tile.name] = detector.analyze(tile.bitmap)
            }
            TestFeedTileRow(tile.name, tile.bitmap, verdicts[tile.name])
        }
    }
}

@Composable
private fun TestFeedTileRow(name: String, bitmap: Bitmap, verdict: Verdict?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = name, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(verdict.describe(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Verdict?.describe(): String = when (this) {
    null -> "analyzing…"
    else -> "$severity · score=${"%.2f".format(score)} · gated=$gated · cacheHit=$cacheHit · ${latencyMs}ms"
}

private fun loadTestFeedTiles(context: Context): List<TestFeedTile> =
    context.assets.list(ASSET_DIR).orEmpty()
        .filter { it.endsWith(".png") }
        .sorted()
        .map { name ->
            val bitmap = context.assets.open("$ASSET_DIR/$name").use { BitmapFactory.decodeStream(it) }
            TestFeedTile(name, bitmap)
        }

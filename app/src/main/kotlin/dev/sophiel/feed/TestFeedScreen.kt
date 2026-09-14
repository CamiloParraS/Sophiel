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

/** Repeats of the bundled fixture set, so the list is tall enough that scrolling
 *  back to the top actually disposes and recomposes the first tile (needed to
 *  demonstrate cacheHit=true on revisit — 10 short tiles alone fit in one screen
 *  and never leave Compose's active window). */
private const val REPEAT_COUNT = 4

/** Real capture frames are ~360 px on the short side (SPEC.md §4.6). Decoding bundled
 *  images at full size (up to 4096 px, ~386 MB of native bitmap memory for the set)
 *  swamped native heap and hid any real Detector leak in the M2.V5 Profiler pass. */
private const val DECODE_MIN_SIDE = 360

private data class TestFeedTile(val id: String, val name: String, val bitmap: Bitmap)

/**
 * Permission-free exerciser for [Detector]: a scrollable list of bundled test
 * images, each showing its live verdict, score, gate state, and latency
 */
@Composable
fun testFeedScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val detector = remember { DetectorFactory.create(context) }
    DisposableEffect(detector) { onDispose { detector.close() } }

    val tiles = remember { loadTestFeedTiles(context) }
    val verdicts = remember { mutableStateMapOf<String, Verdict>() }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(tiles, key = { it.id }) { tile ->
            LaunchedEffect(tile.id) {
                verdicts[tile.id] = detector.analyze(tile.bitmap)
            }
            testFeedTileRow(tile.name, tile.bitmap, verdicts[tile.id])
        }
    }
}

@Composable
private fun testFeedTileRow(name: String, bitmap: Bitmap, verdict: Verdict?) {
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

private fun loadTestFeedTiles(context: Context): List<TestFeedTile> {
    val base = context.assets.list(ASSET_DIR).orEmpty()
        .filter { it.endsWith(".png") }
        .sorted()
        .map { name -> name to decodeCaptureSized(context, "$ASSET_DIR/$name") }
    return (0 until REPEAT_COUNT).flatMap { rep ->
        base.map { (name, bitmap) -> TestFeedTile(id = "$name#$rep", name = name, bitmap = bitmap) }
    }
}

/** Decodes with the largest power-of-two subsample that keeps the short side >= [DECODE_MIN_SIDE]. */
private fun decodeCaptureSized(context: Context, path: String): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_MIN_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return checkNotNull(context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }) {
        "could not decode $path"
    }
}

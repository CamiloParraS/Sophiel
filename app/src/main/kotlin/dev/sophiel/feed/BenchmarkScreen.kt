package dev.sophiel.feed

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sophiel.core.model.NsfwClassifier
import dev.sophiel.core.model.Preprocessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private class BenchRow(val name: String, val bitmap: Bitmap, val ms: Long, val score: Float, val detections: List<Detection>)

private class Runner(val run: (Bitmap) -> Pair<Float, List<Detection>>, val close: () -> Unit)

private fun open(context: Context, model: SpikeModel): Runner = when (model.asset) {
    null -> NsfwClassifier.load(context).let { c -> Runner({ c.classify(Preprocessor.toInputBuffer(it)) to emptyList() }, c::close) }
    else -> NudeNet.load(context, model.asset, model.inputSize).let { n -> Runner({ b -> n.detect(b).let { it.unsafeScore() to it } }, n::close) }
}

/**
 * Spike: raw model cost and output per Test Feed image, whole frame, no skin gate or cache.
 * Each run also logs `bench-summary` to logcat (tag Sophiel) so numbers can be pulled over adb.
 */
@Composable
fun benchmarkScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val images = remember {
        context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".png") }.sorted()
            .map { it to decodeCaptureSized(context, "$ASSET_DIR/$it") }
    }
    var model by remember { mutableStateOf<SpikeModel?>(null) }
    var header by remember { mutableStateOf("Pick a model") }
    val rows = remember { mutableStateListOf<BenchRow>() }

    LaunchedEffect(model) {
        val m = model ?: return@LaunchedEffect
        rows.clear()
        header = "${m.label}: loading…"
        val t0 = SystemClock.elapsedRealtime()
        val runner = try {
            withContext(Dispatchers.Default) { open(context, m).also { it.run(images.first().second) } } // load + warm-up
        } catch (e: Exception) {
            header = "${m.label}: ${e.message} (copy temp_download/NudeNet_*.onnx into app/src/main/assets/)"
            Log.e(TAG, "bench-summary model=${m.name} error=$e")
            return@LaunchedEffect
        }
        val loadMs = SystemClock.elapsedRealtime() - t0
        try {
            for ((name, bitmap) in images) {
                val row = withContext(Dispatchers.Default) {
                    val start = SystemClock.elapsedRealtime()
                    val (score, detections) = runner.run(bitmap)
                    BenchRow(name, bitmap, SystemClock.elapsedRealtime() - start, score, detections)
                }
                rows += row
                Log.d(TAG, "bench model=${m.name} ms=${row.ms} score=${"%.2f".format(row.score)} image=$name")
                header = "${m.label}: ${rows.size}/${images.size}…"
            }
            val ms = rows.map { it.ms }.sorted()
            header = "${m.label} · load+warm ${loadMs}ms · n=${ms.size} · mean ${ms.average().toInt()} · p50 ${ms[ms.size / 2]} · p90 ${ms[ms.size * 9 / 10]} · max ${ms.last()} ms"
            Log.i(TAG, "bench-summary model=${m.name} loadMs=$loadMs n=${ms.size} mean=${ms.average().toInt()} p50=${ms[ms.size / 2]} p90=${ms[ms.size * 9 / 10]} max=${ms.last()}")
        } finally {
            runner.close()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpikeModel.entries.forEach { m -> FilterChip(selected = m == model, onClick = { model = m }, label = { Text(m.label) }) }
        }
        Text(header, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize()) { items(rows, key = { it.name }) { benchRow(it) } }
    }
}

@Composable
private fun benchRow(row: BenchRow) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            bitmap = row.bitmap.asImageBitmap(),
            contentDescription = row.name,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.width(96.dp).aspectRatio(row.bitmap.width.toFloat() / row.bitmap.height).drawWithContent {
                drawContent()
                row.detections.forEach { d ->
                    drawRect(
                        color = if (d.unsafe) Color.Red else Color.Yellow,
                        topLeft = Offset(d.box.left * size.width, d.box.top * size.height),
                        size = Size(d.box.width() * size.width, d.box.height() * size.height),
                        style = Stroke(2f),
                    )
                }
            },
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(row.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${row.ms}ms · score=${"%.2f".format(row.score)}" +
                    row.detections.joinToString("") { "\n${it.label.lowercase()} ${"%.2f".format(it.score)}" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private const val TAG = "Sophiel"

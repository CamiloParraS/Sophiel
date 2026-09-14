package dev.sophiel.core

import android.graphics.Bitmap
import android.os.SystemClock
import dev.sophiel.core.cache.VerdictCache
import dev.sophiel.core.gate.PerceptualHash
import dev.sophiel.core.gate.SkinGate
import dev.sophiel.core.model.NsfwClassifier
import dev.sophiel.core.model.Preprocessor
import dev.sophiel.core.policy.PolicyEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Orchestrates one frame through hash → cache lookup → skin gate → classifier
 * → policy. All work runs on [dispatcher], so concurrent [analyze] callers
 * queue rather than run in parallel (SPEC.md §3.4).
 *
 * The cache and gate only short-circuit the *score*. [PolicyEngine] runs on
 * every frame — cache hits and gated frames included — so its hysteresis
 * sees the whole frame stream. (Caching the post-policy severity would freeze
 * a static screen at whatever severity its first frame got.)
 */
class DetectionPipeline(
    private val classifier: NsfwClassifier,
    private val policy: PolicyEngine,
    private val dispatcher: CoroutineDispatcher,
    private val gate: SkinGate = SkinGate(),
    private val cache: VerdictCache = VerdictCache(),
) : Detector {

    override suspend fun analyze(frame: Bitmap): Verdict = withContext(dispatcher) {
        val start = SystemClock.elapsedRealtime()
        val hash = PerceptualHash.hash(frame)

        val scored = cache.get(hash)?.copy(cacheHit = true)
            ?: score(frame).also { cache.put(hash, it) }

        scored.copy(severity = policy.classify(scored.score), latencyMs = elapsedSince(start))
    }

    /** Gate + classifier only. Severity/latency are placeholders, filled in by [analyze]. */
    private fun score(frame: Bitmap): Verdict {
        val gated = !gate.shouldClassify(frame)
        val score = if (gated) 0f else classifier.classify(Preprocessor.toInputBuffer(frame))
        return Verdict(Severity.SAFE, score, gated = gated, cacheHit = false, latencyMs = 0)
    }

    /** Releases the interpreter and, if [dispatcher] owns an executor thread, shuts it down. */
    override fun close() {
        classifier.close()
        (dispatcher as? java.io.Closeable)?.close()
    }

    private fun elapsedSince(startMs: Long) = SystemClock.elapsedRealtime() - startMs
}

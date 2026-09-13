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

        val cached = cache.get(hash)
        if (cached != null) {
            return@withContext cached.copy(cacheHit = true, latencyMs = elapsedSince(start))
        }

        val verdict = if (!gate.shouldClassify(frame)) {
            Verdict(Severity.SAFE, score = 0f, gated = true, cacheHit = false, latencyMs = elapsedSince(start))
        } else {
            val score = classifier.classify(Preprocessor.toInputBuffer(frame))
            val severity = policy.classify(score)
            Verdict(severity, score, gated = false, cacheHit = false, latencyMs = elapsedSince(start))
        }

        cache.put(hash, verdict)
        verdict
    }

    /** Releases the interpreter and, if [dispatcher] owns an executor thread, shuts it down. */
    override fun close() {
        classifier.close()
        (dispatcher as? java.io.Closeable)?.close()
    }

    private fun elapsedSince(startMs: Long) = SystemClock.elapsedRealtime() - startMs
}

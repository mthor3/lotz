package dev.marty.lotz.sim.market

import kotlin.math.exp
import kotlin.random.Random

/** Exact Poisson sampling using Knuth's method, chunked for numerical stability at large means. */
object PoissonSampler {
    private const val MAX_MEAN_PER_CHUNK = 20.0

    fun sample(mean: Double, random: Random): Int {
        require(mean.isFinite() && mean >= 0.0) { "Poisson mean must be finite and non-negative" }

        var remaining = mean
        var total = 0L
        while (remaining > MAX_MEAN_PER_CHUNK) {
            total += sampleKnuth(MAX_MEAN_PER_CHUNK, random)
            remaining -= MAX_MEAN_PER_CHUNK
        }
        total += sampleKnuth(remaining, random)
        require(total <= Int.MAX_VALUE) { "Poisson sample exceeds Int range" }
        return total.toInt()
    }

    private fun sampleKnuth(mean: Double, random: Random): Int {
        if (mean == 0.0) return 0
        val limit = exp(-mean)
        var product = 1.0
        var count = 0
        do {
            count++
            product *= random.nextDouble()
        } while (product > limit)
        return count - 1
    }
}

package dev.marty.lotz.sim

/**
 * Money as integer cents (build-plan fixed decision: no floating-point money).
 * Placeholder walking-skeleton type; the real domain arrives in Chunk 2.
 */
data class Money(val cents: Long) {
    operator fun plus(other: Money): Money = Money(cents + other.cents)

    override fun toString(): String {
        val sign = if (cents < 0) "-" else ""
        val abs = if (cents < 0) -cents else cents
        val fraction = (abs % 100).toString().padStart(2, '0')
        return "$sign$${abs / 100}.$fraction"
    }

    companion object {
        fun ofDollars(dollars: Long): Money = Money(dollars * 100)
    }
}

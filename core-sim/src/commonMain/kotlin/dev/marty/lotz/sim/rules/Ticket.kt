package dev.marty.lotz.sim.rules

/** A player's chosen (or quick-picked) numbers plus the option ids attached at purchase. */
data class Ticket(
    val mainNumbers: Set<Int>,
    val bonusNumber: Int? = null,
    val optionIds: Set<String> = emptySet(),
    /** Only meaningful for options like Mega Millions' built-in multiplier, rolled per ticket at purchase. */
    val perTicketMultiplier: Int? = null,
)

/** One drawing's winning numbers. */
data class DrawResult(
    val mainNumbers: Set<Int>,
    val bonusNumber: Int? = null,
)

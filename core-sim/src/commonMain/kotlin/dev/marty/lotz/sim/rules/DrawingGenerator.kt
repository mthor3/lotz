package dev.marty.lotz.sim.rules

import kotlin.random.Random

/** Seeded random number generation for drawings, quick-pick tickets, and option rolls. */
object DrawingGenerator {

    /** Draws [count] distinct numbers from 1..[pool] in ascending order. */
    fun distinctNumbers(pool: Int, count: Int, rng: Random): Set<Int> {
        require(count in 1..pool)
        val chosen = LinkedHashSet<Int>(count)
        while (chosen.size < count) {
            chosen.add(rng.nextInt(1, pool + 1))
        }
        return chosen
    }

    fun draw(game: GameDefinition, rng: Random): DrawResult {
        val main = distinctNumbers(game.mainPool, game.mainPick, rng)
        val bonus = if (game.bonusPool > 0) rng.nextInt(1, game.bonusPool + 1) else null
        return DrawResult(main, bonus)
    }

    fun quickPickTicket(game: GameDefinition, rng: Random, optionIds: Set<String> = emptySet()): Ticket {
        val main = distinctNumbers(game.mainPool, game.mainPick, rng)
        val bonus = if (game.bonusPool > 0) rng.nextInt(1, game.bonusPool + 1) else null
        val perTicketMultiplier = optionIds.firstNotNullOfOrNull { id ->
            (game.options.firstOrNull { it.id == id } as? GameOption.Multiplier)?.let { rollMultiplier(it, rng) }
        }
        return Ticket(main, bonus, optionIds, perTicketMultiplier)
    }

    /** Rolls one multiplier value from a weighted ball wheel (e.g. Power Play, Mega Millions built-in). */
    fun rollMultiplier(option: GameOption.Multiplier, rng: Random): Int {
        val roll = rng.nextInt(option.totalWeight)
        var acc = 0
        for ((value, weight) in option.weights) {
            acc += weight
            if (roll < acc) return value
        }
        return option.weights.keys.last()
    }
}

package dev.marty.lotz.sim.engine

import dev.marty.lotz.sim.market.JackpotState
import dev.marty.lotz.sim.market.MarketModel
import dev.marty.lotz.sim.rules.DrawResult
import dev.marty.lotz.sim.rules.DrawingGenerator
import dev.marty.lotz.sim.rules.GameOption
import dev.marty.lotz.sim.rules.PrizeEvaluator
import dev.marty.lotz.sim.rules.Ticket
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random

/**
 * Steps one [PlayerStrategy] drawing-by-drawing over simulated time, evaluating tickets against
 * both the player's own draw and the [MarketModel]'s rolling jackpot, until the strategy's
 * [StopCondition] is met. Deterministic under [seed]: every random draw flows through one seeded
 * [Random] instance in call order.
 */
object SimulationEngine {

    /** Timeline points kept per run; older points are decimated (not dropped) beyond this. */
    private const val MAX_TIMELINE_POINTS = 1000

    /** Notable events kept per run, as a most-recent window. */
    private const val MAX_EVENTS = 500

    /** Non-jackpot prize floor for recording a [SimEvent.BigWin]. */
    private const val BIG_WIN_THRESHOLD_CENTS = 100_000_00L

    val DEFAULT_START_DATE: LocalDate = LocalDate(2026, 1, 1)

    suspend fun run(
        strategy: PlayerStrategy,
        seed: Long,
        startDate: LocalDate = DEFAULT_START_DATE,
        marketModel: MarketModel = MarketModel(),
        onProgress: ((DrawingProgress) -> Unit)? = null,
    ): RunResult {
        if (!strategy.tracking.trackWinnings) {
            return AnalyticSimulator.run(strategy, seed, startDate, onProgress)
        }

        val game = strategy.game
        val rng = Random(seed)
        // Resolved before the loop so RandomOnce replays one pick every drawing. Consuming the rng
        // here (RandomOnce only) is part of this path's determinism contract.
        val numberChoice = resolveNumberChoice(strategy, rng)
        val timeline = BoundedTimeline(MAX_TIMELINE_POINTS)
        val events = ArrayDeque<SimEvent>()

        val durationEndDate = (strategy.stopCondition as? StopCondition.Duration)?.let { startDate.plus(it.period) }

        var currentDate = nextDrawDate(startDate, game.drawDays)
        var jackpotState = JackpotState(game.baseJackpotCents)
        var totalSpent = 0L
        var totalWon = 0L
        val tierWinCounts = mutableMapOf<String, Int>()
        var jackpotWon = false
        var jackpotAnnuityCents = 0L
        var jackpotCashCents = 0L
        var drawingsPlayed = 0
        var drawingIndex = 0

        fun recordEvent(event: SimEvent) {
            events.addLast(event)
            if (events.size > MAX_EVENTS) events.removeFirst()
        }

        while (true) {
            currentCoroutineContext().ensureActive()

            if (durationEndDate != null && currentDate >= durationEndDate) break

            val playsThisDrawing = when (val frequency = strategy.frequency) {
                PlayFrequency.EveryDrawing -> true
                is PlayFrequency.EveryNthDrawing -> drawingIndex % frequency.n == 0
            }

            var boughtThisDrawing = false
            var costCents = 0L
            if (playsThisDrawing) {
                costCents = strategy.costPerDrawingCents
                val budgetCap = strategy.stopCondition as? StopCondition.BudgetCap
                if (budgetCap != null) {
                    val available = budgetCap.totalCents + (if (strategy.reinvestWinnings) totalWon else 0L) - totalSpent
                    if (costCents > available) break
                }
                boughtThisDrawing = true
            }

            // Co-players buy every drawing regardless of whether the player does, so sales and the
            // primary draw happen unconditionally.
            val sales = marketModel.salesForDrawing(game, jackpotState.advertisedJackpotCents, rng)
            val draw = DrawingGenerator.draw(game, rng)

            var drawingWinnings = 0L
            var playerHitJackpot = false

            if (boughtThisDrawing) {
                totalSpent += costCents

                // Power Play-style options (paid add-on) roll one shared ball per drawing; Mega
                // Millions' built-in multiplier (bundled, zero-cost) rolls independently per ticket
                // inside buildTicket. Neither distinction is encoded in GameOption itself, so it is
                // inferred here from whether the option costs extra per play.
                val sharedMultiplierOption = strategy.optionIds.firstNotNullOfOrNull { id ->
                    (game.options.first { it.id == id } as? GameOption.Multiplier)?.takeIf { it.priceCentsPerPlay > 0 }
                }
                val sharedDrawnMultiplier = sharedMultiplierOption?.let { DrawingGenerator.rollMultiplier(it, rng) }

                val doublePlayOption = strategy.optionIds.firstNotNullOfOrNull { id ->
                    game.options.first { it.id == id } as? GameOption.SecondDraw
                }
                // One shared second drawing per primary drawing, same as the primary draw.
                val secondDraw = doublePlayOption?.let { DrawingGenerator.draw(game, rng) }

                repeat(strategy.entriesPerDrawing) {
                    val ticket = buildTicket(strategy, numberChoice, rng)
                    val result = PrizeEvaluator.evaluate(game, ticket, draw, sharedDrawnMultiplier)
                    if (result.isJackpotWin) {
                        playerHitJackpot = true
                    } else if (result.tier != null) {
                        drawingWinnings += result.amountCents
                        tierWinCounts[result.tier.key] = (tierWinCounts[result.tier.key] ?: 0) + 1
                        if (result.amountCents >= BIG_WIN_THRESHOLD_CENTS) {
                            recordEvent(SimEvent.BigWin(currentDate, result.tier.key, result.amountCents))
                        }
                    }

                    if (doublePlayOption != null && secondDraw != null) {
                        val amount = evaluateSecondDraw(doublePlayOption, ticket, secondDraw)
                        if (amount > 0) drawingWinnings += amount
                    }
                }
            }

            val otherWinners = marketModel.otherJackpotWinners(game, sales, rng)
            val outcome = marketModel.advanceJackpot(jackpotState, game, sales, otherWinners, playerHitJackpot)
            jackpotState = outcome.nextState

            if (outcome.didReset) {
                if (playerHitJackpot) {
                    jackpotWon = true
                    jackpotAnnuityCents = outcome.playerAnnuityShareCents
                    jackpotCashCents = outcome.playerCashShareCents
                    drawingWinnings += outcome.playerAnnuityShareCents
                    tierWinCounts[game.jackpotTier.key] = (tierWinCounts[game.jackpotTier.key] ?: 0) + 1
                    if (outcome.totalJackpotWinners > 1) {
                        recordEvent(
                            SimEvent.JackpotSplit(currentDate, outcome.totalJackpotWinners, outcome.playerAnnuityShareCents),
                        )
                    } else {
                        recordEvent(SimEvent.BigWin(currentDate, game.jackpotTier.key, outcome.playerAnnuityShareCents))
                    }
                } else {
                    recordEvent(SimEvent.JackpotReset(currentDate, outcome.previousAdvertisedJackpotCents))
                }
            }

            totalWon += drawingWinnings
            if (boughtThisDrawing) drawingsPlayed++
            drawingIndex++

            timeline.maybeAdd(
                TimelinePoint(
                    date = currentDate,
                    drawingIndex = drawingIndex,
                    cumulativeSpentCents = totalSpent,
                    cumulativeWonCents = totalWon,
                    advertisedJackpotCents = jackpotState.advertisedJackpotCents,
                ),
            )

            onProgress?.invoke(
                DrawingProgress(drawingIndex, currentDate, jackpotState.advertisedJackpotCents, totalSpent, totalWon),
            )

            if (playerHitJackpot) break

            currentDate = nextDrawDate(currentDate.plus(DatePeriod(days = 1)), game.drawDays)
        }

        return RunResult(
            strategy = strategy,
            seed = seed,
            startDate = startDate,
            endDate = currentDate,
            drawingsPlayed = drawingsPlayed,
            totalSpentCents = totalSpent,
            totalWonCents = totalWon,
            tierWinCounts = tierWinCounts,
            jackpotWon = jackpotWon,
            jackpotAnnuityCents = jackpotAnnuityCents,
            jackpotCashCents = jackpotCashCents,
            timeline = timeline.toList(),
            notableEvents = events.toList(),
            fixedNumbers = numberChoice as? NumberChoice.Fixed,
        )
    }

    internal fun nextDrawDate(from: LocalDate, drawDays: Set<DayOfWeek>): LocalDate {
        var date = from
        while (date.dayOfWeek !in drawDays) date = date.plus(DatePeriod(days = 1))
        return date
    }

    /** Resolves [NumberChoice.RandomOnce] to a concrete random [NumberChoice.Fixed] pick. */
    internal fun resolveNumberChoice(strategy: PlayerStrategy, rng: Random): NumberChoice =
        when (strategy.numberChoice) {
            NumberChoice.RandomOnce -> {
                val game = strategy.game
                val main = DrawingGenerator.distinctNumbers(game.mainPool, game.mainPick, rng)
                val bonus = if (game.bonusPool > 0) rng.nextInt(1, game.bonusPool + 1) else null
                NumberChoice.Fixed(main, bonus)
            }
            else -> strategy.numberChoice
        }

    private fun buildTicket(strategy: PlayerStrategy, numberChoice: NumberChoice, rng: Random): Ticket {
        val game = strategy.game
        val (mainNumbers, bonusNumber) = when (numberChoice) {
            NumberChoice.QuickPick -> {
                val main = DrawingGenerator.distinctNumbers(game.mainPool, game.mainPick, rng)
                val bonus = if (game.bonusPool > 0) rng.nextInt(1, game.bonusPool + 1) else null
                main to bonus
            }
            is NumberChoice.Fixed -> numberChoice.mainNumbers to numberChoice.bonusNumber
            NumberChoice.RandomOnce -> error("RandomOnce is resolved to Fixed at run start")
        }
        val bundledMultiplierOption = strategy.optionIds.firstNotNullOfOrNull { id ->
            (game.options.first { it.id == id } as? GameOption.Multiplier)?.takeIf { it.priceCentsPerPlay == 0L }
        }
        val perTicketMultiplier = bundledMultiplierOption?.let { DrawingGenerator.rollMultiplier(it, rng) }
        return Ticket(mainNumbers, bonusNumber, strategy.optionIds, perTicketMultiplier)
    }

    private fun evaluateSecondDraw(option: GameOption.SecondDraw, ticket: Ticket, draw: DrawResult): Long {
        val matches = PrizeEvaluator.matchCount(ticket, draw)
        val bonusMatch = PrizeEvaluator.bonusMatched(ticket, draw)
        val tier = option.prizeTiers.firstOrNull { it.mainMatches == matches && it.bonusMatch == bonusMatch }
        return tier?.baseAmountCents ?: 0L
    }

    /**
     * Keeps at most ~2x[maxPoints] samples in memory: once full, compacts to every other point and
     * doubles the sampling stride, so arbitrarily long [StopCondition.UntilJackpot] runs stay bounded.
     */
    private class BoundedTimeline(private val maxPoints: Int) {
        private val points = ArrayList<TimelinePoint>(maxPoints * 2)
        private var stride = 1
        private var counter = 0L

        fun maybeAdd(point: TimelinePoint) {
            if (counter % stride == 0L) {
                points.add(point)
                if (points.size >= maxPoints * 2) {
                    val compacted = ArrayList<TimelinePoint>(points.size / 2 + 1)
                    for (i in points.indices step 2) compacted.add(points[i])
                    points.clear()
                    points.addAll(compacted)
                    stride *= 2
                }
            }
            counter++
        }

        fun toList(): List<TimelinePoint> = points.toList()
    }
}

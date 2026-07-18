package dev.marty.lotz.sim.rules

import kotlinx.datetime.DayOfWeek

/**
 * The three launch [GameDefinition]s. Figures verified 2026-07-17 against official sources — see
 * docs/game-rules.md for the full prize charts, sources, and any drift from the build plan.
 */
object Games {

    val megabucks = GameDefinition(
        id = "or-megabucks",
        displayName = "Oregon's Game Megabucks",
        mainPool = 48,
        mainPick = 6,
        bonusPool = 0,
        basePriceCents = 100,
        playsPerBasePrice = 2,
        prizeTiers = listOf(
            PrizeTier(key = "6", mainMatches = 6, isJackpot = true),
            PrizeTier(key = "5", mainMatches = 5, baseAmountCents = 800_00),
            PrizeTier(key = "4", mainMatches = 4, baseAmountCents = 40_00),
            // Free ticket without Kicker, represented at its cash-equivalent (one play's price).
            PrizeTier(key = "3", mainMatches = 3, baseAmountCents = 50),
        ),
        drawDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
        baseJackpotCents = 1_000_000_00,
        options = listOf(
            GameOption.FlatMultiplier(
                id = "kicker",
                priceCentsPerPlay = 100,
                multiplier = 4,
                appliesToTierKeys = setOf("5", "4"),
                unlockAmountsCents = mapOf("3" to 4_00),
            ),
        ),
    )

    val powerball = GameDefinition(
        id = "powerball",
        displayName = "Powerball",
        mainPool = 69,
        mainPick = 5,
        bonusPool = 26,
        basePriceCents = 200,
        playsPerBasePrice = 1,
        prizeTiers = listOf(
            PrizeTier(key = "5+1", mainMatches = 5, bonusMatch = true, isJackpot = true),
            PrizeTier(key = "5+0", mainMatches = 5, bonusMatch = false, baseAmountCents = 1_000_000_00),
            PrizeTier(key = "4+1", mainMatches = 4, bonusMatch = true, baseAmountCents = 50_000_00),
            PrizeTier(key = "4+0", mainMatches = 4, bonusMatch = false, baseAmountCents = 100_00),
            PrizeTier(key = "3+1", mainMatches = 3, bonusMatch = true, baseAmountCents = 100_00),
            PrizeTier(key = "3+0", mainMatches = 3, bonusMatch = false, baseAmountCents = 7_00),
            PrizeTier(key = "2+1", mainMatches = 2, bonusMatch = true, baseAmountCents = 7_00),
            PrizeTier(key = "1+1", mainMatches = 1, bonusMatch = true, baseAmountCents = 4_00),
            PrizeTier(key = "0+1", mainMatches = 0, bonusMatch = true, baseAmountCents = 4_00),
        ),
        drawDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
        baseJackpotCents = 20_000_000_00,
        options = listOf(
            GameOption.Multiplier(
                id = "power-play",
                priceCentsPerPlay = 100,
                // Weights when jackpot <= $150M (10x ball in the wheel); see docs/game-rules.md.
                weights = mapOf(2 to 24, 3 to 13, 4 to 3, 5 to 2, 10 to 1),
                tierCaps = mapOf("5+0" to 2_000_000_00),
            ),
            GameOption.SecondDraw(
                id = "double-play",
                priceCentsPerPlay = 100,
                prizeTiers = listOf(
                    PrizeTier(key = "5+1", mainMatches = 5, bonusMatch = true, baseAmountCents = 10_000_000_00),
                    PrizeTier(key = "5+0", mainMatches = 5, bonusMatch = false, baseAmountCents = 500_000_00),
                    PrizeTier(key = "4+1", mainMatches = 4, bonusMatch = true, baseAmountCents = 50_000_00),
                    PrizeTier(key = "4+0", mainMatches = 4, bonusMatch = false, baseAmountCents = 500_00),
                    PrizeTier(key = "3+1", mainMatches = 3, bonusMatch = true, baseAmountCents = 500_00),
                    PrizeTier(key = "3+0", mainMatches = 3, bonusMatch = false, baseAmountCents = 20_00),
                    PrizeTier(key = "2+1", mainMatches = 2, bonusMatch = true, baseAmountCents = 20_00),
                    PrizeTier(key = "1+1", mainMatches = 1, bonusMatch = true, baseAmountCents = 10_00),
                    PrizeTier(key = "0+1", mainMatches = 0, bonusMatch = true, baseAmountCents = 7_00),
                ),
            ),
        ),
    )

    val megaMillions = GameDefinition(
        id = "mega-millions",
        displayName = "Mega Millions",
        mainPool = 70,
        mainPick = 5,
        bonusPool = 24,
        basePriceCents = 500,
        playsPerBasePrice = 1,
        prizeTiers = listOf(
            PrizeTier(key = "5+1", mainMatches = 5, bonusMatch = true, isJackpot = true),
            PrizeTier(key = "5+0", mainMatches = 5, bonusMatch = false, baseAmountCents = 2_000_000_00),
            PrizeTier(key = "4+1", mainMatches = 4, bonusMatch = true, baseAmountCents = 20_000_00),
            PrizeTier(key = "4+0", mainMatches = 4, bonusMatch = false, baseAmountCents = 1_000_00),
            PrizeTier(key = "3+1", mainMatches = 3, bonusMatch = true, baseAmountCents = 400_00),
            PrizeTier(key = "3+0", mainMatches = 3, bonusMatch = false, baseAmountCents = 20_00),
            PrizeTier(key = "2+1", mainMatches = 2, bonusMatch = true, baseAmountCents = 20_00),
            PrizeTier(key = "1+1", mainMatches = 1, bonusMatch = true, baseAmountCents = 14_00),
            PrizeTier(key = "0+1", mainMatches = 0, bonusMatch = true, baseAmountCents = 10_00),
        ),
        drawDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
        baseJackpotCents = 50_000_000_00,
        options = listOf(
            GameOption.Multiplier(
                id = "built-in-multiplier",
                priceCentsPerPlay = 0,
                weights = mapOf(2 to 15, 3 to 10, 4 to 4, 5 to 2, 10 to 1),
            ),
        ),
    )

    val all = listOf(megabucks, powerball, megaMillions)
}

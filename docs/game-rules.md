# Game rules — as verified 2026-07-17

Figures below were checked against official/authoritative sources on this date. Where the build-plan header's
planning-time figures differed from what was found, the difference is called out under **Drift from plan**.
Odds in code are **computed combinatorially** from each game's matrix (see `Combinatorics.oddsOneIn` in
`core-sim`), not hardcoded — the published odds below are used only as test fixtures to confirm the formula
is right.

## Oregon Megabucks

- **Matrix:** pick 6 of 48 (no bonus ball).
- **Price:** $1 buys **2 plays** (this is why the combinatorial per-play jackpot odds of 1 in 12,271,512
  is advertised by third-party sites as "1 in 6,135,756 per dollar" — half the combinatorial odds since a
  dollar buys two plays). Code models this as `basePriceCents = 100`, `playsPerBasePrice = 2`.
- **Draw schedule:** Monday, Wednesday, Saturday, 7:29 PM PT.
- **Jackpot:** starts at $1,000,000, grows with sales until won, resets to $1,000,000 after a win.
- **Prize tiers:** match 6 = jackpot; match 5 ≈ $800; match 4 ≈ $40; match 3 = free ticket (no cash tier
  without the Kicker). Match-5/4/3 amounts are **pari-mutuel** (they vary by drawing sales) — the Oregon
  Lottery publishes them as "average" prizes. Code uses the published average as a fixed `baseAmountCents`;
  this is a documented simplification (a true pari-mutuel pool isn't modeled in v1).
- **Kicker option:** +$1 per play. Multiplies match-4 and match-5 prizes ×4, and unlocks a match-3 cash
  prize (published as $4) that otherwise pays only a free ticket.
- **Drift from plan:** plan header said jackpot odds "1 : 12,271,512 per play" — confirmed correct
  combinatorially (`C(48,6)`); the "1 in 6,135,756" figure seen on third-party sites is the marketing
  per-dollar odds (2 plays/dollar), not a different matrix. No other drift.

Sources: [Oregon Lottery — Megabucks](https://www.oregonlottery.org/jackpot/megabucks/),
[lottery.net — Megabucks](https://www.lottery.net/oregon/megabucks).

## Powerball

- **Matrix:** 5 of 69 (white balls) + 1 of 26 (Powerball).
- **Price:** $2/play. Add-ons: **Power Play** +$1, **Double Play** +$1.
- **Draw schedule:** Monday, Wednesday, Saturday, 10:59 PM ET.
- **Jackpot:** odds 1 in 292,201,338 (`C(69,5) * 26`, confirmed combinatorially); starts near $20,000,000.
- **Prize tiers** (9 total; amount / odds, confirmed against `powerball.com/powerball-prize-chart`):

  | Match | Prize | Odds (1 in) |
  |---|---|---|
  | 5 + PB | Jackpot | 292,201,338.00 |
  | 5 + 0 | $1,000,000 | 11,688,053.52 |
  | 4 + PB | $50,000 | 913,129.18 |
  | 4 + 0 | $100 | 36,525.17 |
  | 3 + PB | $100 | 14,494.11 |
  | 3 + 0 | $7 | 579.76 |
  | 2 + PB | $7 | 701.33 |
  | 1 + PB | $4 | 91.98 |
  | 0 + PB | $4 | 38.32 |

- **Power Play** (+$1/play): multiplies all non-jackpot prizes. Multiplier ball drawn once per drawing
  (applies to every Power Play ticket that drawing, not per-ticket). Weights when the jackpot is
  **≤ $150,000,000** (43 balls: 24×2X, 13×3X, 3×4X, 2×5X, 1×10X — approx. odds 2x≈1/1.75, 3x≈1/3.23,
  4x≈1/14.0, 5x≈1/21.0, 10x≈1/43.0); the 10X ball is removed from the wheel above that jackpot threshold.
  The 5+0 (Match 5) tier is **capped at $2,000,000 flat** regardless of multiplier (2×$1M already equals
  the cap; 3×/4×/5×/10× are capped down to it).
- **Double Play** (+$1/play): a second, independent drawing using the same numbers, own prize pool, own
  jackpot-tier cap of $10,000,000 (not a real progressive jackpot). Power Play does not apply to Double
  Play. Prize chart (confirmed against `powerball.com/double-play-prize-chart`, same odds as primary draw):
  5+1 $10,000,000; 5+0 $500,000; 4+1 $50,000; 4+0 $500; 3+1 $500; 3+0 $20; 2+1 $20; 1+1 $10; 0+1 $7.
- **Drift from plan:** none material; plan's "Match-5 capped $2M" and "10× only below a jackpot threshold"
  both confirmed. Confirmed threshold is $150,000,000.

Sources: [powerball.com — Prize Chart](https://www.powerball.com/powerball-prize-chart),
[powerball.com — Double Play Prize Chart](https://www.powerball.com/double-play-prize-chart),
[lottery.net — Power Play](https://www.lottery.net/powerball/power-play).

## Mega Millions (post-April-2025 format)

- **Matrix:** 5 of 70 (white balls) + 1 of 24 (Mega Ball).
- **Price:** $5/play, **multiplier built into the price** (no separate add-on purchase; "Megaplier" from
  the pre-2025 format is discontinued/superseded by this built-in multiplier).
- **Draw schedule:** Tuesday, Friday, 11:00 PM ET.
- **Jackpot:** odds 1 in 290,472,336 (`C(70,5) * 24`, confirmed combinatorially); starts at $50,000,000.
- **Prize tiers** (9 total; confirmed against `megamillions.com/how-to-play.aspx`):

  | Match | Prize | Odds (1 in) |
  |---|---|---|
  | 5 + MB | Jackpot | 290,472,336 |
  | 5 + 0 | $2,000,000 | 12,629,232 |
  | 4 + MB | $20,000 | 893,761 |
  | 4 + 0 | $1,000 | 38,859 |
  | 3 + MB | $400 | 13,965 |
  | 3 + 0 | $20 | 607 |
  | 2 + MB | $20 | 665 |
  | 1 + MB | $14 | 86 |
  | 0 + MB | $10 | 35 |

- **Built-in multiplier:** every play is randomly assigned one of 2X/3X/4X/5X/10X from a field of 32
  (15×2X, 10×3X, 4×4X, 2×5X, 1×10X), printed on the ticket at purchase time (i.e. rolled **per ticket**,
  unlike Powerball's Power Play which is rolled once per drawing). Applies to all non-jackpot prizes; the
  jackpot itself is never multiplied.
- **Drift from plan:** plan header's per-tier prize amounts (e.g. "5+0 → $1,000,000") were the **pre-2025**
  Megaplier-era numbers; the current post-April-2025 $5 format pays higher base amounts (5+0 → $2,000,000,
  4+MB → $20,000, etc., per the table above) since the multiplier is now baked into a pricier ticket. Code
  follows the confirmed current table, not the plan header. Base jackpot $50M matches the plan.

Sources: [megamillions.com — How to Play](https://www.megamillions.com/how-to-play.aspx),
[megamillions.com — New Mega Millions arrives in April](https://www.megamillions.com/News/2025/New-Mega-Millions%C2%AE-arrives-in-April.aspx).

## Shared drawing schedule note

Confirmed no overlap: Megabucks (Mon/Wed/Sat), Powerball (Mon/Wed/Sat, different draw time/authority),
Mega Millions (Tue/Fri) — each game's `GameDefinition.drawDays` models its own independent schedule.

# Rolling-jackpot market model — calibrated 2026-07-17

Lotz simulates the *other* plays in each drawing, then samples their jackpot winners. It is an offline,
transparent approximation—not a forecast and not a live feed. The model deliberately uses only a few
auditable coefficients. All money remains integer cents; floating point is used only for probabilities and
curve evaluation.

## Equations and behavior

For advertised annuity jackpot `J`, reference jackpot `J0`, reference jackpot-bearing plays `T0`, and
elasticity `e`, expected other-player plays are:

```text
T(J) = T0 * (max(J, J0) / J0)^e
```

Powerball and Mega Millions use a continuous second segment above the documented `$400M` frenzy threshold:

```text
T(J) = T($400M) * (J / $400M)^efrenzy
```

Actual simulated plays multiply the expectation by one draw-level uniform shock in `[1-noise, 1+noise]`.
The shock stands in for draw day, seasonality, competing jackpots, and forecast error. It is bounded so a seed
always replays exactly and extreme random sales cannot dominate the simulation.

Given `T` jackpot-bearing plays and per-play jackpot odds `O`, other winning plays are
`Poisson(lambda = T/O)`. This is the standard rare-independent-event limit. The implementation uses exact
Knuth sampling. Means above `20.0` are split into independent chunks no larger than 20; sums of independent
Poisson variables are Poisson, while chunking avoids underflow in `exp(-lambda)`. The launch-game means are
well below one even during large jackpots.

If the player wins, the advertised jackpot is divided evenly among `1 + otherWinners`; cash value is the
player's annuity share times the fixed cash ratio. If only co-players win, the player receives zero. Either
case resets the next draw to the game's rule-defined base jackpot. With no winner:

```text
advertised increase = T * base ticket price * advertised contribution rate
```

The advertised amount is an annuity value. Actual lotteries revise advertised jackpots using final sales,
reserve pools, securities bids, and minimum-increase rules; Lotz does not model those operational adjustments.
Sub-cent split remainders are truncated because the domain stores cents.

## Coefficients in code

These values map one-for-one to `MarketCoefficients.launchGames`.

| Game | `J0` | `T0` plays | `e` | Frenzy threshold / `efrenzy` | Noise | Base price/play | Advertised contribution | Cash ratio |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Oregon Megabucks | $1M | 197,000 | 0.345 | none | ±20% | $0.50 | 0.5821 | 0.50 |
| Powerball | $20M | 7,000,000 | 0.231 | $400M / 1.46 | ±25% | $2.00 | 0.755702 | 0.45 |
| Mega Millions | $50M | 4,272,000 | 0.284 | $400M / 1.166 | ±12% | $5.00 | 0.614011 | 0.45 |

The reference jackpots and base prices are the rule values already sourced in [game-rules.md](game-rules.md).
The derivations for every remaining number follow.

## Oregon Megabucks calibration

Oregon does not publish a per-draw sales worksheet comparable to the national games. We therefore infer
jackpot-bearing play counts from exact-match-3 winners. A play matches exactly three of six with probability
`C(6,3) * C(42,3) / C(48,6) = 0.01871000085` (1 in 53.44735). Published payout tables report:

| Draw | Jackpot | Match-3 winners (base + Kicker) | Implied plays |
|---|---:|---:|---:|
| 2024-11-20 | $2.0M | 3,694 + 965 = 4,659 | 249,011 |
| 2024-12-14 | $3.0M | 5,516 + 1,302 = 6,818 | 364,404 |
| 2024-12-30 | $3.7M | 4,525 + 1,236 = 5,761 | 307,910 |

Sources: [Nov. 20 payout](https://www.lottonumbers.com/oregon-megabucks/numbers/11-20-2024),
[Dec. 14 payout](https://www.lottonumbers.com/oregon-megabucks/numbers/12-14-2024), and
[Dec. 30 payout](https://www.lottonumbers.com/oregon-megabucks/numbers/12-30-2024). These are secondary
archives; each table's jackpot progression and winner tiers are consistent with Oregon's published game rules.

The endpoint elasticity is `ln(307,910 / 249,011) / ln(3.7 / 2.0) = 0.345`. Back-solving the $2M point gives
196,827 plays at $1M, rounded to `T0 = 197,000`. The middle point is about 27% above the curve and the last
point about 13% below it, so `noise = 0.20` captures most observed draw scatter without pretending that three
points identify a precise demand law.

The scale is cross-checked against the Oregon Lottery's official FY2024 Megabucks revenue of `$31,061,082`:
about $199,000 per drawing over roughly 156 draws. That revenue includes Kicker purchases, while `T` counts
base jackpot chances at $0.50 per play, so the figures are compatible rather than directly equal.
[Oregon Lottery FY2024 annual report](https://www.oregonlottery.org/annual-report-2024/).

Oregon rules reserve about 70% of Megabucks revenue for prizes and allocate 8.50% and 3.29% of the first/base
dollar to match-4 and match-5 pools. The residual `70% - 8.50% - 3.29% = 58.21%` funds the advertised annuity
jackpot, producing contribution `0.5821`. The rules explicitly say the payout percentage is based on 30-year
annuity value. [OAR 177-075-0035 attachment](https://secure.sos.state.or.us/oard/viewAttachment.action?ruleVrsnRsn=28442).

Oregon confirms that winners may choose cash or 30 annual installments but does not publish a stable current
conversion factor. Lotz uses `cash ratio = 0.50`, the public game explanation's stated “half the advertised
value” simplification. [Megabucks cash/annuity explanation](https://www.lottery.net/oregon/megabucks).

## Powerball calibration

The Texas Lottery's official 2026 Powerball jackpot-estimation worksheet contains national base-game sales,
advertised/cash jackpots, draw coverage, and projections. Actual low-jackpot sales begin at `$12,897,798` for
a $20M jackpot: 6,448,899 $2 plays. We round this cross-day baseline to `T0 = 7,000,000` because the same
worksheet documents material Monday/Wednesday/Saturday variation.

Around $400M the actual draws span `$22.1M` (Monday, $416M), `$25.7M` (Wednesday, $434M), and `$33.0M` to
`$36.4M` (Saturday, $396M/$457M). We use a representative `$28M`, or 14M plays. The exponent joining 7M
plays at $20M to 14M at $400M is `ln(2) / ln(20) = 0.231`. The worksheet's $1B Saturday projection is
`$107,437,668`; the continuous exponent from $28M at $400M to that point is approximately `1.46`. This is the
explicit jackpot-fever segment. The ordinary-draw range around $400M is roughly ±25% of the representative
value, giving `noise = 0.25`.

Source: [Texas Lottery Powerball estimate dated 2026-07-13](https://www.texaslottery.com/export/sites/lottery/Documents/jackpotestimates/pb20260713.pdf).
The $1B point is a worksheet projection, not an observed sale, and is used only to control the high-jackpot
tail. Calibration tests allow 2% at that point.

Official rules allocate `34.0066%` of base-game sales to the jackpot prize. The worksheet's $20M jackpot has
a `$9.0M` cash value, so fixed `cash ratio = 9/20 = 0.45`. Converting cash-pool contributions to advertised
annuity dollars gives `0.340066 / 0.45 = 0.755702`.
[OAR 177-085-0025 Powerball prize-pool attachment](https://secure.sos.state.or.us/oard/viewAttachment.action?ruleVrsnRsn=288373).
The same worksheet shows the ratio varies with interest rates; 0.45 is a reproducible snapshot, not a promise.

## Mega Millions calibration

The Texas Lottery's official worksheet for the long July–November 2025 roll provides unusually clean
post-April-2025 $5-game data. Selected actual base-game sales are:

| Advertised jackpot | Gross base-game sales | Jackpot-bearing plays |
|---:|---:|---:|
| $50M | $21,362,490 | 4,272,498 |
| $400M | $38,515,850 | 7,703,170 |
| $980M | $109,762,000 | 21,952,400 |

Source: [Texas Lottery Mega Millions estimate dated 2025-11-14](https://www.texaslottery.com/export/sites/lottery/Documents/jackpotestimates/mm20251114.pdf).

We round the base to `T0 = 4,272,000`. The endpoint exponent from $50M to $400M is `0.284`; the continuous
endpoint exponent from $400M to $980M is `1.166`, again representing jackpot fever. Nearby values and weekday
variation are generally within about 12% of the fitted curve, so `noise = 0.12`.

Current post-2025 rules allocate `27.6305%` of sales to the Jackpot Prize Pool. The worksheet's early roll
uses about `$22.4M` cash for a $50M annuity, rounded to the same `cash ratio = 0.45` snapshot. Thus advertised
contribution is `0.276305 / 0.45 = 0.614011`.
[OAR 177-098-0040](https://www.law.cornell.edu/regulations/oregon/Or-Admin-Code-SS-177-098-0040).

## Known boundaries

- Sales are co-player jackpot-bearing plays. Power Play, Double Play, Kicker, and other add-on revenue neither
  creates extra jackpot combinations nor contributes through these base-game curves.
- The same curve applies to every draw day. Bounded noise absorbs day effects rather than adding calendar
  coefficients unsupported for all three games.
- The Poisson model assumes independently and uniformly chosen combinations. Real players cluster on birthdays
  and other patterns, so real split risk can differ even when the expected number of winners is right.
- Cash ratios and annuity factors move with interest rates. Lotz intentionally fixes its sourced snapshot so a
  seed remains reproducible offline.
- Advertised jackpot growth is not rounded to the lotteries' public display increments; internal cents are kept
  so many small Megabucks contributions are not lost.

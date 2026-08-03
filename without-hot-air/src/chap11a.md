# 11a The machines behind the screen

*Editor's note: this chapter is new in the 2026 revision. It is not David MacKay's writing. Added by Örjan Lundberg.*

Chapter 11 has a short section called "Powering the hidden tendrils of the information age". It reports Jonathan Koomey's finding that in 2005 the servers in American data centres, with their cooling and backup power, used **0.4 kWh per day per person** — "just over 1% of US electricity consumption" — and notes that this had doubled since 2000 as the server count went from 5.6 to 10 million.

MacKay put that in a chapter about gadgets, and in 2005 he was right to. A percent of national electricity is a rounding error next to heating and driving. This chapter exists because the tendrils stopped being hidden.

## What the number is now

| | Data centre electricity | Share of that grid |
|---|---|---|
| World, 2024 | 415 TWh | **1.5%** |
| United States, 2023 | 176 TWh | **4.4%** |
| Ireland, 2024 | about 7 TWh | **22%** |

In this book's units, an American's share of data-centre electricity is about **1.4 kWh/d**, against MacKay's 0.4 twenty years ago. A citizen of the world averages 0.14. **An Irish person's share is about 3.6 kWh/d** — which is close to the 5 kWh/d that chapter 11 offers as the figure for a whole houseful of gadgets left on all the time.[^dcnow]

Ireland is the case worth staring at, because it is the future arriving early in a small country. Data centres took **5% of Irish electricity in 2015 and 22% in 2024**, more than quadrupling in nine years. No other consumer of electricity has ever grown like that inside a developed grid, and Ireland now has more of its power going into computation than into all its homes' lighting, cooking and appliances combined.

## The growth rate is the whole story

Absolute numbers understate this, because the interesting quantity is the exponent.

American data-centre demand grew at about **7% a year from 2014 to 2018**, then **18% a year from 2018 to 2023**, and is projected at **13 to 27% a year to 2028**, reaching **325 to 580 TWh, or 6.7 to 12% of American electricity**. Globally the IEA expects **485 TWh in 2025 to become about 950 TWh in 2030**, roughly 3% of world electricity.[^dcgrowth]

Notice what the 2018 inflection is. Between 2010 and 2018 data-centre computing rose enormously while electricity barely moved, because efficiency — better chips, virtualisation, hyperscale facilities replacing cupboards full of servers — absorbed the growth. That is the same story as chapter 9's light bulb: the service expanded and the energy did not.

**Then it stopped working.** The efficiency gains ran out at roughly the moment demand for AI training and inference began, and since 2018 the curve has followed demand rather than efficiency. Servers for artificial intelligence were **24% of server electricity and 15% of total data-centre energy in 2024**, and are projected at **35 to 50% of data-centre power by 2030**.

This is the shape MacKay's method is built to expose. A quantity growing at 18% a year doubles in four years. Applied to something that is already 4% of a national grid, four more doublings reaches the whole grid. The exponent cannot continue, and the interesting question is what stops it.

## What is hidden

Three costs sit outside the electricity meter, and all three are the sort MacKay would have insisted on counting.

**Water.** Cooling evaporates it. The industry average is about **1.8 litres per kWh** of electricity used, though the best operators are far better — Amazon reports a fleet average of **0.19 L/kWh**, nearly ten times lower. Virginia's data centres consumed over **2.1 billion gallons in 2023**, about 8 million cubic metres, with Loudoun County alone at 900 million gallons.[^dcwater] At 1.8 L/kWh the world's 415 TWh implies something like 750 million cubic metres — and unlike electricity, water is consumed where it is drawn, in places increasingly chosen for cheap power rather than plentiful rivers.

**Grid position.** A data centre is a large, constant, inflexible load that wants to be built quickly. It is therefore the opposite of what chapters 26 and 28a say a renewable-heavy system needs. Where it lands, it competes for connection capacity with everything else, and because it can pay more per kilowatt-hour than a smelter or a housing estate, it wins. Ireland's grid operator has had to refuse or defer connections in Dublin for exactly this reason.

**The rebound that did not happen.** Chapter 9 concludes that Britain kept its lighting efficiency gain because demand for light saturated. Computation has not saturated and shows no sign of it. Every efficiency improvement in computing since 1950 has been met by doing more computing, which is Jevons in its purest available form, and the AI build-out is the largest instance yet.

## What it means for the balance sheet

MacKay's summary figure for chapter 11 is 5 kWh/d for a houseful of gadgets. Data centres are not in that number in any meaningful way — his 0.4 kWh/d for America was a footnote.

On today's American figures they would add about **1.4 kWh/d per person**, and on the 2028 projection something between 2 and 4. That is not enormous beside heating's 37 or driving's 40. **But it is new demand, arriving fast, in the one form the system finds hardest to accommodate: electricity, constant, and inflexible.**

And it changes an argument the book makes elsewhere. Chapters 6, 10 and 28a are about a system where the difficulty is that supply fluctuates and demand does not follow. A data centre is demand that could follow — computation can in principle be moved in time and space more easily than heat or transport can — but is currently built to run flat out because the capital cost of the hardware dwarfs the electricity. If that ever changes, the largest new load on the grid becomes the largest new source of flexibility. Nothing in the physics forbids it. The economics, so far, point the other way.

## Notes and further reading

[^dcnow]: Global data-centre electricity of 415 TWh in 2024, about 1.5% of world electricity, is from the International Energy Agency's *Energy and AI* (2025). The American figure of 176 TWh and 4.4% of national consumption in 2023, excluding cryptocurrency mining, is from Lawrence Berkeley National Laboratory's *2024 United States Data Center Energy Usage Report*, prepared for the Department of Energy. Irish figures are from the Central Statistics Office: data centres took 22% of metered electricity in 2024, against 5% in 2015. Per-person figures here are computed by dividing by populations of roughly 8.2 billion, 335 million and 5.4 million; they are shares of national consumption, not a claim about what any individual uses, since much of the computation serves users in other countries. That last point matters for Ireland especially, where the data centres largely serve Europe rather than Ireland, so the electricity is Irish and the service is not.

[^dcgrowth]: Growth rates and the 2028 range are from the Berkeley Lab report: about 7% a year 2014–2018, about 18% a year 2018–2023, and 13–27% a year projected 2023–2028, giving 325–580 TWh or 6.7–12% of US electricity. The global 2030 projection of about 950 TWh, roughly 3% of world electricity, and the AI shares — 24% of server electricity and 15% of data-centre energy in 2024, rising to 35–50% of data-centre power by 2030 — are the IEA's. All of these are projections made during a capital-investment boom, and projections made during booms have a poor record; the 2028 range spanning nearly a factor of two is the honest reflection of that. The claim that efficiency absorbed growth until about 2018 is the standard reading of the Berkeley Lab series and of Masanet et al. (2020), which found global data-centre energy roughly flat from 2010 to 2018 despite a sixfold rise in compute.

[^dcwater]: Water usage effectiveness figures: an industry average near 1.8 litres per kWh, against Amazon Web Services' reported global fleet average of 0.19 L/kWh. Virginia consumption of over 2.1 billion US gallons in 2023, with Loudoun County near 900 million, is from state and county reporting compiled by the Center for Secure Water at the University of Illinois and others. Two cautions. WUE varies by more than an order of magnitude with cooling design and climate, so a single global multiplication is indicative only — the 750 million cubic metre figure in the text should be read as an order of magnitude, not an estimate. And water *withdrawn* is not water *consumed*: evaporative cooling consumes most of what it takes, while some designs return most of it, and public reporting rarely distinguishes the two.

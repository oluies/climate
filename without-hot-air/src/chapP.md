# P A book that starts where this one stops

*Editor's note: this chapter is new in the 2026 revision. It is not David MacKay's writing. Added by Örjan Lundberg.*

This book asks whether a country's energy demand can be met without fossil fuels, and answers it in kilowatt-hours per day per person. It treats demand as a thing to be supplied. Thomas Murphy's [*Energy and Human Ambitions on a Finite Planet*](https://escholarship.org/uc/energy_ambitions) asks the question MacKay's method brackets out: what happens if demand keeps growing at the rate it has grown for the last few centuries?

It is a physics textbook, written for a course at the University of California, San Diego, and it is free: the university publishes it under a Creative Commons licence at [escholarship.org/uc/energy_ambitions](https://escholarship.org/uc/energy_ambitions). It is recommended here for the same reason MacKay's book is worth reading twice: it does arithmetic in public, states its assumptions, and lets the reader check every step.[^murphybook]

## Galactic-scale energy

The argument that makes the case is in his first chapter, and it runs on one number. Take 2.3% a year, which is a factor of ten a century, and which is roughly what industrial energy use has actually done. Then extrapolate, not because the extrapolation is a forecast but because watching where it breaks tells you what kind of limit you are up against.

Within 400 years, humanity would be using every scrap of the solar energy that reaches the planet, at 100% efficiency. Within about 1,400 years it would need the entire output of the Sun — a sphere built around the star to catch all of it. (Murphy notes in passing that if the whole Earth were beaten into such a shell at our orbit it would be less than 4 mm thick.) A hundred billion stars in the galaxy buys eleven more centuries, so the whole Milky Way goes in about 2,500 years, and the visible universe in about 5,000.

The point is not the timescales. It is that the growth rate we treat as normal runs out of *universe* in a few thousand years, which means it must stop for reasons that have nothing to do with policy, or oil, or anybody's preferences.

## The limit that arrives first

The interesting part is what stops it long before the stars do, and here the physics is the same as chapter 1 of this book. Whatever energy we use ends as heat. The Earth balances the sunlight it absorbs against what it radiates, and a growing power source adds to the first side of that balance.

Today's 18 TW, spread over the disk the Earth presents to the Sun, is 0.14 W/m<sup>2</sup> against the 961 W/m<sup>2</sup> of sunlight absorbed there — a rounding error. Grow it by a factor of ten a century and the rounding error becomes the term that matters.

![Earth's surface temperature against years of growth at a factor of ten a century. The curve sits at 288 K for two centuries, reaches 297 K at 300 years, and crosses the boiling point of water at 417 years. Murphy's own published values are marked and sit on the curve.](/img/without-hot-air/fig-p1-waste-heat.svg)

<span class="figurenumber">Figure P.1.</span> *Added in the 2026 revision.* Murphy's waste-heat calculation, recomputed from the equilibrium this book's chapter 1 uses. The dots are his published table; the curve is the same equation solved here.[^murphyheat]

After one century the surface is 0.1 °C warmer from waste heat alone. After two, about 1 °C — comparable with what burning things has done to date, and by a completely different mechanism. After three, 9 °C. After 417 years, the oceans boil. Keep going and the Earth's surface passes the temperature of the Sun's inside a thousand years.

Nothing in that depends on where the energy comes from. Fusion does not help; nor does solar power from orbit, nor anything else yet imagined, because the constraint is not the source but the exhaust. It is the one limit this book's own method — supply against demand, both in kilowatt-hours — cannot see, because it holds demand fixed.

## Why it belongs next to this book

MacKay's argument is that Britain cannot run on renewables at British population density without either enormous machines or imports, and the binding quantity is area: 2 W/m<sup>2</sup> for wind, 10 to 20 for solar, against a demand of 125 kWh/d per person.

Murphy's argument is that no civilisation can grow its energy use indefinitely on one planet, and the binding quantity is thermodynamics. The two books use the same method — an estimate anyone can check, carried to the point where it changes what you think — on two different questions, and they meet in the middle. If the arithmetic in this book persuaded you, the arithmetic in that one is the next thing to read.

The thing to take from both, in the end, is the habit rather than any particular number. Murphy puts it as a rule for reading anyone's energy plan: ask what quantity is being conserved, then check whether the plan respects it.

## Notes and further reading

[^murphybook]: Thomas W. Murphy, Jr., *Energy and Human Ambitions on a Finite Planet: Assessing and Adapting to Planetary Limits*, University of California, San Diego, March 2021, ISBN 978-0-578-86717-5, DOI [10.21221/S2978-0-578-86717-5](https://doi.org/10.21221/S2978-0-578-86717-5), free at <https://escholarship.org/uc/energy_ambitions>. It is licensed Creative Commons Attribution-NonCommercial 4.0 International, which is why the numbers above can be reproduced here with attribution; this edition is Attribution-NonCommercial-ShareAlike, and neither is for sale. Murphy also ran the blog *Do the Math*, where the galactic-scale argument first appeared in 2011. The figures quoted in this chapter are his: the growth rate of 2.3% a year and its factor of ten a century, the 400-year and 1,400-year and 2,500-year and 5,000-year timescales of his table 1.3, the 4 mm Dyson shell of his box 1.3, and the temperatures of his table 1.4.

[^murphyheat]: Figure P.1 is computed here from Murphy's own statement of the problem, not copied from his figure. The balance is the one this book's chapter 1 uses: 0.707 × 1360 W/m<sup>2</sup> absorbed over the planet's projected disk against σ*T*<sup>4</sup> radiated over its whole surface, which gives 255 K, plus the 33 K the greenhouse effect adds to reach today's observed 288 K. Human power enters as an extra term on the input side, starting at 18 TW — 0.14 W/m<sup>2</sup> over that disk — and multiplied by ten each century, which is how Murphy works the growth rather than compounding 1.023 exactly, since that would give 9.7 per century and would not reproduce his table. The recompute matches four of his five published rows: 288.3 K against his 288.1 at a hundred years, 289.1 against 288.9 at two hundred, 297.0 against 296.9 at three hundred, and 373.0 against his 373.0 at 417 years, which is where he puts the boiling point. The exception is his 400-year row, where the same equation gives 352 K and his table prints 344; his own power-density column for that row, 1,400 W/m<sup>2</sup>, is the one this recompute uses, so the discrepancy is in his temperature rather than in the growth. The data task fails if any of the other four drifts by more than 1.5 K. Two cautions of the kind Murphy himself would insist on. The 33 K greenhouse offset is held constant while the planet heats, which is wrong in a direction that makes the curve optimistic. And the whole exercise assumes the energy is used on the Earth's surface; the argument's force is that it does not matter where it comes from, not that there is no escape at all.

# M Energy return on investment

*A chapter added in the 2026 revision.* Chapter 3 asks a question and then declines to answer it properly:

> **What about the energy-cost of producing the car's fuel?**
>
> Good point. When I estimate the energy consumed by a particular activity, I tend to choose a fairly tight "boundary" around the activity. […] It's been estimated that making each unit of petrol requires an input of 1.4 units of oil and other primary fuels.

That is the honest admission of a book built on tight boundaries, and it points at a whole literature. This chapter is that literature's technical partner, in the same way that chapter A partners chapter 3 and chapter C partners chapter 5.

## The measure

**Energy return on investment** is the ratio of energy delivered to energy spent getting it:

$$
\text{EROI} = \frac{\text{energy delivered}}{\text{energy required to deliver it}}
$$

Below 1 the thing is not an energy source at all; it is an energy sink that happens to move energy from one form to another. The concept was made quantitative by Charles Hall in the 1970s and reached a wide audience through a paper in *Science* in 1984.

The attraction is obvious. A society does not run on the energy it extracts; it runs on what is left after extraction has taken its cut. If that cut grows, the same gross production supports less. MacKay's stacks count gross energy at a tight boundary, and EROI is the correction that boundary omits.

## Why the numbers disagree so violently

Here is the difficulty, and this chapter would be dishonest to bury it. Published EROI values for the same fuel differ by more than an order of magnitude, and the differences are mostly not measurement error. They are boundary choices.

Does the "investment" include the energy to build the drilling rig? The steel mill that made the rig? The roads to the refinery? Each answer is defensible and each gives a different number. Brockway and colleagues put the problem sharply in 2019 by pointing out the "apples and oranges" character of the commonest comparison of all: the EROI of oil measured at the wellhead against that of electricity from a wind turbine. One is a raw fuel needing refining and a heat engine; the other is already the finished product.

**But the deeper trap is scale, and it is not widely understood.** Murphy and Carbajales-Dale showed that two quantities routinely both called EROI are different measures.[^proi] The first is a *facility* ratio: total energy a plant delivers over its life, divided by the energy it took to build and run. The second is an *industry or regional* ratio: energy delivered by a whole sector this year, divided by energy invested in that sector this year. They propose calling the second one **power return on investment**, PROI, and the distinction matters enormously — because a growing industry is always building plants that have not yet delivered anything.

Their worked example should be read twice. In 2018, onshore wind had a **facility-scale EROI of over 70**. The same technology, measured as a growing global industry, had a **PROI of about 25**. Nothing about the turbines differed. The ratio was depressed purely because the industry was expanding, so a large share of that year's energy investment was going into machines not yet generating. The two figures converge only as growth approaches zero.

This has a consequence the peak-oil literature never had to face. **A rapidly growing energy industry is penalised by industry-scale net-energy accounting, and a stagnant one is flattered.** Any comparison that puts a fast-growing renewable sector against a mature oil industry on this basis is measuring the growth, not the technology.

So read the table below as a rough ordering of facility-scale values, not as measurements, and not as a like-for-like comparison.

## The direction of travel

Two things in that table matter more than the levels.

**Fossil EROI is falling, and has been for a century.** Early conventional oil is often quoted at something like 100:1 — the gushers of the 1920s returned enormous energy for very little. Estimates for recent conventional oil cluster far lower, and the unconventional resources that now supply the growth are lower again: oil sands near 5, oil shale barely above 1. One widely cited series has all liquid fuels falling from about 44 in 1950 towards single figures by mid-century, and natural gas from about 140 in 1950 to under 20. The resource is not running out in the sense chapter N examines; it is getting more expensive in the only currency that ultimately matters.

**Renewable EROI has risen past much of it.** This is the reversal that dates the peak-oil literature most sharply. When these arguments were formed, solar's EROI was genuinely marginal; modern crystalline silicon in a sunny location repays its energy in one to four years against a thirty-year life. Wind was always respectable and is now comfortably above oil sands and arguably above current conventional oil.

There is a real dispute underneath that cheerfulness, and it is about intermittency. A fair comparison should include the storage or overbuild that makes a variable source dispatchable. Palmer set out a framework for doing this, building on the metric **energy stored on invested** — the electricity a storage device handles over its life, divided by the energy embodied in building it — and computing a system-level ratio in which the denominator carries both the generators and their storage.[^storage]

His central finding is the one worth carrying away, because it is a shape rather than a number: **storage and overbuild show marked diminishing returns as the share of variable generation rises.** The first units of storage buy a great deal of usable energy; each subsequent unit buys less, because it is used less often. That is why "what is the EROI of wind?" has no single answer even in principle — it depends on how much wind is already there, and the honest form of the question is what the whole system returns at a given penetration.

## The threshold argument, and what is wrong with it

The most consequential claim in this literature is that a society needs a minimum EROI to function: that below about 3:1 a fuel cannot even sustain its own supply chain, that around 5:1 is the floor for a functioning economy, and that something like 12–13:1 is needed to support the surplus that pays for education, medicine and the arts.

It is a compelling argument and it should be treated carefully, for two reasons. The thresholds are derived rather than observed — no society has been run at a controlled EROI to see what breaks. And the calculation is exquisitely sensitive to the boundary problem above: a threshold of 12 computed on one boundary and a fuel EROI of 8 computed on another tell you nothing at all.

What survives is the qualitative point, and it is worth having in this book. **Gross energy production is the wrong number.** A country whose oil comes from tar sands at an EROI of 5 must produce far more crude to deliver the same useful energy than one drawing from conventional fields at 30 — and that extra production appears in the statistics as economic activity, as employment, and as emissions.

## Back to the car

MacKay's own figure can now be placed. Treloar's estimate that each unit of petrol requires 1.4 units of oil and other primary fuels is a well-to-tank ratio, not a wellhead EROI, and most of that 1.4 is the crude itself rather than energy spent processing it. Taken at face value it means the 40 kWh per day in chapter 3 understates the primary energy behind the driving by something like a third to a half.

That correction applies to the petrol car and not to its electric replacement in the same way, because an electric car's chain runs through generation rather than refining, and chapter 15 accounts for the battery separately. It is one more reason the factor-of-two advantage computed there is a floor rather than a ceiling.

## Notes and further reading

The values table draws on standard summaries of the literature, and should be treated as indicative. The methodological argument above is better sourced than the numbers are, which is itself a fair description of the state of the field.

[^proi]: The scale distinction, the term PROI, and the wind example are from "When is EROI Not EROI?", *BioPhysical Economics and Resource Quality* 4:16 (2019), <https://doi.org/10.1007/s41247-019-0065-8>, responding to Brockway et al. (2019) on the incomparability of wellhead-oil and renewable-electricity ratios. The paper shows that regional PROI approaches facility EROI only as the industry growth rate approaches zero.
[^storage]: Graham Palmer, "A Framework for Incorporating EROI into Electrical Storage", *BioPhysical Economics and Resource Quality* 2:6 (2017), <https://doi.org/10.1007/s41247-017-0022-3>, modelled on the Texas ERCOT system. The energy-stored-on-invested metric is from Barnhart and Benson (2013).

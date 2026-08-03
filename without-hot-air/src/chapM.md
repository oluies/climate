# M Energy return on investment

*A chapter added in the 2026 revision.* Chapter 3 asks a question and then declines to answer it properly:

> **What about the energy-cost of producing the car's fuel?**
>
> Good point. When I estimate the energy consumed by a particular activity, I tend to choose a fairly tight "boundary" around the activity. […] It's been estimated that making each unit of petrol requires an input of 1.4 units of oil and other primary fuels.

That is the honest admission of a book built on tight boundaries, and it points at a whole literature. This chapter is that literature's technical partner, in the same way that chapter A partners chapter 3 and chapter C partners chapter 5. It turns out that MacKay's instinct — that the boundary is where the difficulty lives — is exactly right, and that the field spent two decades discovering it.

## The measure

**Energy return on investment** is the ratio of energy delivered to energy spent getting it:

$$
\text{EROI} = \frac{\text{energy delivered}}{\text{energy required to deliver it}}
$$

Below 1 the thing is not an energy source at all. The concept was made quantitative by Charles Hall in the 1970s and reached a wide audience through a paper in *Science* in 1984.

The attraction is obvious. A society does not run on the energy it extracts; it runs on what is left after extraction has taken its cut. MacKay's stacks count gross energy at a tight boundary, and EROI is the correction that boundary omits.

The difficulty is that published EROI values for the same fuel differ by more than an order of magnitude, and the differences are mostly not measurement error. They are boundary choices, of two distinct kinds.

## The first boundary: where the fuel gets used

Most quoted EROI figures are measured **at the point of extraction** — at the wellhead or the pithead. But nobody burns crude oil. Between the wellhead and the tank sit transmission, refining and distribution, each of which costs energy, and the fuel that finally does work is a fraction of what came out of the ground.

Murphy, Raugei, Carbajales-Dale and Rubio Estrada argue that this makes almost every published comparison invalid, and they demonstrate why with three hypothetical resources:[^harmon]

| Resource | EROI at point of extraction | EROI at point of use |
|---|---|---|
| PES1 | 100 | **10** |
| PES2 | 50 | **9.1** |
| PES3 | 25 | **7.7** |

A resource that looks four times better than another at the wellhead is 30% better at the tank. Once the downstream stages dominate, the headline number stops carrying information. This is precisely MacKay's query, stated as a general result: the energy cost of producing the fuel is not a small correction, it is most of the answer.

## The second boundary: what scale you measure at

Michael Carbajales-Dale identified a different trap, and it is not widely understood.[^proi] Two quantities are routinely both called EROI. The first is a *facility* ratio: energy a plant delivers over its life, divided by the energy to build and run it. The second is an *industry* ratio: energy delivered by a sector this year, divided by energy invested in that sector this year. He proposes calling the second **power return on investment**, PROI.

His example should be read twice. In 2018 onshore wind had a **facility-scale EROI of over 70**. The same technology as a growing global industry had a **PROI of about 25**. Nothing about the turbines differed. The ratio was depressed purely because the industry was expanding, so much of that year's investment sat in machines not yet generating. The two converge only as growth approaches zero.

The consequence is uncomfortable for anyone quoting these numbers in an argument. **Industry-scale net-energy accounting penalises fast-growing sectors and flatters stagnant ones.**

## Harmonised values

With both corrections applied — everything measured at point of use, on a common method — the ordering is not the one the older literature reports.

| Fuel or source | EROI at point of use |
|---|---|
| Photovoltaics, wind, hydropower | **at or above 10** |
| Hard coal | 8.8 |
| Natural gas | ~5.6 or below |
| Petroleum oil (median) | **4.2** |
| Biodiesel, bioethanol, oil-sands petrol | below 5 |
| Wood pellets | 1.6 |

Two entries deserve a note. Locally-sourced woodchips come out at 32, but only because that particular supply chain is short and simple; wood pellets, which need drying and pressing, manage 1.6, and the authors warn against reading the first as representative of biomass generally. And maize bioethanol reaches at best 1.6 — a fuel that barely returns the energy put into it.

The headline result is that **oil is below ten and renewable electricity is above it.** That reverses the comparison the peak-oil literature was built on, and it does so not by discovering anything new about turbines but by measuring oil where it is actually used rather than where it comes out of the ground.

The authors note the associated rule of thumb, the **net energy cliff**: at an EROI of 10, 90% of the delivered energy is net. Below that the fraction falls away sharply, which is why the region between about 10 and 1 matters so much more than the difference between 50 and 100.

## What storage does to the answer

Renewables are variable, so a fair comparison should include the storage or overbuild that makes them dispatchable. Graham Palmer set out a framework for this, building on **energy stored on invested** — the electricity a storage device handles over its life against the energy embodied in building it — and computing a system ratio whose denominator carries both generators and storage.[^storage]

His finding is a shape rather than a number, and it is the important one: **storage and overbuild show marked diminishing returns as the share of variable generation rises.** The first units of storage buy a great deal of usable energy; later units buy less, because they cycle less often. So "what is the EROI of wind?" has no single answer even in principle. It depends on how much wind is already there.

## Societal EROI, and the threshold argument

The most consequential claim in this literature is that a society needs a minimum EROI to function. Hall and colleagues put the floor for a fuel at about 3:1 in 2009, and the figure has been repeated ever since.

It can now be checked against an estimate of the thing itself. Dupont, Germain and Jeanmart modelled the world economy as two sectors — energy, and everything else — and obtained for 2018 a **gross societal EROI of 9.4 and a net of 8.5**; an earlier estimate put 2015 at 12.[^societal] Set those against the thresholds on offer:

| Threshold proposed | Value | For what |
|---|---|---|
| Hall et al. (2009) | ~3 | bare minimum for a fuel |
| MEDEAS model | ~5 | below this the energy sector's capital needs overwhelm the economy |
| Court and Fizaine (2016) | 11 | to maintain economic growth |
| Observed in high-HDI countries | above 20 | associated with high human development |

The world's estimated societal EROI, 8.5 to 12, sits **above the first two and below the last two**. So "are we near the minimum?" has no answer until one says which minimum, and the candidates span the estimate. Court's own reconstruction has the minimum *sustainable* societal EROI itself falling — from just over 20 in 1900 to around 6 since the 1970s — because an economy that has built its infrastructure needs less surplus to maintain it than one building it. A threshold that moves with the thing it measures is not much of a threshold.

What survives is a number worth more than any of them. **Only 39% of the final energy the world produces reaches consumption and growth at all.** The other **61% is consumed inside the economy that produces it** — 11% by the energy sector, and fully **50% by the rest of the economy** as intermediate and capital consumption. The authors report this split is insensitive to their model's assumptions.

That is the real content of the net-energy argument, and it needs no threshold to be right. When this book adds up 125 kWh per day per person, the share reaching anything a person would recognise as a benefit is a good deal smaller than the total suggests.

## Back to the car

MacKay's figure can now be placed. Treloar's estimate that each unit of petrol requires 1.4 units of oil and other primary fuels is a well-to-tank ratio, and the harmonised work above puts petrol's point-of-use EROI at about **4.2** — meaning roughly a quarter of the energy mobilised to deliver a litre of petrol is spent delivering it.

So the 40 kWh per day in chapter 3 understates the primary energy behind the driving substantially. And the correction does not apply equally to the electric replacement, whose chain runs through generation rather than refining, and whose upstream sources — wind, PV, hydro — sit above 10 on the same harmonised basis where oil sits at 4.2.

Chapter 15 found the electric car about twice as good as the petrol one once manufacturing was counted. On a point-of-use net-energy basis the gap is wider than that, and for a reason MacKay's method could not see: not because the electric car is better engineered, but because the fuel it does not burn is expensive to make.

## Where the numbers come from, and how far they spread

*Added in the 2026 revision.* Everything above turns on a small number of single figures — an EROI of 4.2 for oil, of 8.8 for hard coal, of 10 or more for photovoltaics. Each is the centre of a distribution, and the distributions are wide. The same is true of nearly every techno-economic number quoted anywhere in this book: the levelised costs in chapter 28a, the power densities in chapter 18's table, the capacity factors in chapter 10.

The scale of that spread has recently become measurable. Gorres and colleagues applied automated extraction to **76 000 energy system studies published since 2010**, pulling out **3.2 million structured quantitative data points** and 20 million metadata entries into a searchable database, with the explicit aim of showing where the assumptions used in models diverge from what has been observed.[^extract]

Their framing is the one this chapter has been arguing from: *"Energy system models guide societally important decisions, but their credibility rests on quantitative assumptions that are difficult to source and audit."* That is exactly the problem harmonisation solves for EROI, and it is worth stating that the same problem applies to every other number here.

A reader who wants to know how firm any figure in this edition is should assume it sits in a range, and that the range is often wider than the difference between the technologies being compared. That is not a reason to abandon the arithmetic. It is a reason to prefer conclusions that survive the whole range — which is why this chapter's finding is stated as *renewable electricity is above the thermal fuels at point of use*, and not as a league table of decimal places.

## Notes and further reading

[^harmon]: David J. Murphy, Marco Raugei, Michael Carbajales-Dale and Brenda Rubio Estrada, "Energy Return on Investment of Major Energy Carriers: Review and Harmonization", *Sustainability* 14 (2022) 7098, <https://doi.org/10.3390/su14127098>. Point-of-use harmonisation using Ecoinvent life-cycle inventories; petrol taken as the representative oil product. Median harmonised EROI for oil 4.2, hard coal 8.8, wood pellets 1.6 maximum, locally-sourced woodchips 32; PV, wind and hydropower at or above 10. The maximum point-of-use EROIs by supply-chain stage in their Table 2 give oil 8.7, gas 5.6 and coal 10 before extraction costs are counted, which is why the medians above are lower.
[^proi]: Michael Carbajales-Dale, "When is EROI Not EROI?", *BioPhysical Economics and Resource Quality* 4:16 (2019), <https://doi.org/10.1007/s41247-019-0065-8>, responding to Brockway et al. (2019) on the incomparability of wellhead-oil and renewable-electricity ratios. Regional PROI approaches facility EROI only as the industry growth rate approaches zero.
[^storage]: Graham Palmer, "A Framework for Incorporating EROI into Electrical Storage", *BioPhysical Economics and Resource Quality* 2:6 (2017), <https://doi.org/10.1007/s41247-017-0022-3>, modelled on the Texas ERCOT system. The energy-stored-on-invested metric is from Barnhart and Benson (2013).
[^societal]: Elise Dupont, Marc Germain and Hervé Jeanmart, "Estimate of the Societal Energy Return on Investment (EROI)", *Biophysical Economics and Sustainability* 6:2 (2021), <https://doi.org/10.1007/s41247-021-00084-9>. Gross societal EROI 9.4 and net 8.5 for 2018 worldwide; 39% of final energy reaching consumption and growth, 11% consumed by the energy sector and 50% within the rest of the economy. The threshold figures are as surveyed there: Hall et al. (2009), the MEDEAS model of Capellán-Pérez et al., Court and Fizaine (2016), Court (2019) on the historical decline, and the association of societal EROI above 20 with an HDI above 0.75.

[^extract]: Maxime Gorres, Jan Göpfert, Patrick Kuckertz, Noor Titan Putri Hartono, Heidi Heinrichs, Jochen Linßen, Iain Staffell and Jann Michael Weinand, "Automated Extraction of Techno-Economic Data from 76,000 Energy System Studies", arXiv:2607.19178, July 2026: <https://arxiv.org/abs/2607.19178>. The database is FAIR-compliant and published with an interactive dashboard. Two cautions on using it. Automated extraction from text inherits the errors of the papers it reads and adds its own — a figure pulled from a table without its footnote may lose the boundary conditions that make it meaningful, which is precisely the failure this chapter is about. And a distribution of published values measures what researchers have assumed, not what is true; a widely repeated number can be widely wrong. It is a map of the literature, which is a different thing from a map of the world, and useful for exactly that reason.

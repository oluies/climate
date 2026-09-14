#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure Q.3: what the 1.5 °C pathways build in Europe, against what they
build in the world.

Left, the multiple each carrier is scaled by between 2020 and 2050 — the same
pathways, asked once of the world and once of Europe. Everything low-carbon
sits to the right of the no-change line in both, except European nuclear,
which is the one carrier where the two answers point in opposite directions.
Right, the totals in the book's own units.

Inputs: data-refresh/ar6-europe-mix.csv and data-refresh/ar6-energy-mix.csv,
both from `mill Refresh.scala` (tasks chapterQEurope and chapterQEnergyMix)."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
WORLD, EUROPE = "#8a8a85", "#1f6f9c"
CARRIERS = ["Solar", "Wind", "Biomass", "Hydro", "Nuclear", "Gas", "Oil", "Coal"]

world, europe = {}, {}
for r in csv.DictReader(open(sys.argv[2])):
    if r["category"] == "C1":
        world[r["carrier"]] = r
for r in csv.DictReader(open(sys.argv[1])):
    if r["region"] == "Europe" and r["category"] == "C1":
        europe[r["carrier"]] = r

fig, (ax, bx) = plt.subplots(1, 2, figsize=(10.2, 4.6),
                             gridspec_kw={"width_ratios": [2.25, 1]})

# Left: the multiple, on a log axis because it spans 30-fold growth and
# a 97% cut.
for i, carrier in enumerate(CARRIERS):
    y = len(CARRIERS) - i
    for rows, colour, off, label in ((world, WORLD, 0.17, "World"),
                                     (europe, EUROPE, -0.17, "Europe")):
        r = rows[carrier]
        mult = float(r["ej_2050"]) / float(r["ej_2020"])
        ax.plot([mult], [y + off], "o", color=colour, ms=7, zorder=3,
                label=label if i == 0 else None)
        # Growth reads as a multiple, decline as a percentage; a carrier that
        # barely moves reads as neither, so it gets a decimal.
        text = (f"×{mult:.0f}" if mult >= 3 else
                f"×{mult:.1f}" if mult >= 1 else
                f"−{(1 - mult) * 100:.0f}%")
        ax.annotate(text, (mult, y + off), textcoords="offset points",
                    xytext=(10, -3), fontsize=8.5, color=colour)
ax.axvline(1, color="#c9c9c4", lw=1.0)
ax.annotate("no change", (1, 0.35), fontsize=8.5, color=MUTED, ha="center")
ax.set_xscale("log")
ax.set_xlim(0.012, 130)
ax.set_xticks([0.02, 0.1, 0.5, 1, 5, 25])
ax.set_xticklabels(["÷50", "÷10", "÷2", "1", "×5", "×25"], fontsize=9.5)
ax.set_yticks(range(1, len(CARRIERS) + 1))
ax.set_yticklabels(list(reversed(CARRIERS)), fontsize=10)
ax.set_ylim(0.2, len(CARRIERS) + 0.8)
ax.set_xlabel("Primary energy in 2050 against 2020, median pathway", fontsize=9.5)
ax.legend(frameon=False, fontsize=9.5, loc="lower right", handletextpad=0.3)

# Right: the total, per person per day, which is the book's unit.
for i, (rows, colour, label) in enumerate(((world, WORLD, "World"),
                                           (europe, EUROPE, "Europe"))):
    r = rows["all sources"]
    for j, (year, alpha) in enumerate((("2020", 0.35), ("2050", 1.0))):
        v = float(r[f"kwh_per_day_{year}"])
        bx.bar(i * 2.6 + j, v, width=0.82, color=colour, alpha=alpha, zorder=3)
        bx.annotate(f"{v:.0f}", (i * 2.6 + j, v), textcoords="offset points",
                    xytext=(0, 4), ha="center", fontsize=9.5, color=INK)
    bx.annotate(label, (i * 2.6 + 0.5, -0.13), xycoords=("data", "axes fraction"),
                ha="center", fontsize=10, color=INK)
bx.set_xticks([0, 1, 2.6, 3.6])
bx.set_xticklabels(["2020", "2050", "2020", "2050"], fontsize=9)
bx.set_ylabel("All primary energy, kWh per day per person", fontsize=9.5)
bx.set_ylim(0, 105)

for a in (ax, bx):
    a.grid(axis="x" if a is ax else "y", color=GRID, lw=0.8, zorder=0)
    a.set_axisbelow(True)
    for s in ("top", "right"):
        a.spines[s].set_visible(False)
    for s in ("bottom", "left"):
        a.spines[s].set_color("#c9c9c4")
    a.tick_params(length=0)

fig.suptitle("The same 1.5 °C pathways, asked about Europe",
             x=0.055, ha="left", fontsize=13, fontweight="bold", color=INK)
ax.annotate("Medians across the C1 pathways of the AR6 Scenarios Database: 94 of them for the world, 69 for Europe. Europe here is the database's\n"
            "R10EUROPE — the continent without the former Soviet republics, 547 million people in 2020 — and its population is the pathway's own.\n"
            "World nuclear roughly doubles by 2050; European nuclear falls by more than half, and by 91% on the tighter EU cut of the same file.",
            xy=(0, -0.22), xycoords="axes fraction", va="top",
            fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[3], format="svg", bbox_inches="tight", metadata={"Date": None})
if len(sys.argv) > 4:
    fig.savefig(sys.argv[4], format="png", dpi=150, bbox_inches="tight")
print("wrote", sys.argv[3], "| Europe C1 total",
      f"{float(europe['all sources']['kwh_per_day_2020']):.0f} to "
      f"{float(europe['all sources']['kwh_per_day_2050']):.0f} kWh/d")

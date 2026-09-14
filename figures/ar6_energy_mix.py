#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure Q.2: what the IPCC's pathways actually build, carrier by carrier.

The report prints no number for solar or for nuclear in any of its categories,
because the mix differs between pathways. The database underneath it does have
them, and this is what they say: where each carrier starts in 2020, where the
median pathway puts it in 2050, and how far apart the pathways are about it.

Input: data-refresh/ar6-energy-mix.csv from `mill Refresh.scala
chapterQEnergyMix`."""
import sys, csv, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
START, GROWS, FALLS = "#8a8a85", "#2f7d4f", "#bf4433"
ORDER = ["Solar", "Wind", "Nuclear", "Hydro", "Biomass", "Coal", "Oil", "Gas"]
TITLE = {"C1": "C1 · 1.5 °C, no or limited overshoot", "C3": "C3 · 2 °C, better than two in three"}

rows = collections.defaultdict(dict)
for r in csv.DictReader(open(sys.argv[1])):
    rows[r["category"]][r["carrier"]] = r

fig, axes = plt.subplots(1, 2, figsize=(10.2, 4.8), sharey=True)
for ax, cat in zip(axes, ("C1", "C3")):
    for i, carrier in enumerate(ORDER):
        r = rows[cat].get(carrier)
        if not r:
            continue
        y = len(ORDER) - 1 - i
        a, b = float(r["ej_2020"]), float(r["ej_2050"])
        colour = GROWS if b >= a else FALLS
        ax.plot([float(r["ej_2050_p5"]), float(r["ej_2050_p95"])], [y, y],
                color=colour, lw=5, alpha=0.22, solid_capstyle="butt", zorder=2)
        ax.annotate("", xy=(b, y), xytext=(a, y), zorder=3,
                    arrowprops=dict(arrowstyle="-|>", color=colour, lw=1.4,
                                    shrinkA=3, shrinkB=0))
        ax.plot([a], [y], "o", color=START, ms=5, zorder=4)
        ax.plot([b], [y], "o", color=colour, ms=6, zorder=4)
        factor = b / a if a else 0
        # A carrier that grows by less than double still grows: label the
        # direction, not just the size.
        if factor >= 2:
            label = f"×{factor:.0f}"
        elif factor >= 1:
            label = f"+{100 * (factor - 1):.0f}%"
        else:
            label = f"−{100 * (1 - factor):.0f}%"
        ax.annotate(label, (b, y), textcoords="offset points", xytext=(10, 5),
                    fontsize=9, color=colour)
    ax.set_xscale("log")
    ax.set_xlim(1, 700)
    ax.set_xticks([1, 3, 10, 30, 100, 300])
    ax.get_xaxis().set_major_formatter(matplotlib.ticker.ScalarFormatter())
    ax.get_xaxis().set_minor_formatter(matplotlib.ticker.NullFormatter())
    ax.set_yticks(range(len(ORDER)))
    ax.set_yticklabels(list(reversed(ORDER)), fontsize=10)
    ax.set_ylim(-0.7, len(ORDER) - 0.3)
    ax.set_xlabel("Primary energy, EJ per year (log scale)", fontsize=10)
    ax.set_title(TITLE[cat], loc="left", fontsize=11, color=INK)
    ax.grid(axis="x", which="both", color=GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ("top", "right", "left"):
        ax.spines[s].set_visible(False)
    ax.spines["bottom"].set_color("#c9c9c4")
    ax.tick_params(length=0, labelsize=9.5)

axes[0].plot([], [], "o", color=START, ms=5, label="2020")
axes[0].plot([], [], "o", color=GROWS, ms=6, label="2050, median across pathways")
axes[0].plot([], [], lw=5, alpha=0.22, color=GROWS, label="2050, 5th to 95th percentile")
# Below both panels and centred on the figure. Anchoring to axes[0] put the
# three-column row wider than the left panel, so its last entry sat under the
# right panel as if it belonged to it.
fig.legend(*axes[0].get_legend_handles_labels(), frameon=False, fontsize=9, ncol=3,
           loc="upper center", bbox_to_anchor=(0.5, 0.045), handletextpad=0.5,
           columnspacing=1.8)
fig.suptitle("The pathways agree about coal and disagree about almost everything else",
             x=0.055, ha="left", fontsize=13, fontweight="bold", color=INK)
axes[0].annotate("Medians across the pathways in each category, from the AR6 Scenarios Database. The shaded bar is how far apart those pathways\n"
                 "are in 2050: solar between 31 and 200 EJ in the 1.5 °C category, nuclear between 4 and 70 — a factor of sixteen, which is why the\n"
                 "report publishes no number for either. At the 2050 population, the median 1.5 °C world runs on 43 kWh/d per person of primary\n"
                 "energy; the medians for solar and for nuclear are 8.5 and 1.6, each over its own set of pathways rather than a share of that 43.",
                 xy=(0, -0.34), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
if len(sys.argv) > 3:
    fig.savefig(sys.argv[3], format="png", dpi=150, bbox_inches="tight")
print("wrote", sys.argv[2], "| C1 solar",
      rows["C1"]["Solar"]["ej_2050"], "EJ, nuclear", rows["C1"]["Nuclear"]["ej_2050"], "EJ")

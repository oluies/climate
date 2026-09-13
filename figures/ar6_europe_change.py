#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure Q.4: what the pathways ask of a European, and what Europe would have
to build.

Left, the demand side: final energy per person per day, all of it and by
sector, from 2020 to 2050 in the 1.5 °C and 2 °C categories. The two categories
are three times further apart in energy used than in the plant that supplies
it, which is the section's argument.

Right, the supply side as a rate rather than a stock: the average gigawatts a
year the median 1.5 °C pathway implies, against what Europe actually built in
2025. Solar is already ahead of it; wind is at about two-thirds.

Inputs: data-refresh/ar6-europe-change.csv from `mill Refresh.scala
chapterQEurope`, and data-refresh/europe-build-rates.csv, which is hand-entered
from the two industry associations named in its source column."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
C1, C3, BUILT = "#1f6f9c", "#bf8a33", "#2f7d4f"
ROWS = [("Final Energy", "All final energy"),
        ("Final Energy|Electricity", "…of it, electricity"),
        ("Final Energy|Transportation", "Transport"),
        ("Final Energy|Residential and Commercial", "Buildings"),
        ("Final Energy|Industry", "Industry")]

data = {}
for r in csv.DictReader(open(sys.argv[1])):
    data[(r["category"], r["variable"])] = r
built = {}
for r in csv.DictReader(open(sys.argv[2])):
    built.setdefault(r["technology"], r)     # first row per technology is the widest area

fig, (ax, bx) = plt.subplots(1, 2, figsize=(11.4, 4.6),
                             gridspec_kw={"width_ratios": [1.35, 1.1]})

# Left: 2020 to 2050, per person per day, one arrow per category.
for i, (key, label) in enumerate(ROWS):
    y = len(ROWS) - 1 - i
    for cat, colour, off in (("C1", C1, 0.16), ("C3", C3, -0.16)):
        r = data[(cat, key)]
        a, b = float(r["per_person_2020"]), float(r["per_person_2050"])
        ax.annotate("", xy=(b, y + off), xytext=(a, y + off), zorder=3,
                    arrowprops=dict(arrowstyle="-|>", color=colour, lw=1.5,
                                    shrinkA=2, shrinkB=0))
        ax.plot([a], [y + off], "o", color="#c9c9c4", ms=4.5, zorder=4)
        ax.plot([b], [y + off], "o", color=colour, ms=6, zorder=4)
        ax.annotate(f"{b:.0f}", (b, y + off), textcoords="offset points",
                    xytext=(9 if b > a else -14, -3.5), fontsize=9, color=colour)
ax.plot([], [], "o", color=C1, ms=6, label="2050, C1 · 1.5 °C")
ax.plot([], [], "o", color=C3, ms=6, label="2050, C3 · 2 °C")
ax.plot([], [], "o", color="#c9c9c4", ms=4.5, label="2020")
ax.legend(frameon=False, fontsize=9, loc="upper left", ncol=3,
          bbox_to_anchor=(0, -0.15), handletextpad=0.3, columnspacing=1.4)
ax.set_yticks(range(len(ROWS)))
ax.set_yticklabels([l for _, l in reversed(ROWS)], fontsize=10)
ax.set_ylim(-0.6, len(ROWS) - 0.4)
ax.set_xlim(0, 82)
ax.set_xlabel("kWh per day per person", fontsize=9.5)
ax.set_title("What it asks of a European", loc="left", fontsize=11, color=INK)
ax.grid(axis="x", color=GRID, lw=0.8)

# Right: the rate, not the stock. Two legs of the 1.5 °C pathway against the
# one year Europe has actually just had.
BARS = []
for tech in ("Wind", "Solar"):
    cap = {y: float(data[("C1", f"Capacity|Electricity|{tech}")][f"v_{y}"])
           for y in ("2020", "2030", "2050")}
    BARS.append((tech, (cap["2030"] - cap["2020"]) / 10, (cap["2050"] - cap["2030"]) / 20,
                 float(built[tech]["installed_gw"])))
for i, (tech, leg1, leg2, actual) in enumerate(BARS):
    base = i * 3.4
    for j, (v, colour, alpha) in enumerate(((leg1, C1, 0.45), (leg2, C1, 0.85), (actual, BUILT, 1.0))):
        bx.bar(base + j, v, width=0.82, color=colour, alpha=alpha, zorder=3)
        bx.annotate(f"{v:.0f}", (base + j, v), textcoords="offset points",
                    xytext=(0, 4), ha="center", fontsize=9.5, color=INK)
    bx.annotate(tech, (base + 1, -0.16), xycoords=("data", "axes fraction"),
                ha="center", fontsize=10.5, color=INK)
bx.set_xticks([0, 1, 2, 3.4, 4.4, 5.4])
bx.set_xticklabels(["2020s", "30s–40s", "2025"] * 2, fontsize=9)
bx.set_ylabel("Gigawatts a year", fontsize=9.5)
bx.set_ylim(0, 78)
bx.set_title("What it asks Europe to build", loc="left", fontsize=11, color=INK)
bx.plot([], [], "s", color=C1, alpha=0.65, ms=8, label="needed, 1.5 °C median")
bx.plot([], [], "s", color=BUILT, ms=8, label="actually built")
bx.legend(frameon=False, fontsize=9, loc="upper left", ncol=2,
          bbox_to_anchor=(0, -0.20), handletextpad=0.3, columnspacing=1.4)
bx.grid(axis="y", color=GRID, lw=0.8)

for a in (ax, bx):
    a.set_axisbelow(True)
    for s in ("top", "right"):
        a.spines[s].set_visible(False)
    for s in ("bottom", "left"):
        a.spines[s].set_color("#c9c9c4")
    a.tick_params(length=0, labelsize=9.5)

fig.suptitle("In Europe the two categories differ more in demand than in machinery",
             x=0.055, ha="left", fontsize=13, fontweight="bold", color=INK)
ax.annotate("Medians across the AR6 pathways for R10EUROPE, each divided by the population the pathway itself carries. The rows are separate medians, so the\n"
            "sectors do not sum to the total. Final energy falls 27% by 2050 in the 1.5 °C category and 9% in the 2 °C one, while the wind and solar capacity the two\n"
            "build differs by only 18% and 28% — and European buildings in the 2 °C pathways use almost as much in 2050 as they do now. Right: the average rate\n"
            "the 1.5 °C median implies, against 19.1 GW of wind built in Europe and 65.1 GW of solar built in the EU during 2025, as reported by WindEurope and\n"
            "SolarPower Europe. Both real figures cover areas slightly different from R10EUROPE, and neither counts the plant that has to be replaced as it ages.",
            xy=(0, -0.30), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[3], format="svg", bbox_inches="tight")
if len(sys.argv) > 4:
    fig.savefig(sys.argv[4], format="png", dpi=150, bbox_inches="tight")
print("wrote", sys.argv[3], "|", ", ".join(
    f"{t}: {a:.0f}/{b:.0f} GW a year needed, {c:.0f} built" for t, a, b, c in BARS))

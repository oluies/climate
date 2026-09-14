#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure Q.1: the IPCC's two headline scenario categories, per person.

The report gives global totals in gigatonnes of CO₂-equivalent a year. Divided
by the people alive in each year, the same pathways become a quantity this book
can put beside its own stacks — and the right-hand axis converts that to the
fuel it would be, at the 250 g of CO₂ per kWh the carbon chart uses.

Input: data-refresh/ar6-pathways.csv from `mill Refresh.scala chapterQPathways`."""
import sys, csv, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
COLOUR = {"C1": "#1f6f9c", "C3": "#bf4433"}
NAME = {"C1": "C1 · 1.5 °C with no or limited overshoot",
        "C3": "C3 · 2 °C with better than two chances in three"}

rows = list(csv.DictReader(open(sys.argv[1])))
hist = next(r for r in rows if r["category"] == "history")
paths = collections.defaultdict(list)
for r in rows:
    if r["category"] != "history":
        paths[r["category"]].append((int(r["year"]), float(r["t_per_person"]),
                                     float(r["t_p5"]), float(r["t_p95"])))

fig, ax = plt.subplots(figsize=(8.6, 5.0))
start = (int(hist["year"]), float(hist["t_per_person"]))
for cat, pts in paths.items():
    pts.sort()
    xs = [start[0]] + [p[0] for p in pts]
    ys = [start[1]] + [p[1] for p in pts]
    ax.plot(xs, ys, color=COLOUR[cat], lw=2.1, marker="o", ms=4.5, label=NAME[cat], zorder=3)
    ax.fill_between([start[0]] + [p[0] for p in pts],
                    [start[1]] + [p[2] for p in pts],
                    [start[1]] + [p[3] for p in pts],
                    color=COLOUR[cat], alpha=0.12, lw=0, zorder=2)
    last = pts[-1]
    ax.annotate(f"{last[1]:.2f} t", (last[0], last[1]), textcoords="offset points",
                xytext=(8, -2), fontsize=9.5, color=COLOUR[cat])
ax.plot([start[0]], [start[1]], "o", color=INK, ms=5, zorder=4)
ax.annotate(f"2019: {start[1]:.1f} t per person", start, textcoords="offset points",
            xytext=(6, 8), fontsize=9.5, color=INK)

ax.set_xlim(2017, 2056)
ax.set_ylim(0, 8.2)
ax.set_xticks([2019, 2030, 2040, 2050])
ax.set_xlabel("Year", fontsize=10.5)
ax.set_ylabel("Greenhouse gas, tonnes of CO₂-equivalent per person per year", fontsize=10.5)
ax.grid(color=GRID, lw=0.8)
ax.set_axisbelow(True)
for s in ("top",):
    ax.spines[s].set_visible(False)
for s in ("bottom", "left", "right"):
    ax.spines[s].set_color("#c9c9c4")
ax.tick_params(length=0, labelsize=9.5)
ax.legend(frameon=False, fontsize=9.5, loc="upper right")

# The same axis in the book's units: what that tonnage would be as fuel.
right = ax.twinx()
right.set_ylim(0, 8.2 * 1e6 / 250 / 365)
right.set_ylabel("as fuel at 250 g CO₂ per kWh, kWh per day", fontsize=10.5)
right.tick_params(length=0, labelsize=9.5)
right.spines["top"].set_visible(False)
for s in ("bottom", "left", "right"):
    right.spines[s].set_color("#c9c9c4")

ax.set_title("The IPCC's pathways, divided by the people who have to live on them",
             loc="left", fontsize=13, fontweight="bold", pad=12, color=INK)
ax.annotate("Medians and 5th-to-95th-percentile ranges from Table SPM.2 of the Sixth Assessment Report's working group III,\n"
            "divided by the United Nations' medium population projection. Because the population grows, every per-person cut is\n"
            "deeper than the global one the report prints: C1's 84% by 2050 is 87% each. The right-hand axis is what the tonnage\n"
            "would be if every remaining tonne were burnt fuel, which it is not — farming and industry are in these totals too.",
            xy=(0, -0.17), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2], "| 2050:",
      ", ".join(f"{c} {p[-1][1]:.2f} t" for c, p in paths.items()))

#!/usr/bin/env python3
"""Death rates by generation technology, remaking MacKay's figure 24.11.
Input: data-refresh/death-rates.csv from `mill Refresh.scala deathRates`."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

INK, MUTED = "#161d1b", "#8a8a85"
FOSSIL, LOWC = "#3f3f46", "#1baf7a"
d = duckdb.sql(f"""
    SELECT source, deaths_twh, deaths_gwy, basis
    FROM read_csv_auto('{sys.argv[1]}') ORDER BY deaths_twh
""").fetchnumpy()
n = len(d["source"])

fig, ax = plt.subplots(figsize=(9.2, 5.0))
y = np.arange(n)
ax.set_xscale("log")
for i in range(n):
    v = float(d["deaths_twh"][i])
    col = FOSSIL if "air pollution" in d["basis"][i] else LOWC
    ax.barh(y[i], v, height=0.62, color=col, alpha=0.85, zorder=3)
    # Both units: the book's own unit is deaths per GW-year, 8.76 times larger.
    ax.text(v * 1.15, y[i], f"{v:g}   ({float(d['deaths_gwy'][i]):.2f} per GW-year)",
            va="center", fontsize=8.8, color=INK)

ax.set_yticks(y); ax.set_yticklabels(d["source"], fontsize=10)
ax.set_xlim(0.01, 900)
ax.set_xticks([0.01, 0.1, 1, 10, 100])
ax.set_xticklabels(["0.01", "0.1", "1", "10", "100"])
ax.set_xlabel("deaths per TWh of electricity (logarithmic)", fontsize=10.5)
ax.grid(axis="x", color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.spines["bottom"].set_color("#c9c9c4")
ax.tick_params(axis="y", length=0)

h = [plt.Rectangle((0, 0), 1, 1, color=FOSSIL, alpha=0.85),
     plt.Rectangle((0, 0), 1, 1, color=LOWC, alpha=0.85)]
# Below the axis: the plot area carries the value labels.
ax.legend(h, ["Accidents and air pollution", "Accidents only"],
          frameon=False, fontsize=9, ncol=2, loc="upper center",
          bbox_to_anchor=(0.5, -0.13))
ax.set_title("Deaths per unit of electricity generated",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("The axis is logarithmic because a linear one makes every low-carbon source invisible: coal is about eight\n"
            "hundred times nuclear. The comparison's weakness is on the chart — the fossil and biomass rows count air\n"
            "pollution as well as accidents, and the low-carbon rows count accidents only. Correcting that would widen\n"
            "the gap, not narrow it, since the low-carbon sources emit almost no pollution to attribute.",
            xy=(0, -0.215), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

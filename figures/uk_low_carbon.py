#!/usr/bin/env python3
"""Figure 18.9 remade: British renewables and nuclear per person, 2006 and 2025.
Input: data-refresh/uk-low-carbon.csv from `mill Refresh.scala ukLowCarbon`."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

INK, MUTED = "#161d1b", "#8a8a85"
COL = {"Wind": "#1baf7a", "Solar": "#eda100", "Hydro": "#2a78d6",
       "Biomass": "#7a5c3e", "Nuclear": "#4a3aa7"}
d = duckdb.sql(f"""
    SELECT source, y2006, y2025 FROM read_csv_auto('{sys.argv[1]}')
""").fetchnumpy()
names = list(d["source"])
a = np.array([float(v) for v in d["y2006"]])
b = np.array([float(v) for v in d["y2025"]])

fig, ax = plt.subplots(figsize=(8.4, 5.6))
for i, (x, vals) in enumerate(((0, a), (1, b))):
    bottom = 0.0
    for n, v in zip(names, vals):
        ax.bar(x, v, bottom=bottom, width=0.42, color=COL[n], zorder=3,
               edgecolor="white", linewidth=1.1)
        # Label each band that is thick enough to carry text.
        if v > 0.35:
            ax.text(x, bottom + v / 2, f"{n}  {v:.2f}", ha="center", va="center",
                    fontsize=9, color="white", fontweight="bold", zorder=4)
        bottom += v
    ax.text(x, bottom + 0.16, f"{bottom:.2f}", ha="center", fontsize=11,
            fontweight="bold", color=INK, zorder=4)

# Solar in 2006 was nil and wind was too thin to label in place.
ax.annotate("wind 0.19, hydro 0.21,\nsolar nil", xy=(0, a[0] / 2), xytext=(-0.32, 1.5),
            fontsize=8.8, color=MUTED, ha="center",
            arrowprops=dict(arrowstyle="-", color="#c9c9c4", lw=0.8))

ax.set_xticks([0, 1])
ax.set_xticklabels(["2006\nMacKay's figure 18.9", "2025\nthis edition"], fontsize=10)
ax.set_ylabel("kWh per day per person", fontsize=10.5)
ax.set_ylim(0, 9)
ax.set_xlim(-0.6, 1.6)
ax.grid(axis="y", color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right"): ax.spines[s].set_visible(False)
for s in ("bottom", "left"): ax.spines[s].set_color("#c9c9c4")
ax.tick_params(axis="x", length=0)
ax.set_title("British low-carbon electricity per person, 2006 and 2025",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("MacKay had to scale the renewable part of this figure a hundredfold to make it visible at all. It no longer needs\n"
            "scaling: renewables went from 0.78 to 6.12 kWh per day per person, almost all of it wind and solar. Nuclear more\n"
            "than halved over the same period as the old fleet retired, so the low-carbon total rose by less than the renewable\n"
            "part alone — from 4.18 to 7.55. Hydro, the thin blue band, is unchanged at 0.2 in both columns: it was already\nbuilt. Set the whole against the 136 kWh/d this chapter finds conceivable.",
            xy=(0, -0.155), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

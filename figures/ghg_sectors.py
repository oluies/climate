#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 1.8: world greenhouse-gas emissions by sector, MacKay's figure 1.9
brought from 2000 to the present. Input: data-refresh/ghg-by-sector.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
NEW, OLD = "#4a3aa7", "#c3c3bd"
d = pd.read_csv(sys.argv[1])
sectors = [c for c in d.columns if c not in ("Entity", "Code", "Year")]
y0, y1 = 2000, int(d.Year.max())
a = d[d.Year == y0][sectors].iloc[0] / 1e9
b = d[d.Year == y1][sectors].iloc[0] / 1e9
sa, sb = a.sum(), b.sum()
order = b.sort_values(ascending=True).index

sns.set_theme(style="whitegrid", rc={"grid.color": GRID, "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.6))
ys = range(len(order))
ax.barh([y + 0.20 for y in ys], [100 * b[s] / sb for s in order], height=0.36,
        color=NEW, label=f"{y1}", zorder=3)
ax.barh([y - 0.20 for y in ys], [100 * a[s] / sa for s in order], height=0.36,
        color=OLD, label=f"{y0}", zorder=3)
for i, s in enumerate(order):
    ax.text(100 * b[s] / sb + 0.5, i + 0.20, f"{100*b[s]/sb:.0f}%", va="center",
            fontsize=9.5, color=INK, fontweight="bold")
ax.set_yticks(list(ys)); ax.set_yticklabels(order, fontsize=10)
ax.set_xlim(0, 38); ax.set_xticks([])
ax.legend(frameon=False, fontsize=9.5, loc="lower right")
for sp in ("top", "right", "bottom"):
    ax.spines[sp].set_visible(False)
ax.set_xlabel("share of world greenhouse-gas emissions", fontsize=10.5,
              color=MUTED, labelpad=10, loc="left")
ax.set_title(f"Figure 1.8. Where the emissions come from, {y0} and {y1}.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
ax.annotate(f"The total rose from {sa:.0f} to {sb:.0f} gigatonnes of CO$_2$-equivalent. Electricity and heat\n"
            f"grew fastest and is now a third of everything.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("All greenhouse gases in CO$_2$-equivalent over 100 years. Source: Climate Watch via Our World in Data. "
            "The land-use row is the most uncertain and has been revised down sharply.",
            xy=(0, -0.12), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

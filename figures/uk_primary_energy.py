#!/usr/bin/env python3
"""UK primary energy history, stacked area (seaborn theme + matplotlib stackplot).
Data from Refresh.scala chapterK -> uk-primary-energy.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt
df = pd.read_csv(sys.argv[1])
order = ["Coal", "Oil", "Gas", "Nuclear", "Renewables"]
COL = {"Coal": "#3f3f46", "Oil": "#2a78d6", "Gas": "#eb6834", "Nuclear": "#4a3aa7", "Renewables": "#1baf7a"}
piv = df.pivot(index="year", columns="category", values="twh").fillna(0)[order]
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.6, 4.6))
ax.stackplot(piv.index, [piv[c] for c in order], labels=order,
             colors=[COL[c] for c in order], edgecolor="white", linewidth=0.7)
ax.set_xlim(int(piv.index.min()), int(piv.index.max()))
ax.set_ylim(0, None); ax.margins(x=0)
ax.set_ylabel("TWh per year"); ax.set_xlabel("")
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
h, l = ax.get_legend_handles_labels()
ax.legend(h[::-1], l[::-1], loc="upper left", frameon=False, fontsize=9.5, handlelength=1.1)
ax.set_title(f"UK primary energy consumption by source, {int(piv.index.min())}–{int(piv.index.max())}",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

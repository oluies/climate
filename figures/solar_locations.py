#!/usr/bin/env python3
"""Figure 6.16 redone: annual mean sunshine on the horizontal, by location.
Input: data-refresh/solar-locations.csv from `mill Refresh.scala chapter06Figs`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"Europe": "#2a78d6", "N. America": "#7a5c3e", "Africa": "#eda100"}
MUTED, RULE = "#8a8a85", "#4a3aa7"
df = pd.read_csv(sys.argv[1]).sort_values("wm2")

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 6.4))
bars = ax.barh(df.place, df.wm2, color=[COL[k] for k in df.region], height=0.68)
for b, v in zip(bars, df.wm2):
    ax.text(v + 2.5, b.get_y() + b.get_height() / 2, f"{v:.0f}",
            va="center", fontsize=9, color="#161d1b")

ax.axvline(100, color=RULE, lw=1.3, ls=(0, (5, 3)), zorder=0)
ax.annotate("MacKay's 100 W/m²", xy=(100, -0.9), xytext=(4, 0),
            textcoords="offset points", fontsize=9, color=RULE)

ax.set_xlabel("annual mean sunshine on a horizontal surface (W/m$^2$)", fontsize=10.5)
ax.set_xlim(0, 290)
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0, labelsize=9.5)
handles = [plt.Rectangle((0, 0), 1, 1, color=c) for c in COL.values()]
ax.legend(handles, COL.keys(), frameon=False, fontsize=9, loc="lower right")
ax.set_title("Sunshine falling on a horizontal surface, selected locations",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("The desert advantage is real but bounded: Ouarzazate has roughly twice\n"
            "Edinburgh's sunlight, not ten times. Chapter 25 turns on that ratio.",
            xy=(0.98, 0.055), xycoords="axes fraction", ha="right", va="bottom",
            fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

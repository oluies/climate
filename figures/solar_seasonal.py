#!/usr/bin/env python3
"""Figure 6.2 redone: mean solar intensity on the horizontal through the year.
Input: data-refresh/solar-seasonal.csv from `mill Refresh.scala chapter06Figs`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"London": "#eb6834", "Edinburgh": "#2a78d6"}
MUTED, RULE = "#8a8a85", "#4a3aa7"
M = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
df = pd.read_csv(sys.argv[1])

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.6))
for p, d in df.groupby("place"):
    d = d.sort_values("month")
    ax.plot(d.month, d.wm2, color=COL[p], lw=2.1, marker="o", ms=4)
    ax.text(12.15, float(d[d.month == 12].wm2.iloc[0]), p, color=COL[p],
            fontsize=9.5, va="center")

mean_all = df.wm2.mean()
ax.axhline(100, color=RULE, lw=1.3, ls=(0, (5, 3)))
ax.annotate("MacKay's 100 W/m² annual average for flat ground",
            xy=(1.1, 100), xytext=(0, 6), textcoords="offset points",
            fontsize=9.5, color=RULE)

ax.set_ylabel("mean intensity on the horizontal (W/m$^2$)", fontsize=10.5)
ax.set_xlabel("")
ax.set_xticks(range(1, 13)); ax.set_xticklabels(M)
ax.set_xlim(0.7, 13.6); ax.set_ylim(0, 260)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title("Solar intensity through the year, London and Edinburgh",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("The winter trough is the problem this book keeps returning to: December\n"
            "delivers roughly a tenth of June, and no amount of panel buys it back.",
            xy=(0.03, 0.95), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

#!/usr/bin/env python3
"""Figure 6.13 redone: sunshine hours as a fraction of daylight hours.
Input: data-refresh/sunniness.csv from `mill Refresh.scala chapter06Figs`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"Cambridge": "#eb6834", "Oxford": "#2a78d6"}
MUTED = "#8a8a85"
df = pd.read_csv(sys.argv[1])

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 4.8))
for s, d in df.groupby("station"):
    d = d.sort_values("year")
    ax.plot(d.year, d.fraction * 100, color=COL[s], lw=1.0, alpha=0.45)
    ax.plot(d.year, d.fraction.rolling(10, center=True).mean() * 100,
            color=COL[s], lw=2.4, label=f"{s} (10-year mean)")
    last = d.iloc[-1]
    ax.text(last.year + 1, last.fraction * 100, s, color=COL[s], fontsize=9.5, va="center")

ax.set_ylabel("sunshine as a share of daylight hours", fontsize=10.5)
ax.set_xlabel("")
ax.set_ylim(20, 50)
ax.yaxis.set_major_formatter(lambda v, _: f"{v:.0f}%")
ax.set_xlim(int(df.year.min()), int(df.year.max()) + 11)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.legend(frameon=False, fontsize=9, loc="lower left")
ax.set_title("Sunniness: hours of sunshine against hours of daylight",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("MacKay quoted 34% for Cambridge, and the Cambridge record agrees. That station\n"
            "stopped reporting sunshine in 2010; Oxford's near-century series runs higher and\n"
            "has been rising, reaching 43% in 2025.",
            xy=(0.03, 0.955), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

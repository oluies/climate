#!/usr/bin/env python3
"""Figure 6.5 recalculated: MacKay's 25 m2 Cambridgeshire array, then and now.
Input: data-refresh/pv-array-cambridge.csv from `mill Refresh.scala chapter06Figs`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt, numpy as np

OLD, NEW, MUTED = "#8a8a85", "#eda100", "#8a8a85"
M = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
df = pd.read_csv(sys.argv[1])
labs = list(dict.fromkeys(df.scenario))
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.6, 4.8))
x = np.arange(1, 13); w = 0.38
for i, (lab, col) in enumerate(zip(labs, (OLD, NEW))):
    d = df[df.scenario == lab].sort_values("month")
    ax.bar(x + (i - 0.5) * w, d.kwh_per_day, width=w, color=col, label=lab)
    print(f"{lab}: annual mean {d.kwh_per_day.mean():.1f} kWh/d")

ax.set_ylim(0, 26)
ax.set_ylabel("kWh per day", fontsize=10.5)
ax.set_xticks(x); ax.set_xticklabels(M)
ax.set_xlim(0.4, 12.6)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.legend(frameon=False, fontsize=9.5, loc="upper left", ncol=2,
          bbox_to_anchor=(0, 1.02))
ax.set_title("MacKay's 25 m² Cambridgeshire array, then and now",
             loc="left", fontsize=12.5, fontweight="bold", pad=30)
# Note sits below the axis: over the bars it collided with both the legend and the peak months.
ax.annotate("Modelled from PVGIS. The 2006 array averaged 12 kWh/d measured and 11.5 modelled; the same roof\n"
            "with 2026 modules gives 15.9 — 26 W/m² of panel against 20. December still delivers under a\n"
            "third of June, whatever the panel.",
            xy=(0, -0.20), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

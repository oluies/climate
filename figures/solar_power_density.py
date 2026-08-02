#!/usr/bin/env python3
"""Power per unit area of four solar installations, against MacKay's assumption.
Input: data-refresh/solar-power-density.csv from `mill Refresh.scala chapter06`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"PV": "#eda100", "Solar thermal": "#eb6834", "Assumption": "#4a3aa7"}
MUTED = "#8a8a85"
df = pd.read_csv(sys.argv[1]).sort_values("wm2")

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 3.8))
bars = ax.barh(df.label, df.wm2, color=[COL[k] for k in df.kind], height=0.62)
for b, v in zip(bars, df.wm2):
    ax.text(v + 0.18, b.get_y() + b.get_height() / 2, f"{v:.1f}",
            va="center", fontsize=10, color="#161d1b")

ax.set_xlabel("average power per unit area (W/m$^2$)", fontsize=10.5)
ax.set_xlim(0, 13.2)
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0)
handles = [plt.Rectangle((0, 0), 1, 1, color=c) for c in COL.values()]
ax.legend(handles, COL.keys(), frameon=False, fontsize=9, loc="lower right")
ax.set_title("Power per unit area: what solar farms actually achieve",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("A photovoltaic field in Kent beats a concentrating plant in the Mojave.",
            xy=(0, -0.30), xycoords="axes fraction", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

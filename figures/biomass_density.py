#!/usr/bin/env python3
"""Figure 6.11 rebuilt from the sources in this chapter's own endnotes.
Input: data-refresh/biomass-density.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

BASE, ASSUME, ERR, MUTED = "#1baf7a", "#4a3aa7", "#e34948", "#8a8a85"
df = pd.read_csv(sys.argv[1]).fillna("").sort_values("wm2")
col = [ASSUME if n == "assumption" else ERR if n == "erratum" else BASE for n in df.note]

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.6))
bars = ax.barh(df.crop, df.wm2, color=col, height=0.66)
for b, v in zip(bars, df.wm2):
    ax.text(v + 0.012, b.get_y() + b.get_height() / 2, f"{v:.3g}",
            va="center", fontsize=9, color="#161d1b")
ax.set_xlabel("power per unit area (W/m$^2$)", fontsize=10.5)
ax.set_xlim(0, 0.95)
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0, labelsize=9.5)
ax.set_title("Power per unit area from growing things",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("Everything here is below 1 W/m², against 10 for a solar farm on the same land\n"
            "(figure 6.21). The corn-to-ethanol figure is the corrected one from the erratum,\n"
            "0.048 rather than the 0.02 printed in 2008.",
            xy=(0.98, 0.20), xycoords="axes fraction", ha="right", va="top",
            fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

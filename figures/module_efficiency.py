#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 6.18 redone: module efficiencies now, against MacKay's two assumptions.
Input: data-refresh/module-efficiency.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"2008": "#8a8a85", "2026": "#eda100", "record": "#4a3aa7"}
LAB = {"2008": "MacKay's 2008 assumptions", "2026": "On sale in 2026",
       "record": "Certified prototype, not mass-produced"}
MUTED = "#8a8a85"
df = pd.read_csv(sys.argv[1]).sort_values("pct")

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.6, 4.4))
bars = ax.barh(df.technology, df.pct, color=[COL[k] for k in df.kind], height=0.66)
for b, v in zip(bars, df.pct):
    ax.text(v + 0.3, b.get_y() + b.get_height() / 2, f"{v:g}%",
            va="center", fontsize=9, color="#161d1b")
ax.axvline(31, color="#c9c9c4", lw=1.2, ls=(0, (5, 3)), zorder=0)
ax.annotate("Shockley–Queisser limit\nfor a single junction",
            xy=(31, 0.1), xytext=(-6, 0), textcoords="offset points",
            ha="right", va="bottom", fontsize=8.5, color=MUTED)
ax.set_xlabel("module efficiency", fontsize=10.5)
ax.set_xlim(0, 38)
ax.xaxis.set_major_formatter(lambda v, _: f"{v:.0f}%")
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0, labelsize=9.5)
handles = [plt.Rectangle((0, 0), 1, 1, color=COL[k]) for k in ("2008", "2026", "record")]
ax.legend(handles, [LAB[k] for k in ("2008", "2026", "record")],
          frameon=False, fontsize=9, loc="lower right")
ax.set_title("Solar module efficiency: MacKay's assumptions and today's product",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

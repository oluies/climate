#!/usr/bin/env python3
"""World energy supply in 2025 and where that year's growth came from (seaborn).
Two panels rather than one dual-axis chart: the stock is in hundreds of EJ, the
flow in single EJ, and putting them on one scale would hide the flow entirely.
Input: data-refresh/world-energy-2025.csv from `mill Refresh.scala chapterJ`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"Oil": "#7a5c3e", "Coal": "#3f3f46", "Gas": "#eb6834",
       "Renewables": "#1baf7a", "Nuclear": "#4a3aa7", "Hydro": "#2a78d6"}
INK, MUTED = "#52514e", "#8a8a85"
df = pd.read_csv(sys.argv[1])
tes = df.ej_2025.sum()
fossil = df[df.source.isin(["Oil", "Coal", "Gas"])].ej_2025.sum()

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(9.6, 4.0))

for ax, col, ttl, fmt in (
        (ax1, "ej_2025", f"Supply in 2025 — {tes:.0f} EJ in total", "%.0f"),
        (ax2, "growth_ej", "Of which added during 2025", "%.1f")):
    d = df.sort_values(col, ascending=False)
    ax.barh(d.source, d[col], color=[COL[s] for s in d.source], height=0.68)
    ax.invert_yaxis()
    ax.bar_label(ax.containers[0], fmt=fmt, padding=4, fontsize=10, color=INK)
    ax.set_xlabel("EJ per year", fontsize=10, color=INK)
    ax.set_title(ttl, loc="left", fontsize=11.5, fontweight="bold", pad=10)
    ax.grid(axis="y", visible=False)
    ax.set_xlim(0, d[col].max() * 1.20)
    for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
    ax.tick_params(axis="y", length=0)

# The two panels answer different questions, so say what each one shows.
ax1.annotate(f"oil, coal and gas together:\n{fossil:.0f} EJ, {fossil/tes*100:.0f}% of all energy",
             xy=(0.97, 0.06), xycoords="axes fraction", ha="right", fontsize=9.5, color=MUTED)
ax2.annotate("renewables added more than\nany single fossil fuel — a first\nfor this century",
             xy=(0.97, 0.06), xycoords="axes fraction", ha="right", fontsize=9.5, color=MUTED)

fig.suptitle("World energy in 2025: a large fossil stock, and a growing one",
             x=0.008, ha="left", fontsize=13, fontweight="bold")
fig.tight_layout(rect=(0, 0, 1, 0.94))
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

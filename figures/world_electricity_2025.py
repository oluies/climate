#!/usr/bin/env python3
"""World electricity generation by source in 2025 (seaborn).
Electricity is the part of the energy system where the transition is visible, so
it gets its own figure: solar passed wind here, and is closing on nuclear.
Input: data-refresh/world-electricity-2025.csv from `mill Refresh.scala chapterJ`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"Fossil": "#3f3f46", "Hydro": "#2a78d6", "Nuclear": "#4a3aa7",
       "Solar": "#eda100", "Wind": "#1baf7a", "Other renewables": "#e34948",
       "Other": "#8a8a85"}
INK, MUTED = "#52514e", "#8a8a85"
df = pd.read_csv(sys.argv[1]).sort_values("twh_2025", ascending=False)
df["change"] = df.twh_2025 - df.twh_2024
total = df.twh_2025.sum()

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.0))
ax.barh(df.source, df.twh_2025, color=[COL[s] for s in df.source], height=0.68)
ax.invert_yaxis()
ax.bar_label(ax.containers[0],
             labels=[f"{v:,.0f}  ({v/total*100:.1f}%)".replace(",", " ") for v in df.twh_2025],
             padding=5, fontsize=10, color=INK)
ax.set_xlabel("TWh generated in 2025", fontsize=10, color=INK)
ax.set_xlim(0, df.twh_2025.max() * 1.28)
ax.grid(axis="y", visible=False)
ax.tick_params(axis="y", length=0)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
tot = f"{total:,.0f}".replace(",", " ")   # thin-space thousands in the number, not the prose
ax.set_title(f"World electricity generation by source, 2025 — {tot} TWh",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
foss = df[df.source == "Fossil"].iloc[0]
ax.annotate(f"fossil generation fell {abs(foss.change):.0f} TWh in 2025\nwhile total generation rose — all of the\ngrowth came from low-carbon sources",
            xy=(0.975, 0.13), xycoords="axes fraction", ha="right", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

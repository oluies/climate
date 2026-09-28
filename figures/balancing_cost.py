#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""German balancing-reserve procurement cost against the nuclear phase-out.
Data from Refresh.scala balancingCost -> balancing-cost.csv; this only draws it.
Usage: python balancing_cost.py <csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

df = pd.read_csv(sys.argv[1]).sort_values("year")
LINE, MARK, INK = "#1f6fd1", "#e34948", "#52514e"

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(7.4, 3.4))

shut = df[df["reactors"] > 0]
for _, r in shut.iterrows():
    ax.axvline(r["year"], color=MARK, lw=1.0, ls=(0, (2, 3)), zorder=0)
    ax.annotate(f"{int(r['reactors'])}", xy=(r["year"], 1.02),
                xycoords=ax.get_xaxis_transform(), ha="center", va="bottom",
                fontsize=9, color=MARK, fontweight="bold")

ax.plot(df["year"], df["cost_meur"], color=LINE, lw=2.0, zorder=3)
ax.scatter(df["year"], df["cost_meur"], color=LINE, s=22, zorder=4)

for yr, note, dx, dy in [(2009, "825", 0, 34), (2018, "123", 0, -30), (2023, "644", 0, 26)]:
    v = float(df.loc[df["year"] == yr, "cost_meur"].iloc[0])
    ax.annotate(note, xy=(yr, v), xytext=(dx, dy), textcoords="offset points",
                ha="center", fontsize=10, color=INK)

ax.set_xlabel(""); ax.set_ylabel("EUR million a year")
ax.set_ylim(0, 950); ax.set_xlim(2008.3, 2024.7)
ax.set_xticks(range(2009, 2025, 3))
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.text(0.0, 1.14, "German balancing-reserve procurement, 2009 to 2024",
        transform=ax.transAxes, ha="left", fontsize=12.5, fontweight="bold")
ax.text(0.0, 1.055, "Red rules mark the years reactors closed; the number is how many",
        transform=ax.transAxes, ha="left", fontsize=9.5, color=INK)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

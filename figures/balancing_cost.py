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

# Annotate first year, trough and post-trough peak by position, not by literal year,
# so the figure survives the next Monitoringbericht adding rows.
first = df.iloc[0]
trough = df.loc[df["cost_meur"].idxmin()]
after = df[df["year"] > trough["year"]]
peak = after.loc[after["cost_meur"].idxmax()] if len(after) else None
for row, dy in [(first, 34), (trough, -30)] + ([(peak, 26)] if peak is not None else []):
    ax.annotate(f"{row['cost_meur']:.0f}", xy=(row["year"], row["cost_meur"]), xytext=(0, dy),
                textcoords="offset points", ha="center", fontsize=10, color=INK)

ax.set_xlabel(""); ax.set_ylabel("EUR million a year")
y0, y1 = int(df["year"].min()), int(df["year"].max())
ax.set_ylim(0, df["cost_meur"].max() * 1.16); ax.set_xlim(y0 - 0.7, y1 + 0.7)
ax.set_xticks(range(y0, y1 + 1, 3))
for s in ("top", "right"): ax.spines[s].set_visible(False)
# The reactor-count digits sit at y = 1.02, so the subtitle clears them at 1.085.
ax.text(0.0, 1.18, f"German balancing-reserve procurement, {y0} to {y1}",
        transform=ax.transAxes, ha="left", fontsize=12.5, fontweight="bold")
ax.text(0.0, 1.085, "Red rules mark the years reactors closed; the number is how many",
        transform=ax.transAxes, ha="left", fontsize=9.5, color=INK)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

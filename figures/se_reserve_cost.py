#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Svenska kraftnat's ancillary-service cost by product, net, 2023 to 2025.
Data from Refresh.scala seReserveCost -> se-reserve-cost.csv; this only draws it.
Usage: python se_reserve_cost.py <csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

df = pd.read_csv(sys.argv[1])
wide = df.pivot(index="year", columns="product", values="msek")[["FCR", "aFRR", "mFRR"]]
COL = {"FCR": "#1f6fd1", "aFRR": "#c9ccd4", "mFRR": "#e34948"}
INK = "#52514e"
years = list(wide.index)
totals = wide.sum(axis=1)

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(7.4, 3.6))

# Axis limits first, so the in-bar label test can be expressed as a fraction of the
# axis rather than as a magic MSEK number: a segment shorter than this cannot hold
# 9.5pt digits without spilling into its neighbour.
ymax = float(totals.max()) * 1.20
ax.set_ylim(0, ymax)
fits = ymax * 0.055

base = [0.0] * len(wide)
for col in wide.columns:
    ax.bar(years, wide[col], bottom=base, width=0.54, color=COL[col], label=None, zorder=3)
    for x, v, b in zip(years, wide[col], base):
        if v >= fits:                      # comfortably inside the segment
            ax.text(x, b + v / 2, f"{int(round(v)):,}".replace(",", " "), ha="center",
                    va="center", fontsize=9.5, color="white" if col != "aFRR" else INK,
                    fontweight="bold")
        else:                              # too short to hold digits: set it beside the bar
            ax.annotate(f"{int(round(v)):,}".replace(",", " "), xy=(x + 0.29, b + v / 2),
                        xytext=(8, 0), textcoords="offset points", va="center",
                        fontsize=9, color=INK,
                        arrowprops=dict(arrowstyle="-", color="#c9c9c4", lw=0.8,
                                        shrinkA=0, shrinkB=2))
    base = [b + v for b, v in zip(base, wide[col])]

for x, t in zip(years, totals):
    ax.text(x, t + ymax * 0.022, f"{int(round(t)):,}".replace(",", " "), ha="center",
            fontsize=10, color=INK)

# Direct series labels beside the last bar, so no legend box sits over the data.
last = wide.iloc[-1]
run = 0.0
for col in wide.columns:
    ax.text(years[-1] + 0.35, run + last[col] / 2, col, va="center", fontsize=10,
            color=COL[col], fontweight="bold")
    run += last[col]

ax.set_xticks(years)
ax.set_xlim(years[0] - 0.45, years[-1] + 0.95)
ax.set_ylabel("MSEK a year, net")
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.text(0.0, 1.14, "What Sweden pays, by reserve product",
        transform=ax.transAxes, ha="left", fontsize=12.5, fontweight="bold")
ax.text(0.0, 1.045, "Containment fell to a fifth while manual restoration went up seventyfold",
        transform=ax.transAxes, ha="left", fontsize=9.5, color=INK)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

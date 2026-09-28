#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Svenska kraftnat's ancillary-service cost by product, 2023 to 2025.
Data from Refresh.scala seReserveCost -> se-reserve-cost.csv; this only draws it.
Usage: python se_reserve_cost.py <csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

df = pd.read_csv(sys.argv[1])
wide = df.pivot(index="year", columns="product", values="msek")[["FCR", "aFRR", "mFRR"]]
COL = {"FCR": "#1f6fd1", "aFRR": "#c9ccd4", "mFRR": "#e34948"}
INK = "#52514e"

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(7.4, 3.6))

base = [0.0] * len(wide)
for col in wide.columns:
    ax.bar(wide.index, wide[col], bottom=base, width=0.54, color=COL[col], label=col, zorder=3)
    for x, v, b in zip(wide.index, wide[col], base):
        if v > 300:   # 377 is the smallest aFRR segment; keep all three labelled
            ax.text(x, b + v / 2, f"{int(round(v)):,}".replace(",", " "), ha="center", va="center",
                    fontsize=9.5, color="white" if col != "aFRR" else INK, fontweight="bold")
    base = [b + v for b, v in zip(base, wide[col])]

for x, t in zip(wide.index, base):
    ax.text(x, t + 220, f"{int(round(t)):,}".replace(",", " "), ha="center", fontsize=10, color=INK)

# direct series labels beside the 2025 bar, so no legend box sits over the data
last = wide.iloc[-1]
lastyear = int(wide.index.max())
for col, y in [("mFRR", last["FCR"] + last["aFRR"] + last["mFRR"] / 2),
               ("aFRR", last["FCR"] + last["aFRR"] / 2),
               ("FCR", last["FCR"] / 2)]:
    ax.text(lastyear + 0.35, y, col, va="center", fontsize=10, color=COL[col], fontweight="bold")

ax.set_xticks(list(wide.index))
ax.set_xlim(wide.index.min() - 0.45, lastyear + 0.95)
ax.set_ylim(0, float(base.max() if hasattr(base, "max") else max(base)) * 1.20)
ax.set_ylabel("MSEK a year, net")
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.text(0.0, 1.14, "What Sweden pays, by reserve product",
        transform=ax.transAxes, ha="left", fontsize=12.5, fontweight="bold")
ax.text(0.0, 1.045, "Containment fell to a fifth while manual restoration went up seventyfold",
        transform=ax.transAxes, ha="left", fontsize=9.5, color=INK)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

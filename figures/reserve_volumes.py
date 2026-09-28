#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Svenska kraftnat's reserve volume requirement by product, 2025 against 2030.
Data from Refresh.scala reserveVolumes -> reserve-volumes.csv; this only draws it.
Usage: python reserve_volumes.py <csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

df = pd.read_csv(sys.argv[1])
NOW, THEN, INK = "#c9ccd4", "#1f6fd1", "#52514e"

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(7.4, 3.9))

y = range(len(df))
h = 0.38
ax.barh([v + h / 2 for v in y], df["mw2025"], height=h, color=NOW, label="2025", zorder=3)
ax.barh([v - h / 2 for v in y], df["mw2030"], height=h, color=THEN, label="2030", zorder=3)

for i, r in df.iterrows():
    ax.text(r["mw2025"] + 18, i + h / 2, f"{int(r['mw2025'])}", va="center", fontsize=9, color=INK)
    ax.text(r["mw2030"] + 18, i - h / 2, f"{int(r['mw2030'])}", va="center", fontsize=9,
            color=THEN, fontweight="bold" if r["mw2030"] != r["mw2025"] else "normal")

ax.set_yticks(list(y)); ax.set_yticklabels(df["product"])
ax.invert_yaxis()
ax.set_xlabel("megawatts, Sweden's share of the Nordic requirement")
ax.set_xlim(0, 1620)
ax.grid(axis="y", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)

split = (df["category"] == "Containment").sum()
ax.axhline(split - 0.5, color="#c9c9c4", lw=1.0)
ax.text(1570, 0.55, "containment\nset by the\ndimensioning fault", ha="right", va="center",
        fontsize=9, color=INK, linespacing=1.4)
ax.text(1570, 4.6, "restoration\nset by area imbalance\nand market design", ha="right", va="center",
        fontsize=9, color=THEN, linespacing=1.4)

ax.legend(frameon=False, loc="lower right", fontsize=9, bbox_to_anchor=(1.0, -0.02))
ax.text(0.0, 1.10, "What Svenska kraftnat must hold, 2025 and 2030",
        transform=ax.transAxes, ha="left", fontsize=12.5, fontweight="bold")
ax.text(0.0, 1.035, "The reserves sized by inertia and the largest single fault do not grow at all",
        transform=ax.transAxes, ha="left", fontsize=9.5, color=INK)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

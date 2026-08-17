#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Germany's net electricity trade, 1990 to the latest full year (seaborn).
Diverging bars around zero: above = net importer, below = net exporter.
Input: data-refresh/de-net-trade.csv (year, twh) from `mill Refresh.scala deTrade`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

IMPORT, EXPORT, INK = "#e34948", "#2a78d6", "#52514e"
df = pd.read_csv(sys.argv[1])
first, last = int(df.year.min()), int(df.year.max())

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.2))
ax.bar(df.year, df.twh, width=0.72,
       color=[IMPORT if v > 0 else EXPORT for v in df.twh])
ax.axhline(0, color=INK, lw=1.1)

ax.set_ylabel("TWh per year"); ax.set_xlabel("")
ax.set_xlim(first - 1, last + 1)
ax.set_xticks(range(1990, last + 1, 5))
ax.grid(axis="x", visible=False)
for s in ("top", "right", "bottom"): ax.spines[s].set_visible(False)

# The sign is the whole point, so name each side rather than shipping a legend.
ax.text(first, ax.get_ylim()[1] * 0.86, "net importer", color=IMPORT,
        fontsize=10.5, fontweight="medium", va="top")
ax.text(first, ax.get_ylim()[0] * 0.86, "net exporter", color=EXPORT,
        fontsize=10.5, fontweight="medium", va="bottom")

peak = df.loc[df.twh.idxmin()]
ax.annotate(f"largest export surplus\n{int(peak.year)}: {-peak.twh:.0f} TWh",
            xy=(peak.year, peak.twh), xytext=(peak.year - 18, peak.twh * 0.62),
            color=INK, fontsize=9.5,
            arrowprops=dict(arrowstyle="-", color="#a8a8a3", lw=0.9))
flip = df[df.year >= 2023].iloc[0]
ax.annotate("last reactors closed,\nApril 2023",
            xy=(flip.year, flip.twh), xytext=(flip.year - 8.5, ax.get_ylim()[1] * 0.62),
            color=INK, fontsize=9.5,
            arrowprops=dict(arrowstyle="-", color="#a8a8a3", lw=0.9))

ax.set_title(f"Germany's net electricity trade, {first}–{last}",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

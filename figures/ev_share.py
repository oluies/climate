#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Share of new cars sold that are electric, 2015 to 2025 (seaborn).
Input: data-refresh/ev-share.csv from `mill Refresh.scala chapter03`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"Norway": "#4a3aa7", "Sweden": "#2a78d6", "China": "#e34948",
       "United Kingdom": "#eb6834", "World": "#161d1b", "United States": "#7a5c3e"}
ORDER = ["Norway", "Sweden", "China", "United Kingdom", "World", "United States"]
MUTED = "#8a8a85"
df = pd.read_csv(sys.argv[1])
first, last = int(df.year.min()), int(df.year.max())

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.6, 4.8))
for c in ORDER:
    d = df[df.country == c].sort_values("year")
    ax.plot(d.year, d.share, color=COL[c], lw=2.4 if c == "World" else 1.9,
            ls="--" if c == "World" else "-")

ends = sorted(((float(df[(df.country == c) & (df.year == last)].share.iloc[0]), c) for c in ORDER),
              reverse=True)
prev = None
for v, c in ends:
    y = v if prev is None or prev - v > 5.5 else prev - 5.5
    prev = y
    ax.text(last + 0.15, y, f"{c}  {v:.0f}%", color=COL[c], fontsize=9.5, va="center")

ax.set_ylabel("share of new cars sold that are electric", fontsize=10.5)
ax.set_xlabel("")
ax.set_xlim(first, last + 5.6)
ax.set_xticks(range(2016, last + 1, 2))
ax.set_ylim(0, 100)
ax.set_yticks(range(0, 101, 25))
ax.yaxis.set_major_formatter(lambda v, _: f"{v:.0f}%")
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"Electric share of new car sales, {first}–{last}",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("Norway has effectively finished; the United States has barely started.\n"
            "Battery-electric and plug-in hybrid together.",
            xy=(0.03, 0.60), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

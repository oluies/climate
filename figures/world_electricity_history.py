#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Electricity generation by region, 1985 to 2025 (seaborn).
The point of the figure is the shape of China's curve against everyone else's.
Input: data-refresh/world-electricity-history.csv from `mill Refresh.scala chapterJ`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"World": "#161d1b", "China": "#e34948", "US": "#7a5c3e",
       "Europe": "#2a78d6", "India": "#1baf7a", "Africa": "#eda100"}
ORDER = ["World", "China", "US", "Europe", "India", "Africa"]
MUTED = "#8a8a85"
df = pd.read_csv(sys.argv[1])
first, last = int(df.year.min()), int(df.year.max())

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.0))
for r in ORDER:
    d = df[df.region == r].sort_values("year")
    ax.plot(d.year, d.twh, color=COL[r], lw=2.4 if r == "World" else 1.9,
            ls="--" if r == "World" else "-", zorder=3 if r == "China" else 2)

ends = sorted(((float(df[(df.region == r) & (df.year == last)].twh.iloc[0]), r) for r in ORDER),
              reverse=True)
prev = None
for v, r in ends:
    y = v if prev is None or prev - v > 1500 else prev - 1500
    prev = y
    lab = f"{r}  {v:,.0f}".replace(",", " ")
    ax.text(last + 1, y, lab, color=COL[r], fontsize=9.5, va="center")

ax.set_ylabel("TWh generated per year", fontsize=10.5)
ax.set_xlabel("")
ax.set_xlim(first, last + 16)
ax.set_xticks(range(1990, last + 1, 10))
ax.set_ylim(0, 34000)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"World electricity generation by region, {first}–{last}",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("China added more generation after 2000 than the United States\n"
            "and Europe together produce in a year today",
            xy=(0.03, 0.93), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

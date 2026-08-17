#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Total energy supply per person, 1965 to 2025, in MacKay's kWh per day per person.
This is the book's own unit applied to the Energy Institute's per-capita series, so
his headline figures for Britain and America can be read straight off it.
Input: data-refresh/world-tes-percapita.csv from `mill Refresh.scala chapterJ`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"US": "#7a5c3e", "Sweden": "#4a3aa7", "Europe": "#2a78d6", "China": "#e34948",
       "World": "#161d1b", "United Kingdom": "#eb6834", "India": "#1baf7a", "Africa": "#eda100"}
ORDER = ["US", "Sweden", "China", "Europe", "United Kingdom", "World", "India", "Africa"]
MUTED = "#8a8a85"
df = pd.read_csv(sys.argv[1])
first, last = int(df.year.min()), int(df.year.max())

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.2))
for r in ORDER:
    d = df[df.region == r].sort_values("year")
    ax.plot(d.year, d.kwh_per_day, color=COL[r], lw=2.4 if r == "World" else 1.8,
            ls="--" if r == "World" else "-", zorder=3 if r == "World" else 2)

# Direct labels at the right edge, nudged apart so none overlap.
ends = sorted(((float(df[(df.region == r) & (df.year == last)].kwh_per_day.iloc[0]), r)
               for r in ORDER), reverse=True)
prev = None
for v, r in ends:
    y = v if prev is None or prev - v > 6 else prev - 6
    prev = y
    ax.text(last + 1.2, y, f"{r}  {v:.0f}", color=COL[r], fontsize=9.5, va="center")

ax.set_ylabel("kWh per day per person", fontsize=10.5)
ax.set_xlabel("")
ax.set_xlim(first, last + 22)
ax.set_xticks(range(1970, last + 1, 10))
ax.set_ylim(0, 270)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"Energy supply per person, {first}–{last}, in the units of this book",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("MacKay put Britain at 125 kWh/d and America at 250 when he wrote.\n"
            "Britain has since fallen to 69; America is back where it was in 1965.",
            xy=(0.30, 0.965), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

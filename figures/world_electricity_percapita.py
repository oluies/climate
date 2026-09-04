#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Electricity generation per person by region, 1985 to 2025, in kWh per day.
The companion to the TWh figure: the totals say China built the most, the
per-person version says what that amounts to for one Chinese citizen, and it is
the only view in which Africa's line is visible at all.
Input: data-refresh/world-electricity-percapita.csv from `mill Refresh.scala chapterJ`."""
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
    ax.plot(d.year, d.kwh_per_day, color=COL[r], lw=2.4 if r == "World" else 1.9,
            ls="--" if r == "World" else "-", zorder=3 if r == "China" else 2)

# Direct labels at the right edge, nudged apart so none overlap.
ends = sorted(((float(df[(df.region == r) & (df.year == last)].kwh_per_day.iloc[0]), r)
               for r in ORDER), reverse=True)
prev = None
for v, r in ends:
    y = v if prev is None or prev - v > 2.2 else prev - 2.2
    prev = y
    ax.text(last + 1, y, f"{r}  {v:.1f}", color=COL[r], fontsize=9.5, va="center")

ax.set_ylabel("kWh per day per person", fontsize=10.5)
ax.set_xlabel("")
ax.set_xlim(first, last + 14)
ax.set_xticks(range(1990, last + 1, 10))
ax.set_ylim(0, max(df.kwh_per_day) * 1.1)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"Electricity generation per person by region, {first}–{last}",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
# The three years in the note are read off the data, not typed in: the workbook
# moves once a year and a hand-written caption would drift away from its own lines.
w = df.pivot(index="year", columns="region", values="kwh_per_day")
cross = int(w.index[w.China >= w.Europe][0])
uspeak = int(w.US.idxmax())
matched = int(w.index[w.China >= w.Africa.iloc[-1]][0])
ax.annotate(f"China passed Europe in {cross} and is still climbing. The United States\n"
            f"peaked in {uspeak}. Africa is where China was in {matched}.",
            xy=(0.03, 0.72), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

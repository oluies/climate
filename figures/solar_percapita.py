#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Solar electricity per person, in this book's units, against MacKay's two estimates.
Input: data-refresh/solar-percapita.csv from `mill Refresh.scala chapter06`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"Total World": "#161d1b", "China": "#e34948",
       "Germany": "#eda100", "United Kingdom": "#eb6834"}
ORDER = ["Germany", "China", "Total World", "United Kingdom"]
NAME = {"Total World": "World"}
MUTED, ROOF = "#8a8a85", "#4a3aa7"

df = pd.read_csv(sys.argv[1])
first, last = int(df.year.min()), int(df.year.max())

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.0))

ax.axhline(5.0, color=ROOF, lw=1.4, ls=(0, (5, 3)), zorder=1)
ax.annotate("MacKay's roof estimate: 5 kWh/d per person",
            xy=(first + 0.3, 5.0), xytext=(0, 7), textcoords="offset points",
            fontsize=9.5, color=ROOF)

for r in ORDER:
    d = df[df.region == r].sort_values("year")
    ax.plot(d.year, d.kwh_per_day, color=COL[r],
            lw=2.4 if r == "Total World" else 1.9,
            ls="--" if r == "Total World" else "-")

ends = sorted(((float(df[(df.region == r) & (df.year == last)].kwh_per_day.iloc[0]), r)
               for r in ORDER), reverse=True)
prev = None
for v, r in ends:
    y = v if prev is None or prev - v > 0.35 else prev - 0.35
    prev = y
    ax.text(last + 0.3, y, f"{NAME.get(r, r)}  {v:.2f}", color=COL[r], fontsize=9.5, va="center")

ax.set_ylabel("kWh per day per person", fontsize=10.5)
ax.set_xlabel("")
ax.set_xlim(first, last + 6)
ax.set_xticks(range(2000, last + 1, 5))
ax.set_ylim(0, 5.6)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"Solar electricity per person, {first}–{last}",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("Nobody has yet reached the output MacKay assigned to roofs alone, and his 50 kWh/d\n"
            "solar farm is ten times above the top of this chart.",
            xy=(0, -0.135), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

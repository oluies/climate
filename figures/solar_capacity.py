#!/usr/bin/env python3
"""World installed solar PV capacity, 2000 to 2025, against MacKay's fantasy.
Input: data-refresh/solar-capacity.csv from `mill Refresh.scala chapter06`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COL = {"Total World": "#161d1b", "China": "#e34948",
       "Germany": "#eda100", "United Kingdom": "#eb6834"}
ORDER = ["Total World", "China", "Germany", "United Kingdom"]
NAME = {"Total World": "World"}
MUTED, RULE = "#8a8a85", "#4a3aa7"
MACKAY = 1250.0                      # GW needed for 50 kWh/d/person in the UK

df = pd.read_csv(sys.argv[1])
first, last = int(df.year.min()), int(df.year.max())

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.2))

ax.axhline(MACKAY, color=RULE, lw=1.4, ls=(0, (5, 3)), zorder=1)
ax.annotate("MacKay's “beyond the bounds of plausibility”: 1250 GW",
            xy=(first + 0.3, MACKAY), xytext=(0, 7), textcoords="offset points",
            fontsize=9.5, color=RULE)

for r in ORDER:
    d = df[df.region == r].sort_values("year")
    ax.plot(d.year, d.gw, color=COL[r], lw=2.4 if r == "Total World" else 1.9,
            ls="--" if r == "Total World" else "-")

ends = sorted(((float(df[(df.region == r) & (df.year == last)].gw.iloc[0]), r) for r in ORDER),
              reverse=True)
prev = None
for v, r in ends:
    y = v if prev is None or prev - v > 150 else prev - 150
    prev = y
    ax.text(last + 0.4, y, f"{NAME.get(r, r)}  {v:,.0f}".replace(",", " "),
            color=COL[r], fontsize=9.5, va="center")

ax.set_ylabel("installed capacity (GW)", fontsize=10.5)
ax.set_xlabel("")
ax.set_xlim(first, last + 7)
ax.set_xticks(range(2000, last + 1, 5))
ax.set_ylim(0, 2600)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"World installed solar PV capacity, {first}–{last}",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("The capacity MacKay called more than a hundred times the world's entire\n"
            "photovoltaic fleet has been passed twice over — and nearly reached by China alone.",
            xy=(0.03, 0.93), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

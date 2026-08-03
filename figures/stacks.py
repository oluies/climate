#!/usr/bin/env python3
"""MacKay's balance sheet against the 2026 revision: both stacks, both editions.
Input: data-refresh/stacks.csv from `mill Refresh.scala stacks`."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

# Consumption in reds and browns, production in the technology colours used
# elsewhere in this edition.
COL = {"Stuff": "#7a5c3e", "Cars": "#c0392b", "Planes": "#e05c4a", "Heating and cooling": "#eb6834",
       "Transporting stuff": "#a9603f", "Food and farming": "#d98b5f", "Gadgets": "#8c5a4a",
       "Light": "#f0b49a", "Defence": "#6e3b32", "Universities": "#caa08c",
       "Data centres": "#3f3f46", "NHS estate": "#b07a6a",
       "Solar": "#eda100", "Offshore wind": "#1baf7a", "Onshore wind": "#4fd1a0",
       "Tide": "#2a78d6", "Wave": "#7fb3e8", "Geothermal": "#8a6d3b", "Hydro": "#1f5fa8"}
ORDER = ["Universities", "NHS estate", "Data centres", "Defence", "Light", "Gadgets",
         "Food and farming", "Transporting stuff", "Heating and cooling", "Planes",
         "Cars", "Stuff",
         "Geothermal", "Wave", "Hydro", "Tide", "Onshore wind", "Offshore wind", "Solar"]
COLS = ["MacKay 2008", "2026 revision", "MacKay's ceiling", "Britain 2025"]
INK, MUTED = "#161d1b", "#8a8a85"

d = duckdb.sql(f'''SELECT stack, "column" AS col, item, kwh_per_day
                     FROM read_csv_auto('{sys.argv[1]}')''').fetchnumpy()
vals = {}
for s, c, i, v in zip(d["stack"], d["col"], d["item"], d["kwh_per_day"]):
    vals.setdefault(c, {})[i] = v

fig, ax = plt.subplots(figsize=(9.4, 7.2))
x = [0, 1, 2.55, 3.55]
for xi, col in zip(x, COLS):
    bottom = 0.0
    for item in ORDER:
        v = vals.get(col, {}).get(item, 0.0)
        if v <= 0:
            continue
        ax.bar(xi, v, bottom=bottom, width=0.72, color=COL[item],
               edgecolor="white", linewidth=0.8, zorder=2)
        if v >= 10:
            ax.text(xi, bottom + v / 2, f"{item}\n{v:.0f}", ha="center", va="center",
                    fontsize=8, color="white", linespacing=1.15)
        bottom += v
    ax.text(xi, bottom + 3, f"{bottom:.0f}", ha="center", fontsize=12, fontweight="bold", color=INK)
    if col == "Britain 2025":
        ax.annotate("about 3% of the ceiling\nto its left",
                    xy=(xi + 0.34, bottom / 2), xytext=(xi + 0.10, 40),
                    ha="center", fontsize=9, color=INK,
                    arrowprops=dict(arrowstyle="-", color=MUTED, lw=0.9,
                                    connectionstyle="arc3,rad=0.25"))

ax.set_xticks(x)
ax.set_xticklabels(COLS, fontsize=10)
ax.set_ylabel("kWh per day per person", fontsize=10.5)
ax.set_ylim(0, 215)
ax.set_xlim(-0.55, 4.1)
ax.grid(axis="y", color="#ededea", zorder=0)
ax.set_axisbelow(True)
for s in ("top", "right"): ax.spines[s].set_visible(False)

for xm, label in ((0.5, "CONSUMPTION"), (3.05, "PRODUCTION")):
    ax.annotate(label, xy=(xm, 205), ha="center", fontsize=10.5,
                fontweight="bold", color=MUTED)

ax.set_title("The balance sheet, 2008 and 2026", loc="left",
             fontsize=13.5, fontweight="bold", pad=14)
ax.annotate("Consumption is what a typical affluent person uses, revised chapter by chapter. Production compares MacKay's\n"
            "maximum-conceivable ceilings with what Britain actually generated in 2025.\n"
            "Segments under about 10 kWh/d are drawn but not labelled — in the consumption columns the unlabelled bands are, from the bottom, universities,\n"
            "NHS estate, data centres, defence, light and gadgets. The two halves are not the same accounting and should\n"
            "not be read as a shortfall.",
            xy=(0, -0.115), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

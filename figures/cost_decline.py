#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Render the cost-decline figure with seaborn (dataviz-validated palette).
Data comes from Refresh.scala (DuckDB) as cost-decline.csv; this only draws it.
Usage: python cost_decline.py <csv> <out.svg>"""
import sys
import pandas as pd, seaborn as sns, matplotlib.pyplot as plt

df = pd.read_csv(sys.argv[1])
order = ["Solar PV", "Onshore wind", "Offshore wind"]
# validated categorical palette (slots 1-3), colorblind-safe, fixed order
palette = {"Solar PV": "#2a78d6", "Onshore wind": "#eb6834", "Offshore wind": "#1baf7a"}

sns.set_theme(style="whitegrid", rc={"axes.edgecolor": "#c9c9c4", "grid.color": "#ededea"})
g = sns.catplot(data=df, x="tech", y="lcoe", col="year", kind="bar",
                hue="tech", hue_order=order, order=order, palette=palette,
                dodge=False, legend=False, height=3.4, aspect=1.05, saturation=1)
g.set_titles("{col_name}", size=13, weight="bold")
g.set_axis_labels("", "USD / MWh")
for ax in g.axes.flat:
    [ax.bar_label(c, fmt="%d", padding=3, fontsize=10, color="#52514e") for c in ax.containers]
    ax.set_xlabel(""); ax.margins(y=0.16)
    ax.grid(axis="x", visible=False)
    ax.tick_params(axis="x", labelsize=9.5)
    for s in ("top", "right"): ax.spines[s].set_visible(False)
g.figure.suptitle("Levelized cost of electricity, global weighted average",
                  x=0.02, y=1.03, ha="left", fontsize=12.5, fontweight="bold")
g.savefig(sys.argv[2], format="svg", bbox_inches="tight")
print("wrote", sys.argv[2])

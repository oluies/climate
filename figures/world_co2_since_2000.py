#!/usr/bin/env python3
"""Change in CO2 from energy by region, 2000 to 2025 (seaborn).
Diverging bars around zero: the question this answers is who is driving the
*increase*, which is a different question from who emits most, or most per person.
Input: data-refresh/world-co2-since-2000.csv from `mill Refresh.scala chapterJ`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

UP, DOWN, INK, MUTED = "#e34948", "#2a78d6", "#52514e", "#8a8a85"
df = pd.read_csv(sys.argv[1])
world = df[df.region == "World"].iloc[0]
d = df[df.region != "World"].sort_values("change_mt", ascending=False)

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.2))
ax.barh(d.region, d.change_mt, height=0.68,
        color=[UP if v > 0 else DOWN for v in d.change_mt])
ax.invert_yaxis()
ax.axvline(0, color=INK, lw=1.1)
ax.bar_label(ax.containers[0],
             labels=[f"{v:+,.0f}".replace(",", " ") for v in d.change_mt],
             padding=5, fontsize=10, color=INK)
ax.set_xlabel("change in CO2 from energy, 2000 to 2025 (million tonnes a year)",
              fontsize=10, color=INK)
ax.set_xlim(d.change_mt.min() * 2.6, d.change_mt.max() * 1.22)   # room for the left labels
ax.grid(axis="y", visible=False)
ax.tick_params(axis="y", length=0)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
share = (d[d.region == "China"].change_mt.iloc[0] / world.change_mt) * 100
ax.set_title("Who drove the increase in emissions this century",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
wt = f"{world.change_mt:,.0f}".replace(",", " ")   # thin-space the number only
ax.annotate(f"world total rose {wt} Mt/year over the period;\n"
            f"China accounts for {share:.0f}% of that increase, while the\n"
            f"United States and Europe both fell",
            xy=(0.985, 0.13), xycoords="axes fraction", ha="right",
            fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

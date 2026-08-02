#!/usr/bin/env python3
"""UK electricity generation per person (MacKay's kWh/d/p), seaborn.
Data from Refresh.scala chapterKElec -> uk-electricity-percapita.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt
df = pd.read_csv(sys.argv[1])
COL = {"Coal": "#3f3f46", "Gas": "#eb6834", "Nuclear": "#4a3aa7", "Wind": "#1baf7a",
       "Solar": "#eda100", "Hydropower": "#2a78d6", "Bioenergy": "#e34948"}
order = list(COL.keys())
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.4))
sns.lineplot(data=df, x="year", y="kwhdp", hue="source", hue_order=order, palette=COL, lw=2, legend=False, ax=ax)
ax.set_xlabel(""); ax.set_ylabel("kWh per day per person")
xmax, xmin = int(df.year.max()), int(df.year.min())
ends = sorted([(s, float(df[(df.source == s) & (df.year == xmax)]["kwhdp"].iloc[0])) for s in order], key=lambda t: t[1])
prev = -1e9
for src, y in ends:
    yy = y if y - prev > 0.28 else prev + 0.28
    prev = yy
    ax.text(xmax + 0.6, yy, src, color=COL[src], fontsize=9.5, va="center")
ax.set_xlim(xmin, xmax + 8); ax.margins(y=0.04)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"UK electricity generation per person, {xmin}–{xmax}", loc="left", fontsize=12.5, fontweight="bold", pad=12)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

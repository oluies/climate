#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""UK electricity mix line chart, seaborn. Data from Refresh.scala uk -> uk-electricity-mix.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt
df = pd.read_csv(sys.argv[1])
COL = {"Coal": "#3f3f46", "Gas": "#eb6834", "Nuclear": "#4a3aa7", "Wind": "#1baf7a",
       "Solar": "#eda100", "Hydropower": "#2a78d6", "Bioenergy": "#e34948"}
order = list(COL.keys())
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.4))
sns.lineplot(data=df, x="year", y="twh", hue="source", hue_order=order, palette=COL, lw=2, legend=False, ax=ax)
ax.set_xlabel(""); ax.set_ylabel("TWh per year")
xmax, xmin = int(df["year"].max()), int(df["year"].min())
ends = sorted([(s, float(df[(df.source == s) & (df.year == xmax)]["twh"].iloc[0])) for s in order], key=lambda t: t[1])
prev = -1e9
for src, y in ends:
    yy = y if y - prev > 7 else prev + 7
    prev = yy
    ax.text(xmax + 0.8, yy, src, color=COL[src], fontsize=9.5, va="center")
ax.set_xlim(xmin, xmax + 9); ax.margins(y=0.04)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"UK electricity generation by source, {xmin}–{xmax}", loc="left", fontsize=12.5, fontweight="bold", pad=12)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

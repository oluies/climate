#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""UK coal vs gas consumption over time (MacKay's Fig K.7 territory), seaborn.
Reuses data-refresh/uk-primary-energy.csv (Coal, Gas rows)."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt
df = pd.read_csv(sys.argv[1])
df = df[df.category.isin(["Coal", "Gas"])]
COL = {"Coal": "#3f3f46", "Gas": "#eb6834"}
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.2))
sns.lineplot(data=df, x="year", y="twh", hue="category", hue_order=["Coal", "Gas"],
             palette=COL, lw=2.2, legend=False, ax=ax)
ax.set_ylabel("TWh per year"); ax.set_xlabel("")
last = int(df.year.max())
for c in ["Coal", "Gas"]:
    y = float(df[(df.category == c) & (df.year == last)]["twh"].iloc[0])
    ax.text(last + 0.8, y, c, color=COL[c], fontsize=10.5, va="center", fontweight="medium")
ax.set_xlim(int(df.year.min()), last + 8); ax.margins(y=0.06)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title(f"UK coal and gas consumption, {int(df.year.min())}–{last}",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

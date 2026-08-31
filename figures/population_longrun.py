#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 1.6: British and world population, 1700 to 2025, replacing MacKay's two
small population graphs. Drawn on the same window as the coal figures so the
shapes can be laid beside each other, which is the comparison his text makes.
Input: data-refresh/population-longrun.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

INK, MUTED, GRID, UK, WORLD = "#161d1b", "#8a8a85", "#ededea", "#4a3aa7", "#161d1b"
df = pd.read_csv(sys.argv[1]).sort_values("year")
df = df[(df.year >= 1700) & (df.year <= 2025)]
last = df.iloc[-1]

sns.set_theme(style="whitegrid", rc={"grid.color": GRID, "axes.edgecolor": "#c9c9c4"})
fig, axes = plt.subplots(1, 2, figsize=(8.8, 4.8))

ax = axes[0]
ax.plot(df.year, df.uk / 1e6, color=UK, lw=2.4)
ax.fill_between(df.year, df.uk / 1e6, color=UK, alpha=0.12)
ax.set_title("United Kingdom", loc="left", fontsize=11, color=INK, pad=8)
ax.set_ylabel("millions", fontsize=10)
ax.set_ylim(0, 78)
for y, lab in ((1800, "1800\n10.8M"), (1900, "1900\n41.1M")):
    v = float(df.loc[df.year == y, "uk"].iloc[0]) / 1e6
    ax.plot([y], [v], "o", ms=5, color=UK)
    ax.annotate(lab, xy=(y, v), xytext=(-6, 10), textcoords="offset points",
                fontsize=8.5, color=UK, ha="right", linespacing=1.3)
ax.annotate(f"2025\n{last.uk/1e6:.0f}M", xy=(2025, last.uk / 1e6), xytext=(-8, -34),
            textcoords="offset points", fontsize=8.5, color=UK, ha="right",
            fontweight="bold", linespacing=1.3)

ax = axes[1]
ax.plot(df.year, df.world / 1e9, color=WORLD, lw=2.4)
ax.fill_between(df.year, df.world / 1e9, color=WORLD, alpha=0.10)
ax.set_title("World", loc="left", fontsize=11, color=INK, pad=8)
ax.set_ylabel("billions", fontsize=10)
ax.set_ylim(0, 9)
for y, lab in ((1800, "1800\n1.0bn"), (1900, "1900\n1.6bn")):
    v = float(df.loc[df.year == y, "world"].iloc[0]) / 1e9
    ax.plot([y], [v], "o", ms=5, color=WORLD)
    ax.annotate(lab, xy=(y, v), xytext=(-6, 10), textcoords="offset points",
                fontsize=8.5, color=INK, ha="right", linespacing=1.3)
ax.annotate(f"2025\n{last.world/1e9:.1f}bn", xy=(2025, last.world / 1e9), xytext=(-8, -34),
            textcoords="offset points", fontsize=8.5, color=INK, ha="right",
            fontweight="bold", linespacing=1.3)

for ax in axes:
    ax.set_xlim(1700, 2025); ax.set_xticks(range(1700, 2026, 100))
    ax.tick_params(labelsize=9)

fig.suptitle("Figure 1.6. The population growth the coal paid for, and what happened next.",
             x=0.055, y=0.99, ha="left", fontsize=12.5, fontweight="bold")
uk0, uk9 = df.iloc[0].uk, float(df.loc[df.year == 1900, "uk"].iloc[0])
w0, w9 = df.iloc[0].world, float(df.loc[df.year == 1900, "world"].iloc[0])
fig.text(0.055, 0.930, f"Same window as the coal figures, so the shapes can be laid beside each other. Britain multiplied "
                       f"{uk9/uk0:.0f}-fold while its coal did,\nthen grew {100*(last.uk/uk9-1):.0f}% in the 125 years since. "
                       f"The world went the other way: {w9/w0:.1f}-fold to 1900, then {last.world/w9:.1f}-fold.",
         fontsize=9.5, color=MUTED, va="top", linespacing=1.5)
fig.text(0.055, 0.03, "Population to 2023 and United Nations projections thereafter. Source: Our World in Data.",
         fontsize=8.5, color=MUTED, va="top")
fig.subplots_adjust(top=0.76, bottom=0.16, left=0.085, right=0.98, wspace=0.28)
fig.savefig(sys.argv[2], format="svg"); print("wrote", sys.argv[2])

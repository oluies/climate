#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""What a megawatt of aFRR capacity costs, by country, four weeks of September 2026.
Data from Refresh.scala afrrPrices -> afrr-capacity-price.csv; this only draws it.
Usage: python afrr_capacity_price.py <csv> <out.svg>"""
import sys, numpy as np, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

df = pd.read_csv(sys.argv[1])
UP, DOWN, LINK, INK = "#e34948", "#1f6fd1", "#d6d6d1", "#52514e"

price = df.pivot(index="country", columns="direction", values="eur_mw_h")
gwh = df.pivot(index="country", columns="direction", values="gwh")
src = df.groupby("country")["source"].first()
# Dearest upward capacity at the top. A country that bought none has no upward price,
# and sorts to the bottom on its downward price rather than being dropped.
order = price.assign(k=price["up"].fillna(price["down"] - 1e6)).sort_values("k").index
price, gwh = price.loc[order], gwh.loc[order]
y = np.arange(len(order))

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(7.4, 6.4))

# Area proportional to the capacity bought, so a price struck on a token volume reads as
# the token it is. The scale is set by the largest dot rather than by a constant, because
# the range runs over four orders of magnitude and a fixed constant either buries the small
# countries or lets the German dot cover the rows above and below it.
BIGGEST_PTS = 115.0
FLOOR_PTS = 5.0
gmax = float(np.nanmax(gwh.to_numpy()))
def area(g): return np.maximum(BIGGEST_PTS * np.asarray(g, dtype=float) / gmax, FLOOR_PTS)

for i, c in enumerate(order):
    u, d = price.loc[c, "up"], price.loc[c, "down"]
    if pd.notna(u) and pd.notna(d):
        ax.plot([u, d], [i, i], color=LINK, lw=1.4, zorder=2, solid_capstyle="round")
ax.scatter(price["down"], y, s=area(gwh["down"]), color=DOWN, zorder=4,
           edgecolor="white", linewidth=0.8)
ax.scatter(price["up"], y, s=area(gwh["up"]), color=UP, zorder=5,
           edgecolor="white", linewidth=0.8)

ax.set_xscale("log")
ax.set_yticks(y)
ax.set_yticklabels([c + (" †" if src[c] != "ENTSO-E" else "") for c in order])
ax.invert_yaxis()
ax.set_ylim(len(order) - 0.4, -0.6)
ax.set_xlabel("euros per megawatt per hour of capacity held, volume-weighted (log scale)")
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)

ax.text(0.0, 1.135, "What a megawatt of aFRR capacity costs across Europe",
        transform=ax.transAxes, ha="left", fontsize=12.5, fontweight="bold")
ax.text(0.0, 1.078, "Four weeks, 1 to 28 September 2026",
        transform=ax.transAxes, ha="left", fontsize=9.5, color=INK)

# One legend row above the axes, at fixed positions. Labelling the two directions on a
# row of the chart itself does not work: the row is chosen by price, and in the cheapest
# country the two prices are within a tenth of a euro of each other, so the two labels
# land on top of one another. The dot sizes in the key are on the same scale as the chart.
KEY = [("up", "upward", UP, 0.00), ("down", "downward", DOWN, 0.17)]
for col, lab, c, x in KEY:
    ax.scatter([x], [1.018], s=44, color=c, transform=ax.transAxes, clip_on=False, zorder=6)
    ax.text(x + 0.022, 1.018, lab, transform=ax.transAxes, va="center", ha="left",
            fontsize=9.5, color=c, fontweight="bold")
key = [10 ** int(np.floor(np.log10(gmax))), 10 ** (int(np.floor(np.log10(gmax))) - 2)]
for j, (g, x) in enumerate(zip(key, (0.58, 0.80))):
    ax.scatter([x], [1.018], s=area(g), color=INK, alpha=0.45,
               transform=ax.transAxes, clip_on=False, zorder=6)
    ax.text(x + 0.022, 1.018, f"{g:,.0f}".replace(",", " ") + (" GWh bought" if j else ""),
            transform=ax.transAxes, va="center", ha="left", fontsize=8.5, color=INK)

if (src != "ENTSO-E").any():
    ax.text(0.0, -0.105, "\u2020 Sweden does not report procured aFRR capacity to the "
            "platform; its row is Svenska kraftnat's own publication",
            transform=ax.transAxes, ha="left", va="top", fontsize=8.5, color=INK)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 1.2a: North Sea oil production and the real oil price, carried forward
from MacKay's figure 1.2. Input: data-refresh/north-sea-oil.csv.

MacKay's version ended in 2007, just as the decline began and the price was
climbing. The point of redrawing it is what the next eighteen years show: the
price went far higher than anything on his chart and the production kept falling.
Two axes are used deliberately here, against the usual rule, because the whole
argument is that the two series moved independently -- MacKay's own figure pairs
them the same way, and this figure exists to be compared with his."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
UK, NO, DK, PRICE = "#4a3aa7", "#1baf7a", "#eda100", "#e34948"

df = pd.read_csv(sys.argv[1]).sort_values("year")
df = df[df.total_kbd > 0]
first, last = int(df.year.min()), int(df.year.max())
peak = df.loc[df.total_kbd.idxmax()]

sns.set_theme(style="whitegrid", rc={"grid.color": GRID, "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.2))

ax.stackplot(df.year, df.uk_kbd / 1000, df.no_kbd / 1000, df.dk_kbd / 1000,
             labels=["United Kingdom", "Norway", "Denmark"],
             colors=[UK, NO, DK], alpha=0.85, zorder=2)
ax.set_ylabel("million barrels per day", fontsize=10.5)
ax.set_xlim(first, last); ax.set_ylim(0, 7.2)
ax.set_xticks(range(1970, last + 1, 10))
ax.legend(frameon=False, fontsize=9.5, loc="upper left", ncol=3,
          bbox_to_anchor=(0.0, 1.0))

ax2 = ax.twinx()
ax2.plot(df.year, df.price_2025usd, color=PRICE, lw=2.0, ls=(0, (5, 2)), zorder=4)
ax2.set_ylabel("crude oil price, 2025 dollars per barrel", fontsize=10.5, color=PRICE)
ax2.tick_params(axis="y", colors=PRICE, labelsize=9.5)
ax2.set_ylim(0, 190); ax2.grid(False)

ax.annotate(f"peak {int(peak.year)}: {peak.total_kbd/1000:.1f} Mb/d",
            xy=(peak.year, peak.total_kbd / 1000), xytext=(8, 10),
            textcoords="offset points", fontsize=9.5, color=INK, ha="left",
            fontweight="bold")
endv = df.iloc[-1]
ax.annotate(f"{last}: {endv.total_kbd/1000:.1f}", xy=(last, endv.total_kbd / 1000),
            xytext=(-4, 12), textcoords="offset points", fontsize=9.5,
            color=INK, ha="right", fontweight="bold")
pk_price = df.loc[df.price_2025usd.idxmax()]
ax2.annotate("real price, right axis", xy=(pk_price.year, pk_price.price_2025usd),
             xytext=(6, 6), textcoords="offset points", fontsize=9.5,
             color=PRICE, ha="left")

ax.set_title("Figure 1.2a. Are “our” fossil fuels running out? Yes.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
ax.annotate("North Sea production fell 57% from its 2000 peak while the real price went higher\n"
            "than anything on MacKay's chart. Price did not bring the oil back.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("United Kingdom, Norway and Denmark. Production: Energy Institute Statistical Review; "
            "price in constant 2025 dollars, same source.",
            xy=(0, -0.16), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

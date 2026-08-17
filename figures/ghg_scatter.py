#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib", "numpy"]
# ///
"""Figures I.11 and I.12: greenhouse-gas emissions per person against income
and against energy use. Input: data-refresh/ghg-scatter.csv from
`mill Refresh.scala ghgScatter`. Third argument picks the x-axis."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np
from matplotlib.ticker import FixedLocator, FuncFormatter, NullLocator, NullFormatter

INK, MUTED = "#161d1b", "#8a8a85"
HIGH, LOW = "#4a3aa7", "#1baf7a"
KIND = sys.argv[3] if len(sys.argv) > 3 else "gdp"
# MacKay's own figure I.10, from DEFRA: grammes of CO2 per kWh of chemical energy.
COAL_G, GAS_G = 300.0, 190.0
# UNDP's threshold for "high human development" in the 2007 report MacKay used.
HDI_HIGH = 0.8

d = duckdb.sql(f"""
    SELECT country, ghg_t, gdp, kwh_d, hdi FROM read_csv_auto('{sys.argv[1]}')
""").fetchnumpy()
x = d["gdp"].astype(float) if KIND == "gdp" else d["kwh_d"].astype(float)
y = d["ghg_t"].astype(float)
hdi = d["hdi"].astype(float)
names = [str(c) for c in d["country"]]
hi = hdi >= HDI_HIGH

fig, ax = plt.subplots(figsize=(9.2, 6.2))
# Squares for high human development, circles below it - MacKay's convention.
ax.scatter(x[hi], y[hi], marker="s", s=26, facecolor="none", edgecolor=HIGH,
           linewidth=1.1, zorder=4, label=f"HDI ≥ {HDI_HIGH} (“high human development”)")
ax.scatter(x[~hi], y[~hi], marker="o", s=26, facecolor="none", edgecolor=LOW,
           linewidth=1.1, zorder=4, label=f"HDI < {HDI_HIGH}")

if KIND == "energy":
    # If every kWh a person used came from one fuel, emissions would lie on
    # these lines. Countries below them are burning something cleaner.
    xs = np.array([0, max(x) * 1.05])
    for g, lab, ls in ((COAL_G, "all coal", (0, (6, 3))), (GAS_G, "all gas", (0, (2, 2)))):
        # g CO2 per kWh x kWh/day x 365 days, then grammes to tonnes: 10^6, not 10^3.
        ys = xs * g * 365.0 / 1e6
        ax.plot(xs, ys, color=MUTED, lw=1.3, ls=ls, zorder=2)
        # Label where the line exits the top of the axes, not at its far end.
        ytop = max(y) * 1.08
        xe = min(xs[-1], ytop / (g * 365.0 / 1e6))
        ax.annotate(lab, xy=(xe, xe * g * 365.0 / 1e6), xytext=(4, -9),
                    textcoords="offset points", ha="left", fontsize=9, color=MUTED)

LABEL = ["United Kingdom", "United States", "China", "India", "Germany",
         "France", "Sweden", "Qatar", "Norway", "Nigeria"]
marked = [(c, xv, yv) for c, xv, yv in zip(names, x, y) if c in LABEL]

if KIND == "gdp":
    ax.set_xscale("log")
    ax.xaxis.set_major_locator(FixedLocator([1000, 2000, 5000, 10000, 20000, 50000, 100000]))
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"${v/1000:g}k"))
    ax.xaxis.set_minor_locator(NullLocator())
    ax.xaxis.set_minor_formatter(NullFormatter())
    ax.set_xlabel("GDP per person (international $ at PPP, 2021 prices, log scale)", fontsize=10.5)
else:
    ax.set_xlabel("energy per person (kWh/d)", fontsize=10.5)
    ax.set_xlim(0, max(x) * 1.05)
ax.set_ylim(0, max(y) * 1.08)
ax.set_ylabel("greenhouse-gas emissions per person (t CO$_2$e/y)", fontsize=10.5)
ax.grid(color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right"): ax.spines[s].set_visible(False)
for s in ("bottom", "left"): ax.spines[s].set_color("#c9c9c4")
ax.legend(loc="upper left", frameon=False, fontsize=9)

noun = "income" if KIND == "gdp" else "energy use"
ax.set_title(f"Greenhouse-gas emissions per person against {noun}, 2023",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
tail = ("Each marker is a country. The cloud rises with income, but at any one income it spreads enormously: among the\n"
        "thirty-three countries earning $40–70k a head, emissions run from 3.7 to 40 tonnes, a factor of eleven. How much\n"
        "a country emits is not settled by how rich it is. Britain, at 5.6 t, is the eighth lowest of that group; China, on\n"
        "two fifths of Britain's income, emits nearly twice as much."
        if KIND == "gdp" else
        f"The dashed lines are what a person's emissions would be if every kWh they used came from one fuel, at the\n"
        f"{COAL_G:.0f} and {GAS_G:.0f} g CO$_2$/kWh of figure I.10. Countries below the lower line are running on something\n"
        "other than fossil fuel; countries above the upper one are emitting more than combustion alone would explain,\n"
        "which is land use and agriculture entering the total.")
ax.annotate(tail, xy=(0, -0.135), xycoords="axes fraction", va="top",
            fontsize=9.3, color=MUTED)
# Labels last: several of the named countries sit within a few pixels of each
# other, so nudge them apart once the axes are final.
fig.canvas.draw()
placed = sorted(((ax.transData.transform((xv, yv))[1], c, xv, yv) for c, xv, yv in marked),
                reverse=True)
want = [p[0] for p in placed]
for i in range(1, len(want)):
    want[i] = min(want[i], want[i - 1] - 13.0)
for target, (orig, c, xv, yv) in zip(want, placed):
    ax.annotate(c, xy=(xv, yv), xytext=(7, (target - orig) * 72.0 / fig.dpi),
                textcoords="offset points", va="center", fontsize=9, color=INK,
                zorder=6, fontweight="bold" if c == "United Kingdom" else "normal")

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

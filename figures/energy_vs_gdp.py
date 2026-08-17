#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib"]
# ///
"""Figures 18.11 and 18.12: energy per person against GDP per person, as paths.
Input: data-refresh/energy-vs-gdp.csv or fossil-vs-gdp.csv from
`mill Refresh.scala energyVsGdp`. Third argument sets the title wording."""
import sys, duckdb, matplotlib.pyplot as plt
from matplotlib.ticker import FixedLocator, FuncFormatter, NullLocator, NullFormatter

INK, MUTED, UK = "#161d1b", "#8a8a85", "#1baf7a"
OTHER = {"Germany": "#4a3aa7", "France": "#2a78d6", "United States": "#eb6834",
         "Japan": "#7a5c3e", "China": "#eda100"}
KIND = sys.argv[3] if len(sys.argv) > 3 else "energy"

d = duckdb.sql(f"""
    SELECT country, year, kwh_d, gdp FROM read_csv_auto('{sys.argv[1]}') ORDER BY country, year
""").fetchnumpy()

series = {}
for c, y, k, g in zip(d["country"], d["year"], d["kwh_d"], d["gdp"]):
    series.setdefault(c, []).append((int(y), float(k), float(g)))

fig, ax = plt.subplots(figsize=(9.2, 6.0))
ends = []

def label(c, x, y, col, dx, dy, ha):
    ax.annotate(c, xy=(x, y), xytext=(dx, dy), textcoords="offset points",
                ha=ha, va="center", fontsize=9.5, color=col,
                fontweight="bold" if c == "United Kingdom" else "normal", zorder=6)

for c, pts in series.items():
    col = UK if c == "United Kingdom" else OTHER.get(c, MUTED)
    lw = 2.6 if c == "United Kingdom" else 1.6
    gs = [p[2] for p in pts]; ks = [p[1] for p in pts]
    ax.plot(gs, ks, color=col, lw=lw, zorder=4 if c == "United Kingdom" else 3,
            solid_capstyle="round")
    # Open circle where the path starts, filled where it ends.
    ax.plot(gs[0], ks[0], marker="o", ms=6, mfc="white", mec=col, mew=1.6, zorder=5)
    ax.plot(gs[-1], ks[-1], marker="o", ms=7, color=col, zorder=5)
    # Every path ends in the same year, so the label carries the country alone.
    # Placement is deferred: the two variants order the countries differently,
    # so the vertical nudges have to be worked out from the drawn axes.
    ends.append((c, gs[-1], ks[-1], col))
    if c == "United Kingdom":
        ax.annotate(f"{pts[0][0]}", xy=(gs[0], ks[0]), xytext=(-9, 5),
                    textcoords="offset points", ha="right", fontsize=9, color=col)

ax.set_xscale("log")
# Set both locators and formatters: on a log axis the default minor formatter
# also draws labels, which collide with the major ones.
ax.xaxis.set_major_locator(FixedLocator([2000, 5000, 10000, 20000, 50000]))
ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"${v/1000:g}k"))
ax.xaxis.set_minor_locator(NullLocator())
ax.xaxis.set_minor_formatter(NullFormatter())
ax.set_xlabel("GDP per person (international $, log scale)", fontsize=10.5)
ax.set_ylabel(f"{'fossil ' if KIND == 'fossil' else ''}energy per person (kWh/d)".strip(),
              fontsize=10.5)
ax.grid(color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right"): ax.spines[s].set_visible(False)
for s in ("bottom", "left"): ax.spines[s].set_color("#c9c9c4")

# Labels last: push any pair closer than MIN_SEP apart, working in display
# pixels so the log x-axis and linear y-axis are both handled honestly.
MIN_SEP = 14.0
fig.canvas.draw()
# Japan ends leftmost of the European cluster, so a right-hand label would run
# across it; it is the one country labelled on the other side.
LEFT = {"Japan"}
placed = sorted(((ax.transData.transform((x, y))[1], c, x, y, col)
                 for c, x, y, col in ends if c not in LEFT), reverse=True)
want = [p[0] for p in placed]
for i in range(1, len(want)):
    want[i] = min(want[i], want[i - 1] - MIN_SEP)
for target, (orig, c, x, y, col) in zip(want, placed):
    label(c, x, y, col, 11, (target - orig) * 72.0 / fig.dpi, "left")
for c, x, y, col in ends:
    if c in LEFT: label(c, x, y, col, -11, 0, "right")

noun = "Fossil energy" if KIND == "fossil" else "Energy"
ax.set_title(f"{noun} per person against income, 1990 onwards",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
tail = ("Hollow circles are 1990, filled circles 2025. Fossil energy falls further than total energy, because part of what\n"
        "replaced it was low-carbon electricity: Britain's fossil supply drops from 110 kWh/d to 54, against 120 to 69 for\n"
        "energy of every kind in figure 18.11. China's fossil use still rises, though by less than its total."
        if KIND == "fossil" else
        "Hollow circles are 1990, filled circles 2025. The rich countries move right and down: richer, on less energy.\n"
        "Britain goes from about 120 kWh/d to about 69 while its income rises by three fifths — which is the decoupling\n"
        "this chapter's balance sheet depends on. China moves right and up, which is what industrialising looks like.")
ax.annotate(tail + ("\nChapter 15 supplies the caveat these paths cannot show: a territorial figure also falls when a "
                    "factory closes and its\noutput is imported." if KIND != "fossil" else ""),
            xy=(0, -0.135), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

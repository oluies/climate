#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib", "numpy"]
# ///
"""Figure 20.9 remade: the carbon pollution of Europe's new cars.
MacKay's original is a histogram of models on sale in the UK in 2006, with a
second scale in kWh per 100 km at 240 g CO2 per kWh. This is the same picture
drawn from the EU registration record: every new car registered in the EU,
Norway and Iceland, by its type-approval CO2 figure.
Inputs: data-refresh/car-co2-distribution.csv and car-co2-annotations.csv,
both from `mill Refresh.scala carCo2`.
Usage: python car_co2.py <distribution.csv> <annotations.csv> <out.svg>"""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
ICE, PHEV, ZERO = "#8a8a85", "#e1a731", "#1baf7a"
REF, ONROAD = "#4a3aa7", "#e34948"

# MacKay's own conversion on the second scale of figure 20.9, and the average
# new car he quotes for the UK in the text beside it.
G_PER_KWH = 240.0
MACKAY_AVG = 168.0
XMAX = 300  # 99.4% of 2025 registrations; the caption says what is off the right

dist = sys.argv[1]
q = f"""SELECT year, bin, klass, SUM(n) AS n FROM read_csv_auto('{dist}')
        GROUP BY year, bin, klass"""
rows = duckdb.sql(q).fetchall()
ann = {r[0]: r[1] for r in
       duckdb.sql(f"SELECT name, value FROM read_csv_auto('{sys.argv[2]}')").fetchall()}

years = sorted({r[0] for r in rows})
bins = np.arange(0, XMAX + 5, 5)
total = {y: sum(r[3] for r in rows if r[0] == y) for y in years}


def series(year, klass=None):
    """Percent of that year's registrations in each bin, clipped to the axis."""
    out = np.zeros(len(bins))
    for y, b, k, n in rows:
        if y != year or (klass is not None and k != klass):
            continue
        i = min(b, XMAX) // 5
        out[i] += n
    return 100 * out / total[year]


fig, ax = plt.subplots(figsize=(10.0, 5.6))

# 2025, stacked by what the car is: the zero column is one powertrain class,
# which is the whole point of drawing it this way rather than as one colour.
bottom = np.zeros(len(bins))
for klass, colour, label in (("ice", ICE, "petrol, diesel and other combustion"),
                             ("phev", PHEV, "plug-in hybrid"),
                             ("zero", ZERO, "battery-electric and fuel-cell")):
    v = series(2025, klass)
    ax.bar(bins, v, width=4.4, bottom=bottom, color=colour, label=label, zorder=3)
    bottom += v

# 2020 for the same measurement, as an outline, to show which way it is moving.
ax.step(bins, series(2020), where="mid", color=INK, lw=1.2, alpha=0.55,
        zorder=4, label="all cars, 2020")

ymax = 21.5
# Three averages, labelled along their own lines so that the crowded left-hand
# end of the chart stays readable. What each one means is in the caption.
for x, colour, text in (
        (ann["official_mean"], INK, f"official, 2025: {ann['official_mean']:.0f}"),
        (ann["onroad_mean"], ONROAD, f"on the road: {ann['onroad_mean']:.0f}"),
        (MACKAY_AVG, REF, f"MacKay, 2006: {MACKAY_AVG:.0f}")):
    ax.axvline(x, color=colour, lw=1.3, ls=(0, (5, 3)), zorder=5)
    ax.annotate(text, xy=(x + 2.5, 14.0), rotation=90, ha="left", va="bottom",
                fontsize=8.6, color=colour, zorder=6)

# The zero column, and where its cars actually sit once the grid is counted.
zero_h = series(2025, "zero")[0]
ax.annotate(f"{zero_h:.0f}% of all new cars enter\nthe average here, as zero",
            xy=(2.5, 18.9), xytext=(14, 20.4), fontsize=9, color=INK, zorder=6,
            ha="left", va="top",
            arrowprops=dict(arrowstyle="->", color=INK, lw=1.0,
                            connectionstyle="arc3,rad=0.25"))
ax.annotate("", xy=(ann["bev_grid"], 12.5), xytext=(1, 12.5),
            arrowprops=dict(arrowstyle="->", color=ZERO, lw=1.5), zorder=6)
ax.annotate(f"about {ann['bev_grid']:.0f} g/km on the grid",
            xy=(ann["bev_grid"] + 3, 12.2), fontsize=8.6, color=ZERO, zorder=6)
ax.annotate("", xy=(ann["phev_real"], 10.6), xytext=(32, 10.6),
            arrowprops=dict(arrowstyle="->", color=PHEV, lw=1.5), zorder=6)
ax.annotate(f"typed at 32, metered at {ann['phev_real']:.0f}",
            xy=(36, 10.9), fontsize=8.6, color=PHEV, zorder=6)

ax.set_xlim(-4, XMAX)
ax.set_ylim(0, ymax)
ax.set_xlabel("grams CO$_2$ per kilometre, as the type-approval test reports it", fontsize=10.5)
ax.set_ylabel("per cent of new cars registered", fontsize=10.5)
ax.grid(axis="y", color=GRID, lw=0.9, zorder=0)
for s in ("top", "right"):
    ax.spines[s].set_visible(False)
for s in ("bottom", "left"):
    ax.spines[s].set_color("#c9c9c4")
ax.legend(frameon=False, fontsize=9, loc="upper right", bbox_to_anchor=(1.0, 1.0))

# MacKay's second horizontal scale, kept because his argument is in kWh.
top = ax.secondary_xaxis("top", functions=(lambda g: g / (G_PER_KWH / 100),
                                           lambda k: k * (G_PER_KWH / 100)))
top.set_xlabel("kWh per 100 km of chemical energy, at MacKay's 240 g CO$_2$ per kWh "
               "— meaningless for the two left-hand colours", fontsize=9.5, color=MUTED)
top.tick_params(colors=MUTED)
top.spines["top"].set_color("#c9c9c4")

ax.set_title("What Europe's new cars are rated at, and what they emit",
             loc="left", fontsize=12.5, fontweight="bold", pad=34)
ax.annotate(
    "Every new car registered in the EU, Norway and Iceland in 2025 — 10.8 million of them — by its type-approval CO$_2$ figure, with 2020\n"
    "outlined behind it. MacKay counted models on sale rather than cars sold, and his figures are on the older NEDC test, so the 168 is\n"
    "not strictly comparable with the rest of the chart; it is drawn to show where the middle of his histogram sat. The left-hand end of\n"
    "this one is an accounting convention rather than a measurement: a battery-electric car is entered as zero whatever charges it, and a\n"
    "plug-in hybrid is credited with electric running that on-board meters show it does not do. Correcting only those two, and the known\n"
    "gap between the test and the road, moves the average from 97 to 143 g/km. Above 300 g/km, off the right-hand edge, lie 0.2% of them.",
    xy=(0, -0.155), xycoords="axes fraction", va="top", fontsize=9.0, color=MUTED)

fig.savefig(sys.argv[3], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[3])

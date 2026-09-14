#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure 4.6a: the distribution behind figure 4.1a, MacKay's histogram pair
redrawn for both years.

Left, one bar per day of the year; right, one per half-hour. The cube of the
speed is what a turbine sees, so the mass of both distributions sitting below
6 m/s is the chapter's argument in one picture.

Input: data-refresh/cambridge-wind.csv from `mill Refresh.scala
chapter4CambridgeWind`."""
import sys, csv, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID, LIMIT = "#161d1b", "#8a8a85", "#ededea", "#8a8a85"
COLOUR = {0: "#1f6f9c", 1: "#bf4433"}       # older year, newer year

rows = list(csv.DictReader(open(sys.argv[1])))
years = sorted({r["year"] for r in rows})
half = collections.defaultdict(list)
days = {y: collections.defaultdict(list) for y in years}
for r in rows:
    speed = float(r["speed_ms"])
    half[r["year"]].append(speed)
    days[r["year"]][r["timestamp"][:10]].append(speed)
daily = {y: [sum(v) / len(v) for v in d.values() if len(v) >= 36]
         for y, d in days.items()}

fig, axes = plt.subplots(1, 2, figsize=(9.2, 4.2))
for ax, (data, title, step) in zip(axes, (
        (daily, "Daily averages", 0.5), (half, "Half-hourly readings", 0.5))):
    for i, year in enumerate(years):
        values = data[year]
        bins = [b * step for b in range(0, int(16 / step) + 1)]
        ax.hist(values, bins=bins, histtype="step", lw=1.6, density=True,
                color=COLOUR[i], label=f"{year}  (mean {sum(values)/len(values):.1f} m/s)")
    ax.axvline(6, color=LIMIT, lw=1.0, ls=(0, (5, 3)))
    ax.set_title(title, loc="left", fontsize=11, color=INK)
    ax.set_xlabel("Wind speed, m/s", fontsize=10)
    ax.set_xlim(0, 14)
    ax.grid(axis="y", color=GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ("top", "right", "left"):
        ax.spines[s].set_visible(False)
    ax.spines["bottom"].set_color("#c9c9c4")
    ax.tick_params(length=0, labelsize=9.5)
    ax.set_yticklabels([])
axes[0].set_ylabel("Share of the year", fontsize=10)
axes[0].legend(frameon=False, fontsize=9.5, loc="upper right")
axes[1].text(6.25, ax.get_ylim()[1] * 0.92, "6 m/s", fontsize=9, color=LIMIT)
fig.suptitle("Cambridge wind speed: where the year actually sits",
             x=0.125, ha="left", fontsize=13, fontweight="bold", color=INK)
axes[0].annotate("Both panels are shares, so the two years can be compared despite different counts. The spike at zero in the later\n"
                 "year is the anemometer as much as the weather: it reads calm 18% of the time against 8% in 2006.",
                 xy=(0, -0.30), xycoords="axes fraction", va="top",
                 fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2], "|",
      ", ".join(f"{y}: {sum(1 for v in daily[y] if v >= 6)} days at or above 6 m/s"
                for y in years))

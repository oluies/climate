#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure 4.1a: MacKay's Cambridge wind-speed year, and the same rooftop in the
last year its anemometer worked.

Same station, same address, same half-hourly file. The 6 m/s line is the
chapter's own assumption, and the point of the figure is how little of either
year is above it.

Input: data-refresh/cambridge-wind.csv from `mill Refresh.scala
chapter4CambridgeWind`."""
import sys, csv, collections, datetime as dt, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
HALF, DAILY, LIMIT = "#9ec4dd", "#bf4433", "#8a8a85"

rows = list(csv.DictReader(open(sys.argv[1])))
years = sorted({r["year"] for r in rows})
half = {y: [] for y in years}
days = {y: collections.defaultdict(list) for y in years}
for r in rows:
    t = dt.datetime.strptime(r["timestamp"], "%Y-%m-%d %H:%M:%S")
    speed = float(r["speed_ms"])
    half[r["year"]].append((t, speed))
    days[r["year"]][t.date()].append(speed)

fig, axes = plt.subplots(len(years), 1, figsize=(9.0, 5.8), sharey=True)
for ax, year in zip(axes, years):
    h = sorted(half[year])
    # Day-of-year on the x axis, so the two panels line up month for month.
    doy = lambda d: d.timetuple().tm_yday + (d.hour * 60 + d.minute) / 1440
    ax.plot([doy(t) for t, _ in h], [s for _, s in h],
            color=HALF, lw=0.4, zorder=2, label="half-hourly")
    # A day needs most of its readings before its mean means anything.
    means = {d: sum(v) / len(v) for d, v in days[year].items() if len(v) >= 36}
    ax.plot([doy(dt.datetime.combine(d, dt.time(12))) for d in sorted(means)],
            [means[d] for d in sorted(means)],
            color=DAILY, lw=1.1, zorder=3, label="daily mean")
    ax.axhline(6, color=LIMIT, lw=1.0, ls=(0, (5, 3)), zorder=4)
    over = sum(1 for v in means.values() if v >= 6)
    ax.text(0.995, 0.93, f"{year}: mean {sum(means.values()) / len(means):.1f} m/s, "
            f"{over} days at or above 6",
            transform=ax.transAxes, ha="right", va="top", fontsize=9.5, color=INK)
    ax.set_xlim(1, 366)
    ax.set_xticks([1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335])
    ax.set_xticklabels(list("JFMAMJJASOND"), fontsize=9.5)
    ax.set_ylim(0, 16)
    ax.grid(axis="y", color=GRID, lw=0.8, zorder=0)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("bottom", "left"):
        ax.spines[s].set_color("#c9c9c4")
    ax.tick_params(length=0, labelsize=9.5)

# September is the quiet end of the later year, so the label sits there
# rather than on top of the data.
axes[-1].text(247, 6.4, "6 m/s, the chapter's assumed wind speed",
              fontsize=9, color=LIMIT)
axes[0].legend(frameon=False, fontsize=9.5, loc="upper left", ncol=2,
               bbox_to_anchor=(0, 1.20), handlelength=2.2)
axes[len(years) // 2].set_ylabel("Wind speed, m/s", fontsize=10.5)
axes[0].set_title("The same Cambridge rooftop, seventeen years apart",
                  loc="left", fontsize=13, fontweight="bold", pad=26, color=INK)
axes[-1].annotate("Computer Laboratory rooftop station, about 10 m up; wind at 50 m is usually about 25% higher. 2023 is\n"
                  "the last year the anemometer worked, and it reads calm 18% of the time against 8% in 2006, so its lower\n"
                  "mean is partly the instrument. The bias runs downwards, and the chapter's 6 m/s is an upper bound.",
                  xy=(0, -0.42), xycoords="axes fraction", va="top",
                  fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2], "|",
      ", ".join(f"{y} {sum(s for _, s in half[y]) / len(half[y]):.2f} m/s" for y in years))

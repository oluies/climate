#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure 4.2a: the Cairngorm summit in MacKay's six months of 2006, and in the
same six months of 2026.

Drawn to the same scale as figure 4.1a on purpose. The Cambridge rooftop spends
its year under the 6 m/s line; this station spends most of its year over it,
and that contrast is the chapter's argument about where to put a windmill.

Input: data-refresh/cairngorm-wind.csv from `mill Refresh.scala
chapter4Cairngorm`."""
import sys, csv, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
HALF, DAILY, LIMIT = "#9ec4dd", "#bf4433", "#8a8a85"

rows = [r for r in csv.DictReader(open(sys.argv[1])) if int(r["day"]) <= 182]
years = sorted({r["year"] for r in rows})
half = collections.defaultdict(list)
days = {y: collections.defaultdict(list) for y in years}
for r in rows:
    day, time, speed = int(r["day"]), int(r["time"]), float(r["speed_ms"])
    half[r["year"]].append((day + (time // 100 * 60 + time % 100) / 1440, speed))
    days[r["year"]][day].append(speed)

fig, axes = plt.subplots(len(years), 1, figsize=(9.0, 5.8), sharey=True)
for ax, year in zip(axes, years):
    h = sorted(half[year])
    ax.plot([x for x, _ in h], [s for _, s in h], color=HALF, lw=0.4, zorder=2,
            label="half-hourly")
    means = {d: sum(v) / len(v) for d, v in days[year].items() if len(v) >= 36}
    ax.plot([d + 0.5 for d in sorted(means)], [means[d] for d in sorted(means)],
            color=DAILY, lw=1.1, zorder=3, label="daily mean")
    ax.axhline(6, color=LIMIT, lw=1.0, ls=(0, (5, 3)), zorder=4)
    over = sum(1 for v in means.values() if v >= 6)
    ax.text(0.995, 0.93, f"{year}: mean {sum(means.values()) / len(means):.1f} m/s, "
            f"{over} of {len(means)} days at or above 6",
            transform=ax.transAxes, ha="right", va="top", fontsize=9.5, color=INK)
    ax.set_xlim(1, 182)
    ax.set_xticks([1, 32, 60, 91, 121, 152])
    ax.set_xticklabels(["January", "February", "March", "April", "May", "June"],
                       fontsize=9.5)
    ax.set_ylim(0, 32)
    ax.grid(axis="y", color=GRID, lw=0.8, zorder=0)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("bottom", "left"):
        ax.spines[s].set_color("#c9c9c4")
    ax.tick_params(length=0, labelsize=9.5)

# Early January 2006 is a dead anemometer, so the label goes in that gap.
axes[0].text(3, 7.2, "6 m/s", fontsize=9, color=LIMIT)
axes[0].legend(frameon=False, fontsize=9.5, loc="upper left", ncol=2,
               bbox_to_anchor=(0, 1.20), handlelength=2.2)
axes[len(years) // 2].set_ylabel("Wind speed, m/s", fontsize=10.5)
axes[0].set_title("Cairngorm summit, the six months MacKay had and the same six months now",
                  loc="left", fontsize=13, fontweight="bold", pad=26, color=INK)
axes[-1].annotate("Heriot-Watt's station at 1245 m. His six months were not a choice of style: the 2006 anemometer failed in July\n"
                  "and again at the end of November, so January to June is what that year has. Summit years differ enormously and\n"
                  "several archive years are instrument failures, so no trend is claimed here — only the contrast with Cambridge.",
                  xy=(0, -0.42), xycoords="axes fraction", va="top",
                  fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
print("wrote", sys.argv[2], "|", ", ".join(
    f"{y} mean {sum(s for _, s in half[y]) / len(half[y]):.2f} m/s" for y in years))

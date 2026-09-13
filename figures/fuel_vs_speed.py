#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure A.12a: measured fuel consumption against steady speed, from four
decades of dynamometer studies, against the square law MacKay's figure A.12
tests it against.

His figure has two cars on it and one of its sources has gone offline. This
one has 111 cars across four studies plus a model of three 2016 vehicles, all
from ORNL's Transportation Energy Data Book, edition 40, tables 4.34 and 4.33.

Input: data-refresh/fuel-vs-speed.csv from `mill Refresh.scala
chapterAFuelVsSpeed`."""
import sys, csv, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
COLOUR = {"1973 study (13 cars)": "#8a7b5f", "1984 study (15 cars)": "#b48a3c",
          "1997 study (9 cars)": "#2f7d4f", "2012 study (74 cars)": "#1f6f9c",
          "2016 model: midsize car": "#bf4433", "2016 model: large SUV": "#d98b7f",
          "2016 model: hybrid car": "#7a3b8f"}
DASHED = {"2016 model: midsize car", "2016 model: large SUV", "2016 model: hybrid car"}

series = collections.defaultdict(list)
for r in csv.DictReader(open(sys.argv[1])):
    series[r["series"]].append((float(r["kmh"]), float(r["kwh_per_100km"])))

fig, ax = plt.subplots(figsize=(8.8, 5.0))
for label, points in series.items():
    points.sort()
    ax.plot([p[0] for p in points], [p[1] for p in points],
            color=COLOUR[label], lw=1.7, marker="o", ms=3.2,
            ls=(0, (4, 2)) if label in DASHED else "-", label=label)

# The square law, anchored on the 2012 study at 80 km/h: what MacKay's figure
# A.12 draws to show that real cars do not follow it.
anchor = dict(series["2012 study (74 cars)"])[80.5]
xs = [v for v in range(40, 135, 5)]
ax.plot(xs, [anchor * (v / 80.5) ** 2 for v in xs], color=MUTED, lw=1.2,
        ls=(0, (1.5, 1.5)), label="a square law through the 2012 study at 80 km/h")

ax.set_xlim(20, 135)
ax.set_ylim(0, 160)
ax.set_xlabel("Steady speed, km/h", fontsize=10.5)
ax.set_ylabel("Fuel consumption, kWh per 100 km", fontsize=10.5)
ax.grid(color=GRID, lw=0.8)
ax.set_axisbelow(True)
for s in ("top", "right"):
    ax.spines[s].set_visible(False)
for s in ("bottom", "left"):
    ax.spines[s].set_color("#c9c9c4")
ax.tick_params(length=0, labelsize=9.5)
ax.legend(frameon=False, fontsize=9, loc="upper left", handlelength=2.2)
ax.set_title("Real cars do not burn fuel as the square of their speed",
             loc="left", fontsize=13, fontweight="bold", pad=12, color=INK)
ax.annotate("Dynamometer measurements collected in ORNL's Transportation Energy Data Book, edition 40, table 4.34, converted\n"
            "at 33.7 kWh per US gallon. Dashed lines are Argonne's Autonomie model for three model-year-2016 vehicles, table 4.33.\n"
            "Every curve has a minimum, and above it none of them rises anywhere near as steeply as the dotted square law: from 80\n"
            "to 113 km/h the 74-car study rises 32%, where a square law demands 96%.",
            xy=(0, -0.17), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
print("wrote", sys.argv[2], "|", len(series), "series")

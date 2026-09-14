#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figures A.9, A.10, A.11 and A.14 redrawn — MacKay's own curves, nothing added.

The originals are low-resolution bitmaps from the 2008 EPUB and are hard to
read a value off. These are the same four curves computed from the assumptions
printed in his own captions, so his text can still be checked against them.
The 2026 additions live in figures A.9a and A.14a and are deliberately not
here: this is his figure, redrawn.

Usage: mackay_models.py <steady-speed.csv> <ev-range.csv> <output directory>,
writing fig-a9-car.svg, fig-a10-bike.svg, fig-a11-train.svg and
fig-a14-range.svg. Both inputs come from `mill Refresh.scala`, tasks
chapterASteadySpeed and chapterAElectricRange."""
import sys, csv, collections, pathlib, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID, LINE = "#161d1b", "#8a8a85", "#ededea", "#1f6f9c"

def frame(ax, xlabel, ylabel):
    ax.set_xlabel(xlabel, fontsize=10)
    ax.set_ylabel(ylabel, fontsize=10)
    ax.grid(color=GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("bottom", "left"):
        ax.spines[s].set_color("#c9c9c4")
    ax.tick_params(length=0, labelsize=9.5)

steady = collections.defaultdict(list)
for r in csv.DictReader(open(sys.argv[1])):
    steady[r["label"]].append((float(r["kmh"]), float(r["kwh_per_100km"])))
ranges = collections.defaultdict(list)
for r in csv.DictReader(open(sys.argv[2])):
    ranges[r["series"]].append((float(r["batt_kg"]), float(r["range_km"]),
                                float(r["kwh_per_100km"])))
out = pathlib.Path(sys.argv[3])

# A.9, A.10, A.11: one curve each, at a steady speed.
for name, label, ylabel, note in (
        ("fig-a9-car.svg", "Petrol car (MacKay's)", "kWh per 100 km",
         "Engine efficiency 0.25, drag-area 1 m², 1000 kg, rolling resistance 0.01."),
        ("fig-a10-bike.svg", "Bicycle (MacKay's)", "kWh per 100 km",
         "Rider efficiency 0.25, drag-area 0.75 m², 90 kg, rolling resistance 0.005."),
        ("fig-a11-train.svg", "Train full (584 seats)", "kWh per 100 passenger-km",
         "Engine efficiency 0.90, drag-area 11 m², 400 tonnes, rolling resistance 0.002, 584 passengers.")):
    points = sorted(steady[label])
    fig, ax = plt.subplots(figsize=(5.2, 3.6))
    ax.plot([p[0] for p in points], [p[1] for p in points], color=LINE, lw=1.9)
    frame(ax, "Steady speed, km/h", ylabel)
    ax.set_xlim(0, points[-1][0])
    ax.set_ylim(0, None)
    ax.annotate(note, xy=(0, -0.26), xycoords="axes fraction", va="top",
                fontsize=8.5, color=MUTED)
    fig.savefig(out / name, format="svg", bbox_inches="tight", metadata={"Date": None})
    plt.close(fig)
    print("wrote", out / name)

# A.14: his two battery technologies, and nothing later.
fig, ax = plt.subplots(figsize=(5.8, 4.0))
for label, colour in (("Lead-acid 40 Wh/kg", "#8a7b5f"),
                      ("Lithium 2008 120 Wh/kg", LINE)):
    points = sorted(ranges[label])
    ax.plot([p[1] for p in points], [p[2] for p in points], color=colour, lw=1.9,
            label=label)
    for kg, km, cost in points:
        if kg in (100, 250, 500, 1000):
            ax.plot([km], [cost], "o", color=colour, ms=4)
            ax.annotate(f"{kg:.0f} kg", (km, cost), textcoords="offset points",
                        xytext=(0, 7), ha="center", fontsize=8, color=colour)
frame(ax, "Range on one charge, km", "Transport cost at the wall socket, kWh per 100 km")
ax.set_xlim(0, 700)
ax.set_ylim(8, 26)
ax.legend(frameon=False, fontsize=9, loc="upper left")
ax.annotate("740 kg of car and occupants before any battery, 50 km/h, drag-area 0.8 m², rolling\n"
            "resistance 0.01, 500 m between stops, half the braking energy recovered, 85%\n"
            "drive efficiency, 85% charging. Dots are pack masses.",
            xy=(0, -0.20), xycoords="axes fraction", va="top",
            fontsize=8.5, color=MUTED)
fig.savefig(out / "fig-a14-range.svg", format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", out / "fig-a14-range.svg")

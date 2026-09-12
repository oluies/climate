#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure A.9a: MacKay's three steady-speed curves — car, bicycle, train — with
2025 vehicles drawn on the same axes.

One panel per figure of his: A.9, A.10 and A.11. Each curve is the same two
terms, air resistance and rolling resistance, divided by the efficiency of
whatever turns the wheels. Nothing here is measured; everything is his model
with different numbers in it, which is the point.

Input: data-refresh/steady-speed.csv from `mill Refresh.scala
chapterASteadySpeed`."""
import sys, csv, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
# His vehicle is the blue line in every panel; what has been added is coloured.
COLOUR = {
    "Petrol car (MacKay's)": "#1f6f9c", "Electric car 2025": "#2f7d4f",
    "Electric SUV 2025": "#bf4433", "Bicycle (MacKay's)": "#1f6f9c",
    "Electric bicycle": "#2f7d4f", "Train full (584 seats)": "#1f6f9c",
    "Train at 40% of seats": "#bf4433",
}
PANEL = (("car", "A.9  Car", "kWh per 100 km"),
         ("bike", "A.10  Bicycle", "kWh per 100 km"),
         ("train", "A.11  Train", "kWh per 100 passenger-km"))

series = collections.defaultdict(lambda: collections.defaultdict(list))
for r in csv.DictReader(open(sys.argv[1])):
    series[r["panel"]][r["label"]].append((float(r["kmh"]), float(r["kwh_per_100km"])))

fig, axes = plt.subplots(1, 3, figsize=(10.4, 4.1))
for ax, (panel, title, ylabel) in zip(axes, PANEL):
    for label, points in series[panel].items():
        points.sort()
        ax.plot([p[0] for p in points], [p[1] for p in points],
                color=COLOUR[label], lw=1.8, label=label)
    ax.set_title(title, loc="left", fontsize=11, color=INK)
    ax.set_xlabel("Steady speed, km/h", fontsize=9.5)
    ax.set_ylabel(ylabel, fontsize=9.5)
    ax.set_xlim(0, max(p[0] for pts in series[panel].values() for p in pts))
    ax.set_ylim(0, None)
    ax.grid(color=GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("bottom", "left"):
        ax.spines[s].set_color("#c9c9c4")
    ax.tick_params(length=0, labelsize=9)
    ax.legend(frameon=False, fontsize=8.5, loc="upper left", handlelength=1.8)

fig.suptitle("The same two terms, with 2025 vehicles in them",
             x=0.055, ha="left", fontsize=13, fontweight="bold", color=INK)
axes[0].annotate("Air resistance and rolling resistance at a steady speed, divided by the efficiency of the drive: MacKay's own model, with his\n"
                 "assumptions for the blue lines. Added: an electric car (drag-area 0.60 m², 1800 kg, 85% from battery to wheels) and an electric\n"
                 "sport-utility (0.90 m², 2400 kg); an electric bicycle (his bicycle plus 25 kg, a motor at 75% instead of a rider at 25%); and his\n"
                 "own train at 40% of its seats rather than all of them. The petrol car passes 78 kWh per 100 km at 110 km/h, which is the 80 this\n"
                 "book is built on.",
                 xy=(0, -0.30), xycoords="axes fraction", va="top",
                 fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
at110 = {l: dict(p)[110.0] for l, p in series["car"].items()}
print("wrote", sys.argv[2], "| at 110 km/h:",
      ", ".join(f"{l} {v:.1f}" for l, v in at110.items()))

#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure A.14a: MacKay's figure A.14 with the battery of 2025, and then with
the car of 2025.

Both axes and the whole model are his. Each curve is one battery technology,
traced out by putting more pack in the car: further right is more range,
further up is more energy per distance, because the pack has to be carried.
His two curves are the lower pair; the third is a 2025 pack in his 2008 car,
and the fourth is that pack in a car that weighs what cars now weigh.

Input: data-refresh/ev-range.csv from `mill Refresh.scala chapterAElectricRange`."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
STYLE = {                                   # colour, dash, label position
    "Lead-acid 40 Wh/kg":      ("#8a7b5f", (0, (5, 2))),
    "Lithium 2008 120 Wh/kg":  ("#1f6f9c", None),
    "Pack 2025 160 Wh/kg":     ("#2f7d4f", None),
    "Pack 2025 in a 2025 car":  ("#bf4433", (0, (1.6, 1.6))),
}
MARK = (100, 250, 500, 1000)                # pack masses worth a dot, in kg
LABELLED = ("Pack 2025 160 Wh/kg", "Pack 2025 in a 2025 car")

rows = list(csv.DictReader(open(sys.argv[1])))
series = {}
for r in rows:
    series.setdefault(r["series"], []).append(
        (float(r["batt_kg"]), float(r["range_km"]), float(r["kwh_per_100km"])))

fig, ax = plt.subplots(figsize=(8.6, 5.4))
for name, points in series.items():
    colour, dash = STYLE[name]
    points.sort()
    x = [p[1] for p in points]
    y = [p[2] for p in points]
    ax.plot(x, y, color=colour, lw=1.8, ls=dash or "-", zorder=3, label=name)
    for kg, km, cost in points:
        if kg in MARK:
            ax.plot([km], [cost], "o", color=colour, ms=4.5, zorder=4)
            # The dots are the whole point of the curve: each is a pack mass.
            # Only the two 2025 curves carry the labels; at 740 kg of car the
            # three older curves share a cost for a given pack and their
            # labels sat on top of each other.
            if name in LABELLED:
                ax.annotate(f"{kg:.0f} kg", (km, cost), textcoords="offset points",
                            xytext=(0, 8), ha="center", fontsize=8, color=colour)

# MacKay's own two anchors, so a reader can find his figure inside this one.
ax.annotate("MacKay's 2008 pair:\n500 kg of pack, 180 km or 540 km",
            xy=(180, 13.1), xytext=(300, 8.6), fontsize=9, color=MUTED,
            arrowprops=dict(arrowstyle="-", color=MUTED, lw=0.8,
                            connectionstyle="arc3,rad=-0.15"))

ax.set_xlim(0, 1210)
ax.set_ylim(8, 26)
ax.set_xlabel("Range on one charge, km", fontsize=10.5)
ax.set_ylabel("Transport cost at the wall socket, kWh per 100 km", fontsize=10.5)
ax.grid(color=GRID, lw=0.8, zorder=0)
ax.set_axisbelow(True)
for s in ("top", "right"):
    ax.spines[s].set_visible(False)
for s in ("bottom", "left"):
    ax.spines[s].set_color("#c9c9c4")
ax.tick_params(length=0, labelsize=9.5)
ax.legend(frameon=False, fontsize=9.5, loc="upper left", handlelength=2.4)
ax.set_title("The battery got better; then the car got heavier",
             loc="left", fontsize=13, fontweight="bold", pad=12, color=INK)
ax.annotate("MacKay's model and his assumptions throughout: 50 km/h, a drag-area of 0.8 m², rolling resistance 0.01,\n"
            "500 m between stops, half the braking energy recovered, 85% drive efficiency and 85% charging. The lower\n"
            "three curves carry his 740 kg of car and occupants; the dotted curve carries 1500 kg, which is what a car\n"
            "of this size now weighs without its pack. Every dot is a pack mass: 100, 250, 500 and 1000 kg.",
            xy=(0, -0.165), xycoords="axes fraction", va="top",
            fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
hundred = {n: next(p for p in ps if p[0] == 500) for n, ps in series.items()}
print("wrote", sys.argv[2], "| 500 kg of pack:",
      ", ".join(f"{n} {p[1]:.0f} km" for n, p in hundred.items()))

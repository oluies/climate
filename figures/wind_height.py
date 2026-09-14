#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure B.7a: MacKay's figure B.7 redrawn — wind speed and wind power
against height, under the two standard shear formulas his chapter prints.

The original is a 219-pixel bitmap. These are the same two curves, computed.

Input: data-refresh/wind-height.csv from `mill Refresh.scala
appendixBCModelCurves`."""
import sys, csv, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
COLOUR = {"DWIA": "#1f6f9c", "NREL": "#bf4433"}
NAME = {"DWIA": "Danish Wind Industry Association (logarithmic)",
        "NREL": "National Renewable Energy Laboratory (power law)"}

series = collections.defaultdict(list)
for r in csv.DictReader(open(sys.argv[1])):
    series[r["model"]].append((float(r["height_m"]), float(r["speed_ms"]),
                               float(r["power_density_wm2"])))

fig, axes = plt.subplots(1, 2, figsize=(9.6, 4.2))
for ax, col, ylabel, title in ((axes[0], 1, "Wind speed, m/s", "Speed against height"),
                               (axes[1], 2, "Power density, W/m²", "Power against height")):
    for model, points in series.items():
        points.sort()
        ax.plot([p[0] for p in points], [p[col] for p in points],
                color=COLOUR[model], lw=1.9, label=NAME[model])
    ax.set_xscale("log")
    ax.set_xticks([10, 20, 50, 100, 200, 400])
    ax.get_xaxis().set_major_formatter(matplotlib.ticker.ScalarFormatter())
    ax.get_xaxis().set_minor_formatter(matplotlib.ticker.NullFormatter())
    ax.set_xlim(10, 400)
    ax.set_ylim(0, None)
    ax.set_xlabel("Height above the ground, m", fontsize=10)
    ax.set_ylabel(ylabel, fontsize=10)
    ax.set_title(title, loc="left", fontsize=11, color=INK)
    ax.grid(which="both", color=GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("bottom", "left"):
        ax.spines[s].set_color("#c9c9c4")
    ax.tick_params(length=0, labelsize=9.5)
axes[0].legend(frameon=False, fontsize=8.5, loc="lower right")
fig.suptitle("Taller windmills see faster wind, and much more power",
             x=0.06, ha="left", fontsize=13, fontweight="bold", color=INK)
axes[0].annotate("Both formulas are pinned to 6 m/s at 10 m, as MacKay's figure pins them, and the Danish one takes a roughness length\n"
                 "of 0.1 m and the American one an exponent of a seventh. Power density is ½ρv³ at 1.3 kg/m³. Doubling the height from\n"
                 "50 to 100 m buys about 11% more speed and 37% more power.",
                 xy=(0, -0.30), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

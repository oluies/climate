#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure A.13a: engine power against top speed for 665 car models on sale in
the 2020s, on the log-log axes of MacKay's figure A.13.

His caption states the law: power goes as the cube of top speed. On the
combustion cars in this sample it still does, at an exponent of 2.9. On
electric cars it does not, because their top speed is no longer set by the
power they have.

Input: data-refresh/power-vs-topspeed.csv from `mill Refresh.scala
chapterAPowerVsTopSpeed`."""
import sys, csv, math, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
COLOUR = {"Combustion": "#1f6f9c", "Hybrid": "#b48a3c", "Electric": "#bf4433"}

rows = list(csv.DictReader(open(sys.argv[1])))
by = collections.defaultdict(list)
for r in rows:
    by[r["kind"]].append((float(r["vmax_kmh"]), float(r["power_kw"]), r))

def exponent(points):
    xs = [math.log(v) for v, _, _ in points]
    ys = [math.log(k) for _, k, _ in points]
    mx, my = sum(xs) / len(xs), sum(ys) / len(ys)
    b = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sum((x - mx) ** 2 for x in xs)
    return b, math.exp(my - b * mx)

fig, ax = plt.subplots(figsize=(8.8, 5.4))
for kind in ("Hybrid", "Electric", "Combustion"):
    pts = by[kind]
    b, a = exponent(pts)
    ax.scatter([p[0] for p in pts], [p[1] for p in pts], s=16, alpha=0.55,
               color=COLOUR[kind], edgecolors="none",
               label=f"{kind} ({len(pts)} models, exponent {b:.1f})")
# The cube law, drawn through the combustion cars' own fit at 200 km/h.
b, a = exponent(by["Combustion"])
ref = a * 200 ** b
xs = [v for v in range(120, 345, 5)]
ax.plot(xs, [ref * (v / 200) ** 3 for v in xs], color=INK, lw=1.2, ls=(0, (5, 3)),
        label="power ∝ speed³, through the combustion fit at 200 km/h")

ax.set_xscale("log"); ax.set_yscale("log")
ax.set_xticks([125, 150, 175, 200, 250, 300])
ax.set_yticks([30, 50, 100, 200, 400])
# Plain numbers on both log axes, and nothing on the minor ticks, which
# otherwise label themselves as powers of ten.
for axis in (ax.get_xaxis(), ax.get_yaxis()):
    axis.set_major_formatter(matplotlib.ticker.ScalarFormatter())
    axis.set_minor_formatter(matplotlib.ticker.NullFormatter())
ax.set_xlim(120, 350)
ax.set_ylim(28, 600)
ax.set_xlabel("Declared maximum speed, km/h", fontsize=10.5)
ax.set_ylabel("Maximum net power, kW", fontsize=10.5)
ax.grid(which="both", color=GRID, lw=0.8)
ax.set_axisbelow(True)
for s in ("top", "right"):
    ax.spines[s].set_visible(False)
for s in ("bottom", "left"):
    ax.spines[s].set_color("#c9c9c4")
ax.tick_params(length=0, labelsize=9.5)
ax.legend(frameon=False, fontsize=9, loc="upper left")

wall = sum(1 for r in rows if float(r["vmax_kmh"]) == 250)
ax.axvline(250, color=MUTED, lw=1.0, ls=(0, (1.5, 1.5)))
ax.annotate(f"{wall} models declare exactly 250 km/h", xy=(250, 33), xytext=(150, 31),
            fontsize=9, color=MUTED,
            arrowprops=dict(arrowstyle="->", color=MUTED, lw=0.8))
ax.set_title("The cube law holds for engines, and not for motors",
             loc="left", fontsize=13, fontweight="bold", pad=12, color=INK)
ax.annotate("Every model with at least a hundred Dutch registrations since 2023, one point per make and model: type-approval maximum\n"
            "design speed against maximum net power, both from the RDW open vehicle register. Electric cars sit above the line because\n"
            "their top speed is set by gearing and cooling rather than by power — a 504 kW Tesla Model S declares 263 km/h, where a\n"
            "478 kW Porsche 911 Turbo S declares 327.",
            xy=(0, -0.17), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2], "| exponents:",
      ", ".join(f"{k} {exponent(v)[0]:.2f}" for k, v in by.items()))

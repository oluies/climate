#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure M.1: what 100 units of crude oil are worth by the time a car moves.

A Sankey diagram — the band's thickness is the energy still in the fuel, and
each branch peeling downwards is what the next stage costs. The three brackets
are the boundaries chapter M is about: draw the line at the wellhead and oil
returns ten for one, draw it at the petrol tank and it returns six, draw it
where the service actually happens and it returns three.

Drawn from the percentages in Hall, Balogh and Murphy (2009) rather than from
the well-known chart of them, which does not reproduce its own source's total.
The chapter's note says why.

Input: data-refresh/eroi-oil-cascade.csv from `mill Refresh.scala
chapterMOilCascade`."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.path import Path
from matplotlib.patches import PathPatch

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
FUEL, LOSS, EDGE = "#1f6f9c", "#d9b8b2", "#bf4433"
BOUNDARY = {"mine mouth": "EROI at the wellhead",
            "point of use": "EROI at the petrol tank",
            "extended": "EROI where the work happens"}

rows = list(csv.DictReader(open(sys.argv[1])))
for r in rows:
    r["cost_pct"], r["remaining_pct"] = float(r["cost_pct"]), float(r["remaining_pct"])

STEP = 1.0                       # one x unit per stage
fig, ax = plt.subplots(figsize=(10.6, 5.0))

# The band. Top edge stays at 100; the bottom rises as energy is taken out, so
# the thickness at any x is what is left.
xs, tops, bottoms = [0.0], [100.0], [0.0]
for i, r in enumerate(rows):
    a, b = (i + 0.35) * STEP, (i + 0.65) * STEP
    xs += [a, b]
    tops += [100.0, 100.0]
    bottoms += [bottoms[-1], 100.0 - r["remaining_pct"]]
xs.append(len(rows) * STEP)
tops.append(100.0)
bottoms.append(bottoms[-1])
ax.fill_between(xs, bottoms, tops, color=FUEL, zorder=3, lw=0)

# Each loss branch: a ribbon of the right thickness leaving the underside of
# the band and curving down to its own labelled row. The rows are spaced by
# what they hold plus a constant gap, so the 3-unit branch does not come to
# rest on top of the 17-unit one.
GAP, FEET, y = 9.0, [], -8.0
for r in rows:
    FEET.append(y)
    y -= r["cost_pct"] + GAP
for i, r in enumerate(rows):
    a, b = (i + 0.35) * STEP, (i + 0.65) * STEP
    top = 100.0 - r["remaining_pct"]           # the band's underside after this step
    bot = top - r["cost_pct"]                  # ...and before it: the slab removed
    foot = FEET[i]                             # where this branch comes to rest
    x_end, mid = len(rows) + 0.05, (b + len(rows)) / 2
    verts = [(b, top), (mid, top), (mid, foot), (x_end, foot),
             (x_end, foot - r["cost_pct"]),
             (mid, foot - r["cost_pct"]), (mid, bot), (a, bot), (a, top)]
    codes = [Path.MOVETO, Path.CURVE4, Path.CURVE4, Path.CURVE4, Path.LINETO,
             Path.CURVE4, Path.CURVE4, Path.CURVE4, Path.CLOSEPOLY]
    ax.add_patch(PathPatch(Path(verts, codes), facecolor=LOSS, edgecolor="none", zorder=2))
    ax.annotate(f"\u2212{r['cost_pct']:.0f}", (x_end + 0.12, foot - r["cost_pct"] / 2),
                va="center", fontsize=10.5, color=EDGE, fontweight="bold")
    ax.annotate(r["what"], (x_end + 0.62, foot - r["cost_pct"] / 2),
                va="center", fontsize=9.5, color=INK)
    # The stage, above the band, over the fuel it acts on.
    ax.annotate(r["stage"], ((a + b) / 2, 102.5), ha="center", fontsize=10.5, color=INK)

# What is left at each stage, written inside the band.
ax.annotate("100", (0.12, 50), ha="left", va="center", fontsize=13,
            color="white", fontweight="bold")
for i, r in enumerate(rows):
    x = (i + 0.82) * STEP if i < len(rows) - 1 else len(rows) - 0.12
    left = r["remaining_pct"]
    ax.annotate(f"{left:.0f}", (x, 100 - left / 2), ha="center", va="center",
                fontsize=12 if left > 40 else 11, color="white", fontweight="bold")

# The three boundaries, as brackets over the stages they contain.
last = {}
for i, r in enumerate(rows):
    last[r["boundary"]] = i
for n, (name, end) in enumerate(last.items()):
    y = 111 + 9.5 * n
    x0, x1 = 0.05, (end + 1) * STEP - 0.05
    ax.plot([x0, x0, x1, x1], [y - 3.0, y, y, y - 3.0], color=MUTED, lw=1.0, clip_on=False)
    # Left-aligned on the common left edge rather than centred: the labels are
    # wider than the innermost bracket, and stacking them flush reads as the
    # nesting it is — each boundary contains the one below it.
    ax.annotate(BOUNDARY[name], (x0 + 0.06, y + 1.8), ha="left", fontsize=9.5,
                color=MUTED, clip_on=False)

ax.set_xlim(0, len(rows) + 4.4)
ax.set_ylim(FEET[-1] - rows[-1]["cost_pct"] - 6, 142)
ax.axis("off")
fig.suptitle("100 units of crude oil, and what is left doing work",
             x=0.055, ha="left", fontsize=13, fontweight="bold", color=INK)
ax.annotate("Hall, Balogh and Murphy's arithmetic for American oil, drawn from the percentages in their own paper. Of 100 units of crude in the ground,\n"
            "10 are spent getting it out, 10 running the refinery, 17 leave as the part of the barrel that never becomes fuel, 3 move it to where it burns,\n"
            "and 24 build and maintain the roads it drives on. Thirty-six are still doing work — so about three units of crude for one unit of service,\n"
            "which is where their “minimum EROI of 3:1” comes from. The widely reproduced chart of this cascade leaves 20.5 rather than 36; the note says why.",
            xy=(0, 0), xycoords=("axes fraction", "axes fraction"),
            xytext=(0, -0.02), textcoords="axes fraction", va="top",
            fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
if len(sys.argv) > 3:
    fig.savefig(sys.argv[3], format="png", dpi=150, bbox_inches="tight")
print("wrote", sys.argv[2], "|", rows[-1]["remaining_pct"], "units left,",
      f"{100 / float(rows[-1]['remaining_pct']):.1f} units of crude per unit of service")

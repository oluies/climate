#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure 1.3a: North Sea gas, the same question figure 1.2a asks of oil.

Figure 1.3 shows that Britain's feared generating gap never appeared. This
shows what the fleet that closed it burns, and where that comes from. Britain's
gas surplus lasted nine years against oil's twenty-four, and it was never large
— the tall band is Norway, on a scale seven times the others.

The shaded strip on Denmark's line is the Tyra rebuild, the only interruption
in either figure that is engineering rather than depletion.

Input: data-refresh/north-sea-gas-trade.csv from `mill Refresh.scala
chapter01GasTrade`."""
import sys, csv, collections, textwrap, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID, ZERO = "#161d1b", "#8a8a85", "#ededea", "#c9c9c4"
COLOUR = {"United Kingdom": "#4a3aa7", "Norway": "#1baf7a",
          "Denmark": "#eda100", "Netherlands": "#bf4433"}
# The oil figure draws "importing" in this same red, which is free there
# because no series uses it. Here the Netherlands does, on the panel the label
# sits on, so the label takes the muted grey instead. The three small-panel
# lines stay red, amber and indigo: separable by hue, not only by lightness.
SMALL = ["Netherlands", "Denmark", "United Kingdom"]

series = collections.defaultdict(list)
for r in csv.DictReader(open(sys.argv[1])):
    series[r["country"]].append((int(r["year"]), float(r["net_kwh_per_day"])))
for v in series.values():
    v.sort()
missing = [c for c in COLOUR if c not in series]
if missing:
    raise SystemExit(f"{sys.argv[1]}: no rows for {', '.join(missing)}")

def surplus_years(country):
    years = [y for y, v in series[country] if v > 0]
    if not years:
        raise SystemExit(f"{sys.argv[1]}: {country} is never in surplus, "
                         "which is not the figure this script draws")
    return years

fig, (ax, bx) = plt.subplots(1, 2, figsize=(10.8, 4.8), sharex=True)

# Left: Norway, which is the whole of the rest of the chart several times over.
n = series["Norway"]
ax.fill_between([y for y, _ in n], 0, [v for _, v in n], color=COLOUR["Norway"],
                alpha=0.22, zorder=2)
ax.plot([y for y, _ in n], [v for _, v in n], color=COLOUR["Norway"], lw=2.0, zorder=3)
peak = max(n, key=lambda p: p[1])
ax.plot([peak[0]], [peak[1]], "o", color=COLOUR["Norway"], ms=5, zorder=4)
ax.annotate(f"{peak[1]:.0f} kWh/d in {peak[0]}", peak, textcoords="offset points",
            xytext=(-6, 8), ha="right", fontsize=9.5, color=INK)
ax.annotate(f"{n[-1][1]:.0f} in {n[-1][0]}", n[-1], textcoords="offset points",
            xytext=(4, -20), ha="right", fontsize=9.5, color=INK)
ax.set_title("Norway", loc="left", fontsize=11.5, color=INK)
ax.set_ylim(0, 700)
ax.set_ylabel("Net gas exports, kWh per day per person", fontsize=9.5)

# Right: the other three, at a seventh of the scale, where the crossings are.
for country in SMALL:
    s = series[country]
    bx.plot([y for y, _ in s], [v for _, v in s], color=COLOUR[country], lw=2.0,
            zorder=3, label=country)
    bx.annotate(f"{s[-1][1]:.0f}", s[-1], textcoords="offset points", xytext=(6, -4),
                fontsize=9.5, color=COLOUR[country], fontweight="bold")
bx.axhline(0, color=ZERO, lw=1.2, zorder=2)

# Britain's nine years, marked because they are the figure's point.
uk = surplus_years("United Kingdom")
bx.axvspan(uk[0], uk[-1], color=COLOUR["United Kingdom"], alpha=0.10, zorder=1)
# Below the lines rather than above them: the top right of this panel is the
# legend and the top middle is the Dutch curve.
bx.annotate(f"Britain in surplus,\n{uk[0]}–{uk[-1]}: {len(uk)} years",
            ((uk[0] + uk[-1]) / 2, -27), ha="center", va="top", fontsize=9,
            color=COLOUR["United Kingdom"])
# Denmark's interruption, which is a rebuild rather than an ending.
dk = surplus_years("Denmark")
gap = sorted(set(range(dk[0], dk[-1] + 1)) - set(dk))
if gap:
    bx.axvspan(gap[0] - 0.5, gap[-1] + 0.5, color=COLOUR["Denmark"], alpha=0.16, zorder=1)
    bx.annotate("Tyra\nrebuilt", (gap[-1] + 1.0, 30), ha="left", fontsize=8.5,
                color=COLOUR["Denmark"])
bx.annotate("exporting", (1967, 5), fontsize=9, color=MUTED)
bx.annotate("importing", (1967, -12), fontsize=9, color=MUTED)
bx.set_title("The Netherlands, Denmark and Britain", loc="left", fontsize=11.5, color=INK)
bx.set_ylim(-40, 100)
bx.legend(frameon=False, fontsize=9.5, loc="upper right")

for a in (ax, bx):
    a.set_xlim(1965, max(y for y, _ in series["Norway"]))
    a.grid(color=GRID, lw=0.8)
    a.set_axisbelow(True)
    for sp in ("top", "right"):
        a.spines[sp].set_visible(False)
    for sp in ("bottom", "left"):
        a.spines[sp].set_color(ZERO)
    a.tick_params(length=0, labelsize=9.5)

fig.suptitle("North Sea gas: Britain's nine years", x=0.055, ha="left",
             fontsize=13, fontweight="bold", color=INK)
nl = surplus_years("Netherlands")
# Every number in the caption comes from what was plotted: the axis limits, the
# runs and the gap. The Danish clause is written only when there is a gap to
# describe, which is the same condition the shaded strip is drawn under.
unbroken = "unbroken " if len(nl) == nl[-1] - nl[0] + 1 else ""
danish = (f" Denmark's gap is the Tyra hub being rebuilt, {gap[0]}–{gap[-1]}, the only interruption in "
          "either figure that is engineering rather than depletion." if gap else
          " Denmark's surplus runs without interruption here.")
# Wrapped rather than hand-broken, so no line can quietly grow past the figure
# box (which pads the canvas and shrinks the chart) or fall well short of it
# (which trims narrower than figure 1.2a, the figure this one is read beside).
caption = textwrap.fill(
    "Gas production minus each country's own consumption, per person per day, on the same basis as figure 1.2a. "
    f"Note the two scales: Norway's panel goes to {ax.get_ylim()[1]:.0f} kWh per day per person and the other "
    f"three to {bx.get_ylim()[1]:.0f}. Britain's gas surplus ran {uk[0]}–{uk[-1]}, {len(uk)} years against the "
    f"twenty-four its oil managed, and peaked at a fifth of oil's best year. The Dutch run is the long one — "
    f"{len(nl)} {unbroken}years to {nl[-1]} — and it ends without the gas running out: Groningen was shut in "
    f"because of the earthquakes it caused.{danish} Production minus inland consumption is a proxy for net "
    "trade, not customs data.", 155)
ax.annotate(caption,
            xy=(0, -0.19), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
if len(sys.argv) > 3:
    fig.savefig(sys.argv[3], format="png", dpi=150, bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2], "|", ", ".join(
    f"{c} {series[c][-1][1]:.0f} kWh/d" for c in COLOUR))

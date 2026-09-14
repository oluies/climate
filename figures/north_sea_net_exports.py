#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure 1.2a: the same three North Sea countries, as exporters rather than
producers.

Figure 1.2 plots what they pump. This plots what they have left to sell —
production minus what they burn themselves — per person per day, which is the
book's own unit. On the production chart the three look like one story at three
scales. Here they are three different stories, and the difference is domestic
demand.

Norway is drawn on its own axis because it is twenty times the others and
putting them on one scale would flatten Britain and Denmark into the zero line,
which is the part of the figure that has to be readable.

Input: data-refresh/north-sea-net-exports.csv from `mill Refresh.scala
chapter01NetExports`."""
import sys, csv, collections, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID, ZERO = "#161d1b", "#8a8a85", "#ededea", "#c9c9c4"
COLOUR = {"United Kingdom": "#4a3aa7", "Norway": "#1baf7a", "Denmark": "#eda100"}
IMPORT = "#bf4433"

series = collections.defaultdict(list)
for r in csv.DictReader(open(sys.argv[1])):
    series[r["country"]].append((int(r["year"]), float(r["net_kwh_per_day"])))
for v in series.values():
    v.sort()
missing = [c for c in COLOUR if c not in series]
if missing:
    raise SystemExit(f"{sys.argv[1]}: no rows for {', '.join(missing)}")

# Each country's own run of surplus years, taken from the data rather than
# written down, so the annotations cannot drift from the lines.
def run(country):
    years = [y for y, v in series[country] if v > 0]
    return (years[0], years[-1]) if years else None

fig, (ax, bx) = plt.subplots(1, 2, figsize=(10.8, 4.8),
                             gridspec_kw={"width_ratios": [1, 1]}, sharex=True)

# Left: Norway, on its own scale.
n = series["Norway"]
ax.fill_between([y for y, _ in n], 0, [v for _, v in n], color=COLOUR["Norway"],
                alpha=0.22, zorder=2)
ax.plot([y for y, _ in n], [v for _, v in n], color=COLOUR["Norway"], lw=2.0, zorder=3)
peak = max(n, key=lambda p: p[1])
ax.plot([peak[0]], [peak[1]], "o", color=COLOUR["Norway"], ms=5, zorder=4)
ax.annotate(f"{peak[1]:.0f} kWh/d in {peak[0]}", peak, textcoords="offset points",
            xytext=(6, 6), fontsize=9.5, color=INK)
ax.annotate(f"{n[-1][1]:.0f} in {n[-1][0]}", n[-1], textcoords="offset points",
            xytext=(-4, 10), ha="right", fontsize=9.5, color=INK)
ax.set_title("Norway", loc="left", fontsize=11.5, color=INK)
ax.set_ylim(0, 1200)
ax.set_ylabel("Net oil exports, kWh per day per person", fontsize=9.5)

# Right: Britain and Denmark, on a scale forty times smaller, where the
# crossing into deficit is the whole point.
for country in ("United Kingdom", "Denmark"):
    s = series[country]
    bx.plot([y for y, _ in s], [v for _, v in s], color=COLOUR[country], lw=2.0,
            zorder=3, label=country)
    span = run(country)
    if span:
        end = next(v for y, v in s if y == span[1])
        bx.annotate(f"{span[0]}–{span[1]}", (span[1], end), textcoords="offset points",
                    xytext=(7, 6 if country == "United Kingdom" else -14),
                    fontsize=9, color=COLOUR[country])
    # The two end within a kilowatt-hour of each other, so they are written on
    # opposite sides of the line rather than on top of one another.
    bx.annotate(f"{s[-1][1]:.0f}", s[-1], textcoords="offset points",
                xytext=(6, 4 if country == "United Kingdom" else -12),
                fontsize=9.5, color=COLOUR[country], fontweight="bold")
bx.axhline(0, color=ZERO, lw=1.2, zorder=2)
bx.annotate("exporting", (1967, 3), fontsize=9, color=MUTED)
bx.annotate("importing", (1967, -7), fontsize=9, color=IMPORT)
bx.set_title("Britain and Denmark", loc="left", fontsize=11.5, color=INK)
bx.set_ylim(-30, 62)
bx.legend(frameon=False, fontsize=9.5, loc="upper left", bbox_to_anchor=(0.02, 1.0))

for a in (ax, bx):
    a.set_xlim(1965, max(y for y, _ in series["Norway"]))
    a.grid(color=GRID, lw=0.8)
    a.set_axisbelow(True)
    for sp in ("top", "right"):
        a.spines[sp].set_visible(False)
    for sp in ("bottom", "left"):
        a.spines[sp].set_color(ZERO)
    a.tick_params(length=0, labelsize=9.5)

fig.suptitle("What the North Sea had left to sell", x=0.055, ha="left",
             fontsize=13, fontweight="bold", color=INK)
uk, dk = run("United Kingdom"), run("Denmark")
ax.annotate("Oil production minus each country's own consumption, divided by the people living there. Note the two scales: Norway's panel goes to 1200 kWh per day\n"
            f"per person, Britain and Denmark's to 60. All three imported oil before the North Sea; Britain's surplus ran {uk[0]}–{uk[1]} and Denmark's {dk[0]}–{dk[1]}, and both have\n"
            "been importers ever since. Norway has not stopped, because it burns almost nothing of what it pumps: it still sells about nine-tenths of its production.\n"
            "This is production minus inland consumption rather than customs data — Britain exports its own light crude and imports heavier grades, so its gross\n"
            "flows are much larger than the net line here. In energy rather than barrels, so the levels are not those of figure 1.2; the shapes are.",
            xy=(0, -0.19), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
if len(sys.argv) > 3:
    fig.savefig(sys.argv[3], format="png", dpi=150, bbox_inches="tight")
print("wrote", sys.argv[2], "|", ", ".join(
    f"{c} {series[c][-1][1]:.0f} kWh/d" for c in COLOUR))

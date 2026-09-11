#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure 11a.2: Finnish data-centre electricity capacity, committed against
assumed.

The left-hand bars are one running total -- what is operating, what has an
investment decision, and an estimate of Google's four sites -- and the
right-hand bars are three separate scenarios for what the fleet would be. The
dashed line carries the committed total across the divide, because the argument
is that it has already passed two of the three.

Input: data-refresh/fi-dc-pipeline.csv from `mill Refresh.scala
chapter11aFinlandPipeline`."""
import sys, csv, textwrap, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
BUILT, ADDED, ESTIMATE, TOTAL, SCENARIO = \
    "#1f6f9c", "#5f97b8", "#bf4433", "#14384a", "#b7bab3"
GAP = 0.9                       # blank column between the two panels
COLOUR = {"base": BUILT, "add": ADDED, "total": TOTAL, "scenario": SCENARIO}

# Four digits run together and five are split, which is the book's own habit.
num = lambda v: f"{v:,.0f}".replace(",", " ") if v >= 10000 else f"{v:.0f}"

rows = list(csv.DictReader(open(sys.argv[1])))
for r in rows:
    r["mw"] = float(r["mw"])
left = [r for r in rows if r["kind"] != "scenario"]
right = [r for r in rows if r["kind"] == "scenario"]
total = next(r["mw"] for r in rows if r["kind"] == "total")

# The Google bar is an estimate rather than a disclosure, so it is the one bar
# drawn in the warning colour. It is identified by its kind and position, not
# by its label, so a rename in the data does not silently lose the highlight.
adds = [i for i, r in enumerate(left) if r["kind"] == "add"]
estimate = adds[-1] if adds else None

fig, ax = plt.subplots(figsize=(9.6, 5.2))
x, bottom, ticks = 0.0, 0.0, []
for i, r in enumerate(left):
    h, colour = r["mw"], COLOUR[r["kind"]]
    b = 0.0 if r["kind"] in ("base", "total") else bottom
    if i == estimate:
        colour = ESTIMATE
    ax.bar(x, h, bottom=b, width=0.62, color=colour, zorder=3)
    ax.text(x, b + h + 110, ("+" if r["kind"] == "add" else "") + num(h),
            ha="center", va="bottom", fontsize=10.5, color=INK,
            fontweight="bold" if r["kind"] == "total" else "normal")
    if r["kind"] != "total":                      # carry the running total over
        ax.plot([x + 0.31, x + 1 - 0.31], [b + h, b + h], color=MUTED,
                lw=0.9, ls=(0, (1.6, 1.6)), zorder=2)
        bottom = b + h
    ticks.append((x, r))
    x += 1.0

x += GAP
divider = x - GAP / 2 - 0.5
for r in right:
    ax.bar(x, r["mw"], width=0.62, color=SCENARIO, zorder=3)
    ax.text(x, r["mw"] + 110, num(r["mw"]), ha="center", va="bottom",
            fontsize=10.5, color=INK)
    ticks.append((x, r))
    x += 1.0

ax.axhline(total, xmin=0.0, xmax=1.0, color=ESTIMATE, lw=1.1,
           ls=(0, (5, 3)), zorder=1)
ax.axvline(divider, color="#d5d5d0", lw=1.0, zorder=1)
ax.set_xlim(-0.7, x - 0.3)
ax.set_ylim(0, 8000)
ax.set_xticks([t[0] for t in ticks])
# Two of the labels are a sentence long, so they are wrapped rather than
# shrunk: seven bars across a page cannot take a horizontal label each.
tick = lambda r: (textwrap.fill(r["label"], 14, break_long_words=False) +
                  "\n" + r["sublabel"]).strip()
ax.set_xticklabels([tick(t[1]) for t in ticks], fontsize=8.5, linespacing=1.45)
ax.set_yticks(range(0, 8001, 1000))
ax.set_yticklabels([num(v) for v in range(0, 8001, 1000)], fontsize=9.5)
ax.set_ylabel("Data-centre electricity capacity, MW", fontsize=10.5)
ax.grid(axis="y", color=GRID, lw=0.8, zorder=0)
ax.set_axisbelow(True)
for s in ("top", "right", "left"):
    ax.spines[s].set_visible(False)
ax.spines["bottom"].set_color("#c9c9c4")
ax.tick_params(length=0)

# Panel headings rather than a legend: the two halves are different quantities,
# and a legend would invite the reader to compare bar against bar.
for span, text in (((-0.7, divider), "W H A T   I S   C O M M I T T E D"),
                   ((divider, x - 0.3), "W H A T   T H E   S C E N A R I O S   A S S U M E")):
    ax.text(sum(span) / 2, 7700, text, ha="center", va="center",
            fontsize=9.5, color=MUTED)

ax.set_title("Finland has committed more data centre than its scenarios assumed",
             loc="left", fontsize=13, fontweight="bold", pad=14, color=INK)
ax.annotate("Left-hand bars are one running total; right-hand bars are separate scenarios.\n"
            "Google does not publish the capacity of its four sites: the 1300 MW is an estimate\n"
            "from the EUR 13 billion. The committed total excludes a further 2500 MW in planning.\n"
            "The 2050 and 2055 scenarios are energy, converted to power at 60% utilisation.",
            xy=(0, -0.30), xycoords="axes fraction", va="top",
            fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
print("wrote", sys.argv[2], f"({num(total)} MW committed, "
      f"{total / max(r['mw'] for r in right):.2f} of the largest scenario)")

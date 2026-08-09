#!/usr/bin/env python3
"""Figure O.1: why the hydrogen ladder is ordered the way it is.

This is not a reproduction of Liebreich's ladder, which is his work. It is this
book's own arithmetic for the same question: for each use, how many kWh of
electricity does hydrogen need to deliver one kWh of what the user actually
wants, and what does the best alternative need? The ordering falls out of the
ratio between those two, which is the ladder's logic restated in MacKay's unit.

No input file: the four plotted numbers are computed here from the conversion
factors, so those cannot drift from the chapter. The chapter's other figures -
the unfavourable-end car cost and the round-trip range - are derived in the text
and are not reproduced here.
"""
import sys, matplotlib.pyplot as plt, numpy as np

INK, MUTED = "#161d1b", "#8a8a85"
H2, ALT = "#4a3aa7", "#1baf7a"

ELECTROLYSIS = 0.70          # electricity into hydrogen
COMPRESS_HI = 0.90           # optimistic handling loss; the chapter gives the other end
FUELCELL = 0.55              # hydrogen back to shaft power
BOILER = 0.90                # hydrogen to heat
BATTERY = 1 / 1.15           # grid to wheels, round trip
COP = 4.0                    # heat pump seasonal performance
PTL = 1 / 1.7                # electricity to liquid fuel, chapter 20

# kWh of electricity per kWh delivered. None = no alternative exists.
# Ordered as the ladder is: unavoidable uses at the top, beaten ones at the foot.
ROWS = [
    ("Steel, fertilizer,\nmethanol", 1 / ELECTROLYSIS,             None,        "no alternative molecule"),
    ("Long-haul flight", 1 / PTL,                                  None,        "nothing flies on a battery"),
    ("A car",          1 / (ELECTROLYSIS * COMPRESS_HI * FUELCELL), 1 / BATTERY, "battery"),
    ("Domestic heat",  1 / (ELECTROLYSIS * BOILER),               1 / COP,     "heat pump"),
]

fig, ax = plt.subplots(figsize=(9.2, 5.4))
y = np.arange(len(ROWS))[::-1]
h = 0.34

for i, (name, h2, alt, altlab) in zip(y, ROWS):
    ax.barh(i + h / 2, h2, height=h, color=H2, zorder=3)
    ax.annotate(f"{h2:.1f}", xy=(h2, i + h / 2), xytext=(5, 0), textcoords="offset points",
                va="center", fontsize=9.5, color=H2, fontweight="bold")
    if alt is None:
        # Nothing to compare against - which is precisely why these sit at the top.
        ax.annotate(altlab, xy=(0.06, i - h / 2), va="center", fontsize=9.5,
                    color=MUTED, style="italic", zorder=4)
    else:
        ax.barh(i - h / 2, alt, height=h, color=ALT, zorder=3)
        ax.annotate(f"{alt:.2f}  {altlab}", xy=(alt, i - h / 2), xytext=(5, 0),
                    textcoords="offset points", va="center", fontsize=9.5, color=ALT)
        ax.annotate(f"×{h2/alt:.1f}", xy=(max(h2, alt), i), xytext=(52, 0),
                    textcoords="offset points", va="center", fontsize=11,
                    color=INK, fontweight="bold")

ax.set_yticks(y)
ax.set_yticklabels([r[0] for r in ROWS], fontsize=10.5)
ax.set_xlim(0, 3.2)
ax.set_ylim(-0.75, len(ROWS) - 0.25)
ax.set_xlabel("kWh of electricity per kWh of useful output, fuel or feedstock — see caption", fontsize=10.5)
ax.grid(axis="x", color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.spines["bottom"].set_color("#c9c9c4")
ax.tick_params(axis="y", length=0)
ax.set_title("Why the ladder is ordered the way it is", loc="left",
             fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("Purple is hydrogen; green is the best alternative. A use sits low on the ladder when the alternative is cheap, not\n"
            "when hydrogen is difficult — a hydrogen boiler needs six times the electricity of a heat pump, and a fuel-cell car\n"
            "two and a half times a battery. A use sits high when there is no green bar to draw at all: nothing flies a long-haul\n"
            "aircraft on a battery, and no other molecule reduces iron ore or fixes nitrogen. Hydrogen is not efficient at the top\n"
            "of the ladder either — it is simply unopposed. The bottom two bars are measured at the point of use, the wheels and\n"
            "the radiator; the top two at the fuel and the feedstock, because that is where the comparison stops.",
            xy=(0, -0.20), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[1], format="svg", bbox_inches="tight"); print("wrote", sys.argv[1])

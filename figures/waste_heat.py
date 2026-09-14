#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure P.1: what waste heat alone does, if energy use keeps growing.

Thomas Murphy's calculation, recomputed: add a human power source growing by a
factor of ten a century to the sunlight the Earth absorbs, and solve the same
equilibrium this book's chapter 1 solves. Nothing here depends on where the
energy comes from.

Input: data-refresh/waste-heat.csv from `mill Refresh.scala chapterPWasteHeat`."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID, LINE, MARK = "#161d1b", "#8a8a85", "#ededea", "#bf4433", "#1f6f9c"
rows = [(float(r["years"]), float(r["waste_wm2"]), float(r["temperature_k"]))
        for r in csv.DictReader(open(sys.argv[1]))]

fig, ax = plt.subplots(figsize=(8.4, 4.8))
ax.plot([r[0] for r in rows], [r[2] for r in rows], color=LINE, lw=2.1, zorder=3)
ax.axhline(373, color=MUTED, lw=1.0, ls=(0, (5, 3)), zorder=2)
ax.text(8, 375, "water boils", fontsize=9, color=MUTED)
ax.axhline(288, color=MUTED, lw=1.0, ls=(0, (1.5, 1.5)), zorder=2)
ax.text(8, 281, "today, 288 K", fontsize=9, color=MUTED)

# Murphy's own table 1.4, so a reader can see the recompute land on it.
for years, kelvin, note in ((100, 288.1, ""), (200, 288.9, ""), (300, 296.9, ""),
                            (417, 373.0, "417 years")):
    ax.plot([years], [kelvin], "o", color=MARK, ms=5, zorder=4)
    if note:
        ax.annotate(note, (years, kelvin), textcoords="offset points",
                    xytext=(-12, 10), ha="right", fontsize=9, color=MARK)
ax.plot([], [], "o", color=MARK, ms=5, label="Murphy's table 1.4")
ax.plot([], [], color=LINE, lw=2.1, label="the same equation, recomputed here")

ax.set_xlim(0, 460)
ax.set_ylim(275, 395)
ax.set_xlabel("Years of growth at a factor of ten a century", fontsize=10.5)
ax.set_ylabel("Earth's surface temperature, K", fontsize=10.5)
ax.grid(color=GRID, lw=0.8)
ax.set_axisbelow(True)
for s in ("top", "right"):
    ax.spines[s].set_visible(False)
for s in ("bottom", "left"):
    ax.spines[s].set_color("#c9c9c4")
ax.tick_params(length=0, labelsize=9.5)
ax.legend(frameon=False, fontsize=9.5, loc="upper left")
ax.set_title("Growth alone boils the planet, whatever the energy source",
             loc="left", fontsize=13, fontweight="bold", pad=12, color=INK)
ax.annotate("Today's 18 TW is 0.14 W/m² spread over the disk the Earth presents to the Sun, against the 961 W/m² of sunlight it\n"
            "absorbs there. Growing that term by ten a century and solving the same balance as chapter 1 — in, sunlight plus\n"
            "waste heat; out, σT⁴ — gives this curve, with 33 K added throughout for the greenhouse effect. No source of energy\n"
            "escapes it: the heat is the end of every use.",
            xy=(0, -0.20), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
boil = next(r[0] for r in rows if r[2] >= 373)
print("wrote", sys.argv[2], f"| boiling at {boil:.0f} years")

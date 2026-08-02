#!/usr/bin/env python3
"""Figure 6.15 redone with a 2026 flagship module beside MacKay's 2008 Sanyo.
Power against light intensity at 25 C. Modelled from datasheet ratings: output is
proportional to irradiance, with the small low-light efficiency droop crystalline
silicon actually shows. The point of the figure is the proportionality."""
import sys, numpy as np, seaborn as sns, matplotlib.pyplot as plt

MUTED = "#8a8a85"
# (label, rated W at 1000 W/m2, module area m2, colour)
MODS = [("Sanyo HIP-210NKHE1 (2008): 210 W, 1.18 m², 17.8%", 210, 1.18, "#8a8a85"),
        ("Maxeon 7 (2026): 445 W, 1.85 m², 24.1%", 445, 1.85, "#eda100")]
G = np.linspace(0, 1000, 201)
# Relative efficiency vs irradiance: ~0.97 at 200 W/m2, 1.00 at STC.
rel = np.where(G > 0, 1.0 - 0.055 * np.exp(-G / 260.0), 1.0)

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.4, 4.8))
for lab, pmax, area, c in MODS:
    ax.plot(G, pmax * (G / 1000.0) * rel, color=c, lw=2.3, label=lab)

for g, txt in [(1000, "bright sun"), (200, "bright but cloudy"), (100, "overcast")]:
    ax.axvline(g, color="#d8d8d4", lw=0.9, zorder=0)
    ax.annotate(txt, xy=(g, 12), xytext=(-5, 0), textcoords="offset points",
                rotation=90, ha="right", va="bottom", fontsize=8.5, color=MUTED)

ax.set_xlabel("light intensity (W/m$^2$)", fontsize=10.5)
ax.set_ylabel("module power output (W)", fontsize=10.5)
ax.set_xlim(0, 1050); ax.set_ylim(0, 500)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.legend(frameon=False, fontsize=9.5, loc="upper left")
ax.set_title("Module power against light intensity, at 25 °C",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("Eighteen years and eight percentage points of efficiency later the shape is unchanged: output is\n"
            "proportional to sunlight. A better panel raises the line; it does not bend it. At a tenth of full\n"
            "sun you get about a tenth of the power.",
            xy=(0, -0.19), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

#!/usr/bin/env python3
"""MacKay's Figure J.4 regenerated: the American states plotted against the
countries of Europe, on the same log-log axes with constant-density diagonals.
Input: data-refresh/states-and-europe.csv from `mill Refresh.scala chapterJ4`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt, numpy as np

INK, MUTED, RULE = "#161d1b", "#8a8a85", "#d8d8d4"
US, EU = "#eb6834", "#2a78d6"
df = pd.read_csv(sys.argv[1])
X0, X1, Y0, Y1 = 2e2, 2e6, 2e5, 1e8

sns.set_theme(style="whitegrid", rc={"grid.color": "#f2f2ef", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 6.4))

for d, lab in [(1000, "1000 people per km²"), (100, "100"), (10, "10"), (1, "1")]:
    ax.plot([X0, X1], [X0 * d, X1 * d], color=RULE, lw=0.9, zorder=0)
    xe = min(X1, Y1 / d)
    ax.annotate(lab, xy=(xe, xe * d), xytext=(-4, 4), textcoords="offset points",
                fontsize=8.5, color=MUTED, ha="right", va="bottom")

for kind, col, mk, lab in (("US state", US, "o", "US states"),
                           ("European country", EU, "s", "European countries")):
    d = df[df.kind == kind]
    ax.scatter(d.area_km2, d.population, s=26, marker=mk, color=col,
               alpha=0.85, linewidths=0, zorder=3, label=lab)

# Enough labels to navigate by, chosen to span the range rather than to be complete.
LABEL = {"California": (-7, -11), "Texas": (7, -4), "Florida": (-6, 3), "New York": (-6, 3),
         "Alaska": (-6, 3), "Wyoming": (6, 2), "Rhode Island": (-6, 2), "Montana": (6, -10),
         "Vermont": (-6, -9), "New Jersey": (-6, 2), "Illinois": (6, -10), "Pennsylvania": (6, 2),
         "Germany": (6, 3), "France": (7, 11), "England": (-6, 3), "Spain": (7, 1),
         "Italy": (-7, 4), "Poland": (-7, -10), "Sweden": (6, 2), "Norway": (-6, -9),
         "Netherlands": (-6, 2), "Iceland": (-6, 2), "Scotland": (6, -9), "Ukraine": (7, -11),
         "Finland": (6, 2), "Malta": (-6, 2)}
for _, r in df[df.region.isin(LABEL)].iterrows():
    dx, dy = LABEL[r.region]
    ax.annotate(r.region, xy=(r.area_km2, r.population), xytext=(dx, dy),
                textcoords="offset points", fontsize=8.5,
                ha="right" if dx < 0 else "left",
                color=US if r.kind == "US state" else EU)

ax.set_xscale("log"); ax.set_yscale("log")
ax.set_xlabel("Land area (km²)", fontsize=10.5)
ax.set_ylabel("Population", fontsize=10.5)
ax.set_xlim(X0, X1); ax.set_ylim(Y0, Y1)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.legend(frameon=False, loc="upper left", fontsize=9.5)
ax.set_title("The American states and the countries of Europe",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("Europe is the denser continent: its countries sit above the American\n"
            "states of the same size, and no state matches the Netherlands or England.",
            xy=(0.985, 0.055), xycoords="axes fraction", ha="right",
            fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

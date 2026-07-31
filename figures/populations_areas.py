#!/usr/bin/env python3
"""MacKay's Figure J.1 regenerated: populations against land areas, both on log
scales, with diagonals of constant population density. 2023 populations.
Input: data-refresh/populations-areas.csv (built from the chapter J table)."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt, numpy as np

INK, MUTED, RULE, DOT = "#161d1b", "#8a8a85", "#d8d8d4", "#2a78d6"
HILITE = "#e34948"
df = pd.read_csv(sys.argv[1])
# MacKay plots the continents and the world alongside the countries; keep them,
# but mark them, or they read as unusually roomy countries.
AGG = {"World", "Asia", "Africa", "Europe", "North America", "Latin America",
       "Oceania", "European Union"}
df["kind"] = np.where(df.region.isin(AGG), "aggregate", "country")

X0, X1, Y0, Y1 = 3e2, 3e8, 3e4, 3e10
sns.set_theme(style="whitegrid", rc={"grid.color": "#f2f2ef", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 6.4))

# Lines of constant density, MacKay's organising device. Label each where it
# leaves the plot, so the labels never land on top of the countries.
for d, lab in [(1000, "1000 people per km²"), (100, "100"), (10, "10"), (1, "1"), (0.1, "0.1")]:
    ax.plot([X0, X1], [X0 * d, X1 * d], color=RULE, lw=0.9, zorder=0)
    xe = min(X1, Y1 / d)                      # x where the line exits top or right
    ax.annotate(lab, xy=(xe, xe * d), xytext=(-4, 4), textcoords="offset points",
                fontsize=8.5, color=MUTED, ha="right", va="bottom")

# The world's own diagonal, which is what MacKay called the central line.
w = df[df.region == "World"].iloc[0]
wd = w.population / w.area_km2
ax.plot([X0, X1], [X0 * wd, X1 * wd], color="#46534f", lw=1.1, ls=(0, (5, 3)), zorder=1)
ax.annotate(f"the world: {wd:.0f} per km²", xy=(1.4e4, 1.4e4 * wd), xytext=(0, 5),
            textcoords="offset points", fontsize=9, color="#46534f", rotation=29,
            rotation_mode="anchor")

for kind, mk, sz, col, lab in (("country", "o", 24, DOT, "countries"),
                               ("aggregate", "D", 34, HILITE, "continents and the world")):
    d = df[df.kind == kind]
    ax.scatter(d.area_km2, d.population, s=sz, marker=mk, color=col,
               alpha=0.85, linewidths=0, zorder=3, label=lab)

# offsets in points, to keep the busy middle of the cloud readable
LABEL = {"World": (6, 4), "China": (6, 3), "India": (-6, 3), "USA (ex. Alaska)": (6, -10),
         "Bangladesh": (-6, 3), "Japan": (6, 2), "England": (-6, 2), "Netherlands": (-6, 2),
         "Russia": (6, -9), "Canada": (6, 2), "Australia": (6, -9), "Sweden": (6, 2),
         "Greenland": (-6, 2), "Iceland": (-6, 2), "Hong Kong": (6, 3), "Singapore": (-6, -8),
         "Africa": (7, 4), "Europe": (-7, 3)}
for _, r in df[df.region.isin(LABEL)].iterrows():
    dx, dy = LABEL[r.region]
    ax.annotate(r.region, xy=(r.area_km2, r.population), xytext=(dx, dy),
                textcoords="offset points", fontsize=8.5,
                ha="right" if dx < 0 else "left",
                color=HILITE if r.kind == "aggregate" else INK)

ax.set_xscale("log"); ax.set_yscale("log")
ax.set_xlabel("Land area (km²)", fontsize=10.5)
ax.set_ylabel("Population", fontsize=10.5)
ax.set_xlim(X0, X1); ax.set_ylim(Y0, Y1)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.legend(frameon=False, loc="lower right", fontsize=9.5)
ax.set_title("Populations and areas of the countries and regions of the world, 2023",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate(f"the world's diagonal has moved: {wd:.0f} people per km²,\nagainst 43 when this book was written",
            xy=(0.015, 0.955), xycoords="axes fraction", va="top",
            fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

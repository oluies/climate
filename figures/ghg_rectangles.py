#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas"]
# ///
"""MacKay's rectangle construction, redrawn for 2000 and 2023. Width is
population, height is greenhouse-gas emissions per person, so each rectangle's
area is a country's total emissions and the whole figure's area is the world's.

Inputs: data-refresh/owid-per-capita-ghg-emissions.csv and owid-population.csv."""
import sys, csv, textwrap, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
HI, MID, LO = "#b8402e", "#eda100", "#4a3aa7"
Y0, Y1 = 2000, 2023

def load(path, idx=3):
    out = {}
    for r in csv.reader(open(path)):
        # Bara lander: aggregat saknar ISO3 eller bar OWID-prefix.
        if len(r) > idx and r[1] and not r[1].startswith("OWID_") and len(r[1]) == 3 \
           and r[2].lstrip("-").isdigit() and r[idx]:
            out.setdefault(r[0], {})[int(r[2])] = float(r[idx])
    return out

ghg, pop = load(sys.argv[1]), load(sys.argv[2])
def year(y):
    v = [(c, pop[c][y], ghg[c][y]) for c in ghg
         if c in pop and y in ghg[c] and y in pop[c] and pop[c][y] > 0]
    return sorted(v, key=lambda t: -t[2])

LABEL = {"United States": "USA", "United Kingdom": "UK", "Russia": "Russia",
         "China": "China", "India": "India", "Japan": "Japan", "Germany": "Germany",
         "Indonesia": "Indonesia", "Brazil": "Brazil", "Nigeria": "Nigeria",
         "Australia": "Australia", "Canada": "Canada", "Saudi Arabia": "Saudi"}

have = {f.name for f in font_manager.fontManager.ttflist}
FAM = next((f for f in ("Helvetica Neue", "Helvetica", "Arial") if f in have), "DejaVu Sans")
plt.rcParams.update({"font.family": FAM})

YMAX = 34
cut = []                       # (land, ar, ton per person, klippt area)
fig, axes = plt.subplots(2, 1, figsize=(8.8, 8.4), sharex=True, sharey=True)
for ax, y in zip(axes, (Y0, Y1)):
    x = 0.0; tot = 0.0
    for c, p, pc in year(y):
        w = p / 1e9
        col = HI if pc >= 12 else (MID if pc >= 6 else LO)
        # Nagra fa lander gar over skalan. De ar harfina, sa att hoja axeln
        # for deras skull skulle platta till hela bilden; i stallet klipps de
        # uttalat, med en markering och en rad i fotnoten. h anvands ocksa till
        # etiketten, annars hamnar den utanfor axeln och ritas aldrig.
        h = min(pc, YMAX)
        ax.add_patch(plt.Rectangle((x, 0), w, h, facecolor=col,
                                   edgecolor="white", linewidth=0.35, alpha=0.9))
        if pc > YMAX:
            cut.append((c, y, pc, p * (pc - YMAX)))
            ax.plot([x + w / 2], [YMAX], marker="^", ms=4.5, color=col, zorder=6)
        if c in LABEL and (w > 0.12 or pc > 14):
            rot = 90 if w < 0.30 else 0
            if pc > 20 and rot == 90:
                # Hoga smala staplar: etiketten inuti, annars sticker den upp
                # genom rubriken.
                ax.text(x + w / 2, h - 0.8, LABEL[c], ha="center", va="top",
                        fontsize=8.5, color="white", rotation=90)
            else:
                ax.text(x + w / 2, h + 0.5, LABEL[c], ha="center", va="bottom",
                        fontsize=8.5, color=INK, rotation=rot)
        x += w; tot += p * pc
    ax.set_ylabel("tonnes CO$_2$e per person", fontsize=10)
    ax.set_title(f"{y}   ·   world total {tot/1e9:.0f} Gt CO$_2$e across {x:.1f} bn people",
                 loc="left", fontsize=10.5, color=MUTED, pad=10)
    ax.set_xlim(0, 8.3); ax.set_ylim(0, YMAX)
    ax.grid(axis="y", color=GRID, lw=1); ax.set_axisbelow(True)
    for sp in ("top", "right"):
        ax.spines[sp].set_visible(False)
axes[1].set_xlabel("cumulative population, billions", fontsize=10)
fig.suptitle("Figure 1.12. Every country a rectangle: width is population, height is emissions per person.",
             x=0.055, y=0.99, ha="left", fontsize=12.5, fontweight="bold")
fig.text(0.055, 0.962, "Area is total emissions, so the whole figure is the world's. Ordered tallest first, as MacKay draws them.\n"
                       "Red is above 12 tonnes a head, amber 6 to 12, blue below 6.",
         fontsize=9.5, color=MUTED, va="top", linespacing=1.5)
# Tom om en dataomgang eller ett hogre YMAX gor att ingen langre klipps.
names = sorted({c for c, _, _, _ in cut})
cutline = ""
if names:
    lost = sum(a for _, _, _, a in cut) / sum(p * pc for y in (Y0, Y1) for _, p, pc in year(y))
    cutline = " " + (", ".join(names[:-1]) + " and " + names[-1] if len(names) > 1 else names[0]) + \
              f" rise above the {YMAX}-tonne axis and are drawn cut off at it, with a caret; they are " \
              f"narrow enough that the area lost is {lost * 100:.2f}% of the total."
fig.text(0.055, 0.058, textwrap.fill(
    "All greenhouse gases in CO$_2$-equivalent including land use. Sources: Jones et al. and UN "
    "population, via Our World in Data. Only countries are drawn, so the totals sit a little below "
    "the world figure." + cutline, 118),
         fontsize=8.5, color=MUTED, va="top", linespacing=1.6)
fig.subplots_adjust(top=0.875, bottom=0.155, left=0.09, right=0.98, hspace=0.20)
fig.savefig(sys.argv[3], format="svg", bbox_inches="tight"); print("wrote", sys.argv[3])

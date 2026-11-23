#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Data centres as a share of energy and electricity, from the world down to one state.
Input: data-refresh/dc-world-share.csv from `mill Refresh.scala chapter11aWorldShare`.
Usage: python dc_world_share.py <csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt
from matplotlib.lines import Line2D

NOW, THEN, LINK, MUTED, TEXT = "#2a78d6", "#eb6834", "#c9c9c4", "#8a8a85", "#161d1b"
GROUPS = [("world", "The world"), ("national", "Countries and the EU"), ("local", "One state")]
GAP = 1.1

d = pd.read_csv(sys.argv[1])
d["reach"] = d.share_then_high.fillna(d.share_now)
rows, ys, heads, y = [], [], [], 0.0
for g, title in GROUPS:
    part = d[d.group == g].sort_values("reach")
    heads.append((y - 0.75, title))
    for r in part.itertuples():
        rows.append(r); ys.append(y); y += 1
    y += GAP

def pct(v): return f"{v:.2f}%" if v < 1 else f"{v:.1f}%" if v < 10 else f"{v:.0f}%"

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.9))
for yi, r in zip(ys, rows):
    ax.scatter(r.share_now, yi, s=44, color=NOW, zorder=3)
    ax.text(r.share_now - 0.9, yi, f"({r.year_now}) {pct(r.share_now)}",
            ha="right", va="center", fontsize=8.5, color=NOW)
    if pd.isna(r.share_then_high):
        continue
    ax.hlines(yi, r.share_now, r.share_then_high, color=LINK, lw=1.6, zorder=1)
    if r.share_then_high > r.share_then_low:   # a forecast range, drawn as a bar
        ax.hlines(yi, r.share_then_low, r.share_then_high, color=THEN, lw=7, alpha=0.45, zorder=2)
        ax.scatter([r.share_then_low, r.share_then_high], [yi, yi], s=44, color=THEN, zorder=3)
        then = f"{pct(r.share_then_low)} to {pct(r.share_then_high)} ({int(r.year_then)})"
    else:
        ax.scatter(r.share_then_high, yi, s=44, color=THEN, zorder=3)
        then = f"{pct(r.share_then_high)} ({int(r.year_then)})"
    ax.text(r.share_then_high + 0.9, yi, then, ha="left", va="center",
            fontsize=8.5, color=THEN, fontweight="bold")

for yh, title in heads:
    ax.text(-13, yh, title.upper(), ha="left", va="center", fontsize=8, color=MUTED, fontweight="bold")
ax.set_xlim(-13, 62)
ax.set_ylim(y - GAP + 0.3, -1.3)
ax.set_yticks(ys)
ax.set_yticklabels([r.label for r in rows], fontsize=9.5)
ax.set_xticks(range(0, 51, 10))
ax.xaxis.set_major_formatter(lambda v, _: f"{v:.0f}%")
ax.set_xlabel("Data-centre electricity as a share of the total", fontsize=10.5)
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0)

handles = [Line2D([], [], ls="", marker="o", color=NOW, label="latest measured"),
           Line2D([], [], ls="", marker="o", color=THEN, label="forecast")]
ax.legend(handles=handles, frameon=False, fontsize=9.5, ncol=2, loc="lower left",
          bbox_to_anchor=(0, 1.005), handletextpad=0.4, columnspacing=1.6)
ax.set_title("Small for the world, large where it lands",
             loc="left", fontsize=12.5, fontweight="bold", pad=34)
ax.annotate("World energy is the IEA's data-centre electricity over the Energy Institute's total energy supply,\n"
            "the 2030 figure against 2025 supply. Every other row is a share of that place's electricity.",
            xy=(0, -0.11), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2], f"({len(rows)} rows)")

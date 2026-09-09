#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Data-centre IT capacity as a share of national peak electricity load.
Input: data-refresh/dc-peak-share.csv from `mill Refresh.scala chapter11aPeakShare`.
Usage: python dc_peak_share.py <csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

NOW, THEN, LINK, MUTED, TEXT = "#2a78d6", "#eb6834", "#c9c9c4", "#8a8a85", "#161d1b"
# The two countries the chapter argues from get their labels in full colour;
# the rest are there to give those two a scale to sit on.
HIGHLIGHT = {"Ireland", "Sweden"}

d = pd.read_csv(sys.argv[1]).sort_values("share_2031")
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 0.34 * len(d) + 2.1))
y = range(len(d))
ax.hlines(y, d.share_2025, d.share_2031, color=LINK, lw=1.6, zorder=1)
ax.scatter(d.share_2025, y, s=44, color=NOW, zorder=3, label="2025")
ax.scatter(d.share_2031, y, s=44, color=THEN, zorder=3, label="2031 forecast")

# Labels outside each end of the dumbbell, so neither sits on the connector.
for i, r in enumerate(d.itertuples()):
    ax.text(r.share_2025 - 0.7, i, f"{r.share_2025:.1f}%", ha="right", va="center",
            fontsize=8.5, color=NOW)
    ax.text(r.share_2031 + 0.7, i, f"{r.share_2031:.1f}%", ha="left", va="center",
            fontsize=8.5, color=THEN, fontweight="bold")
ax.set_yticks(list(y))
ax.set_yticklabels(d.country, fontsize=9.5)
for t in ax.get_yticklabels():
    if t.get_text() in HIGHLIGHT: t.set_fontweight("bold"); t.set_color(TEXT)

ax.set_xlim(-3.5, 52)
ax.set_xlabel("IT capacity as a share of 2025 national peak electricity load", fontsize=10.5)
ax.xaxis.set_major_formatter(lambda v, _: f"{v:.0f}%" if v >= 0 else "")
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0)
# Above the plot rather than in it: a legend dot inside the panel reads as data.
ax.legend(frameon=False, fontsize=9.5, ncol=2, loc="lower left",
          bbox_to_anchor=(0, 1.005), handletextpad=0.4, columnspacing=1.6)
ax.set_title("How large could the data centres get, next to the grid they plug into?",
             loc="left", fontsize=12.5, fontweight="bold", pad=34)
ax.annotate("Installed IT capacity, not electricity consumed and not spare capacity. Ireland's 29% is already built;\n"
            "everywhere else the second dot is a pipeline. Both years are measured against the 2025 peak.",
            xy=(0, -0.115), xycoords="axes fraction", va="top",
            fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
print("wrote", sys.argv[2], f"({len(d)} countries, Ireland {d.share_2031.max():.1f}%)")

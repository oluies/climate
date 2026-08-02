#!/usr/bin/env python3
"""Which producers are past peak, and by how far.
Input: data-refresh/peaks-by-country.csv from `mill Refresh.scala chapterN`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

OIL, PAST, ATPEAK, MUTED = "#7a5c3e", "#7a5c3e", "#1baf7a", "#8a8a85"
df = pd.read_csv(sys.argv[1])
fuel = sys.argv[3] if len(sys.argv) > 3 else "Oil"
d = df[df.fuel == fuel].sort_values("peak_year")
col = [ATPEAK if y >= 2024 else PAST for y in d.peak_year]

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 0.30 * len(d) + 1.9))
labels = [f"{c}  ({y})" for c, y in zip(d.country, d.peak_year)]
bars = ax.barh(labels, d.pct_of_peak, color=col, height=0.68)
for b, v in zip(bars, d.pct_of_peak):
    ax.text(v + 1.2, b.get_y() + b.get_height() / 2, f"{v:.0f}%",
            va="center", fontsize=8.5, color="#161d1b")

ax.axvline(100, color="#c9c9c4", lw=1.1, zorder=0)
ax.set_xlabel(f"2025 {fuel.lower()} production as a share of that country's own maximum", fontsize=10.5)
ax.set_xlim(0, 118)
ax.xaxis.set_major_formatter(lambda v, _: f"{v:.0f}%")
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0, labelsize=8.5)
ax.invert_yaxis()
h = [plt.Rectangle((0, 0), 1, 1, color=c) for c in (PAST, ATPEAK)]
# Upper right: the top rows are the deepest declines, so that corner is empty.
ax.legend(h, ["Past its peak", "At its peak in 2025"], frameon=False,
          fontsize=9, loc="upper right")
n_past = int((d.peak_year <= 2020).sum())
ax.set_title(f"Peak {fuel.lower()}, country by country (peak year in brackets)",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
note_y = -0.055 - 2.2 / len(d)
ax.annotate(f"{n_past} of {len(d)} producers are past their own maximum — yet world output set a record in 2025.\n"
            "Peak oil is not a forecast. For most of the world it is history.",
            xy=(0, note_y), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2], f"({n_past}/{len(d)} past peak)")

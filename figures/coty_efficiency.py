#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib", "numpy"]
# ///
"""Figure 20.22 remade: the electric winners of European Car of the Year.
Input: data-refresh/coty-efficiency.csv from `mill Refresh.scala cotyEfficiency`."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

INK, MUTED, BAR, REF = "#161d1b", "#8a8a85", "#1baf7a", "#4a3aa7"
ROADSTER = 15.0
d = duckdb.sql(f"""
    SELECT year, model, lo, hi FROM read_csv_auto('{sys.argv[1]}') ORDER BY year
""").fetchnumpy()
n = len(d["year"])

fig, ax = plt.subplots(figsize=(9.2, 5.2))
x = np.arange(n)
for i in range(n):
    lo, hi = float(d["lo"][i]), float(d["hi"][i])
    if hi > lo:
        ax.bar(x[i], hi - lo, bottom=lo, width=0.5, color=BAR, alpha=0.35, zorder=3)
        ax.plot([x[i] - 0.25, x[i] + 0.25], [lo] * 2, color=BAR, lw=2.4, zorder=4)
        ax.plot([x[i] - 0.25, x[i] + 0.25], [hi] * 2, color=BAR, lw=2.4, zorder=4)
        lab = f"{lo:g}–{hi:g}"
    else:
        ax.plot([x[i] - 0.25, x[i] + 0.25], [lo] * 2, color=BAR, lw=3.2, zorder=4)
        lab = f"{lo:g}"
    # Lift the label clear when the bar top is close to the Roadster reference line.
    dy = 13 if abs(hi - ROADSTER) < 1.6 else 6
    ax.annotate(lab, xy=(x[i], hi), xytext=(0, dy), textcoords="offset points",
                ha="center", fontsize=9, color=INK, zorder=5)

# MacKay's own figure 20.22: a Tesla Roadster at 15 kWh per 100 km.
ax.axhline(ROADSTER, color=REF, lw=1.4, ls=(0, (5, 3)), zorder=2)
ax.annotate("MacKay's Tesla Roadster, 2008 — 15 kWh/100 km", xy=(-0.42, ROADSTER),
            xytext=(0, -14), textcoords="offset points", ha="left", fontsize=9,
            color=REF, zorder=5)

ax.set_xticks(x)
ax.set_xticklabels([f"{int(y)}\n{m}" for y, m in zip(d["year"], d["model"])], fontsize=9)
ax.set_ylim(0, 28)
ax.set_ylabel("kWh per 100 km (WLTP)", fontsize=10.5)
ax.grid(axis="y", color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right"): ax.spines[s].set_visible(False)
for s in ("bottom", "left"): ax.spines[s].set_color("#c9c9c4")
ax.tick_params(axis="x", length=0)
ax.set_title("European Car of the Year, when the winner was electric",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("Bars show the range across variants; a single line means one figure is published. The award's first electric winner,\n"
            "in 2019, used half as much again as the sports car MacKay put in figure 20.22 eleven years earlier. Winners reached\n"
            "under the Roadster's number with their best variants from 2024, and wholly below it by 2026 — by which point the car\n"
            "was a mainstream saloon rather than a £90 000 two-seater. Every bar is a test-cycle figure and every one is\n"
            "optimistic: WLTP barely loads the cabin heater, which alone draws 0.5 to 1 kW on the move.",
            xy=(0, -0.20), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

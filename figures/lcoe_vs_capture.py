#!/usr/bin/env python3
"""What it costs to build against what the output actually earned, GB.
Input: data-refresh/lcoe-vs-capture.csv from `mill Refresh.scala lcoeVsCapture`."""
import sys, duckdb, seaborn as sns, matplotlib.pyplot as plt, numpy as np

COST, CLEARS, SHORT, MUTED = "#8a8a85", "#1baf7a", "#e34948", "#8a8a85"
SYSAVG = 79.9
# Ordering, derived columns and types all in SQL; fetchnumpy keeps it out of pandas.
d = duckdb.sql(f"""
    SELECT technology, lcoe_low, lcoe_central, lcoe_high, capture,
           capture > lcoe_central AS ok
    FROM read_csv_auto('{sys.argv[1]}')
""").fetchnumpy()
n = len(d["technology"])

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(9.0, 5.0))
y = np.arange(n)[::-1]

# LCOE as a range with the central estimate marked, then what it actually earned.
for i, yy in enumerate(y):
    lo, mid, hi = d["lcoe_low"][i], d["lcoe_central"][i], d["lcoe_high"][i]
    ax.plot([lo, hi], [yy + 0.17] * 2, color=COST, lw=6, solid_capstyle="butt",
            alpha=0.35, zorder=2)
    ax.plot([mid] * 2, [yy + 0.03, yy + 0.31], color=COST, lw=2.4, zorder=3)
    ax.text(hi + 2, yy + 0.17, f"{mid:.0f}", va="center", fontsize=8.5, color="#161d1b")

    cap = d["capture"][i]
    ax.barh(yy - 0.19, cap, height=0.3, zorder=2,
            color=CLEARS if d["ok"][i] else SHORT)
    ax.text(cap + 2, yy - 0.19, f"{cap:.1f}", va="center", fontsize=8.5, color="#161d1b")

ax.axvline(SYSAVG, color="#4a3aa7", lw=1.2, ls=(0, (5, 3)), zorder=1)
# Between the solar and onshore rows, where nothing else reaches this far right.
ax.annotate(f"system average {SYSAVG:.1f}", xy=(SYSAVG, n - 1.55), xytext=(5, 0),
            textcoords="offset points", fontsize=8.5, color="#4a3aa7")

ax.set_yticks(y); ax.set_yticklabels(d["technology"], fontsize=9.5)
ax.set_xlabel("£ per MWh", fontsize=10.5)
ax.set_xlim(0, 140)
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0)
h = [plt.Line2D([0], [0], color=COST, lw=6, alpha=0.5),
     plt.Rectangle((0, 0), 1, 1, color=CLEARS),
     plt.Rectangle((0, 0), 1, 1, color=SHORT)]
# Below the axis: the plot area is full of bars and reference labels.
ax.legend(h, ["Cost to build (LCOE, low–high)", "Earned more than it cost",
              "Earned less than it cost"],
          frameon=False, fontsize=9, ncol=3, loc="upper center",
          bbox_to_anchor=(0.5, -0.115))
ax.set_title("What it costs to build against what it earned, Great Britain",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("Cost is DESNZ levelised cost for projects commissioning 2035, 2024 prices. Earnings are the price each\n"
            "technology actually realised on the GB market in 2025, weighted by its own output. Floating offshore wind\n"
            "and gas with carbon capture do not clear their costs; everything else does — at today's prices, and the\n"
            "point of chapter 28a is that building more of a technology lowers the price it earns.",
            xy=(0, -0.235), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

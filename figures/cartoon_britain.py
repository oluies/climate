#!/usr/bin/env python3
"""Cartoon Britain, 2008 and 2026 — MacKay's three-legged simplification, redrawn.
Input: data-refresh/cartoon-britain.csv from `mill Refresh.scala cartoonBritain`."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

OLD, NEW, FOSSIL, MUTED, INK = "#8a8a85", "#eb6834", "#3f3f46", "#8a8a85", "#161d1b"
CATS = ["Heating", "Transport", "Electricity"]
d = duckdb.sql(f"""SELECT year, category, kwh_per_day
                   FROM read_csv_auto('{sys.argv[1]}')""").fetchnumpy()
v = {(int(y), c): float(k) for y, c, k in zip(d["year"], d["category"], d["kwh_per_day"])}

fig, ax = plt.subplots(figsize=(8.6, 4.8))
x = np.arange(len(CATS)); w = 0.36
for i, (yr, col) in enumerate(((2008, OLD), (2026, NEW))):
    vals = [v[(yr, c)] for c in CATS]
    b = ax.bar(x + (i - 0.5) * w, vals, width=w, color=col, zorder=2,
               label=f"{yr}")
    for xi, val in zip(x + (i - 0.5) * w, vals):
        ax.text(xi, val + 1, f"{val:.0f}", ha="center", fontsize=9.5, color=INK)

# The fossil fuel burnt to make the electricity, drawn as an outline behind it.
for i, yr in enumerate((2008, 2026)):
    f = v[(yr, "Electricity fossil input")]
    xi = x[2] + (i - 0.5) * w
    ax.bar(xi, f, width=w, facecolor="none", edgecolor=FOSSIL, lw=1.5,
           linestyle=(0, (3, 2)), zorder=3)
    ax.text(xi, f + 1, f"{f:.0f}", ha="center", fontsize=9, color=FOSSIL)

ax.annotate("dashed outline: fossil fuel burnt\nto make that electricity",
            xy=(x[2] - 0.5 * w, 45), xytext=(x[2] - 1.05, 52),
            fontsize=9, color=FOSSIL,
            arrowprops=dict(arrowstyle="-", color=FOSSIL, lw=0.9))

ax.set_xticks(x); ax.set_xticklabels(CATS, fontsize=11)
ax.set_ylabel("kWh per day per person", fontsize=10.5)
ax.set_ylim(0, 62)
ax.grid(axis="y", color="#ededea", zorder=0); ax.set_axisbelow(True)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.legend(frameon=False, fontsize=10, loc="upper right")
ax.set_title("Cartoon Britain, 2008 and 2026", loc="left",
             fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("Electricity is the leg that changed. It takes 7 kWh/d of fossil fuel to make Britain's electricity now,\n"
            "against the 45 MacKay's cartoon assumed. Transport and heating are still burning what they burnt —\n"
            "which is the reverse of the order Part II proposes.",
            xy=(0, -0.155), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

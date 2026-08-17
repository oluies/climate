#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib", "numpy"]
# ///
"""Levelised cost as ranges, not points, with nuclear split by how it is financed.
Input: data-refresh/nuclear-costs.csv from `mill Refresh.scala nuclearCosts`."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

INK, MUTED = "#161d1b", "#8a8a85"
REN, NUC = "#1baf7a", "#4a3aa7"
# Ordering in SQL; fetchnumpy keeps this out of pandas.
d = duckdb.sql(f"""
    SELECT "group" AS grp, label, lo, mid, hi
    FROM read_csv_auto('{sys.argv[1]}') ORDER BY grp DESC, mid
""").fetchnumpy()
n = len(d["label"])

fig, ax = plt.subplots(figsize=(9.4, 5.6))
y = np.arange(n)[::-1]
for i, yy in enumerate(y):
    lo, mid, hi = float(d["lo"][i]), float(d["mid"][i]), float(d["hi"][i])
    col = NUC if d["grp"][i] == "Nuclear" else REN
    # Box from low to high with the central estimate marked: every one of these
    # is a range in its source, and the range is the point.
    ax.barh(yy, hi - lo, left=lo, height=0.46, color=col, alpha=0.30, zorder=2)
    ax.plot([lo, lo], [yy - 0.23, yy + 0.23], color=col, lw=1.6, zorder=3)
    ax.plot([hi, hi], [yy - 0.23, yy + 0.23], color=col, lw=1.6, zorder=3)
    ax.plot([mid, mid], [yy - 0.23, yy + 0.23], color=col, lw=3.0, zorder=4)
    # Value to the left: the capture diamonds sit to the right of the cheap rows.
    ax.text(lo - 5, yy, f"{mid:.0f}", va="center", ha="right", fontsize=8.8, color=INK)

# The span of the nuclear rows, which is the figure's argument.
nuc = [i for i in range(n) if d["grp"][i] == "Nuclear"]
lo_n, hi_n = min(float(d["lo"][i]) for i in nuc), max(float(d["hi"][i]) for i in nuc)
yn = [y[i] for i in nuc]
ax.annotate("", xy=(lo_n, min(yn) - 0.72), xytext=(hi_n, min(yn) - 0.72),
            arrowprops=dict(arrowstyle="<->", color=NUC, lw=1.3))
ax.text((lo_n * hi_n) ** 0.5, min(yn) - 1.02,
        f"the same technology, {lo_n:.0f}–{hi_n:.0f} — a factor of {hi_n / lo_n:.1f}",
        ha="center", va="top", fontsize=9.2, color=NUC)

ax.set_yticks(y); ax.set_yticklabels(d["label"], fontsize=9.5)
ax.set_ylim(min(y) - 1.9, max(y) + 0.7)
ax.set_xlim(0, 265)
ax.set_xlabel("€ per MWh", fontsize=10.5)
ax.grid(axis="x", color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.spines["bottom"].set_color("#c9c9c4")
ax.tick_params(axis="y", length=0)

h = [plt.Rectangle((0, 0), 1, 1, color=REN, alpha=0.45),
     plt.Rectangle((0, 0), 1, 1, color=NUC, alpha=0.45)]
# Below the axis: the plot area is full of bars and value labels.
ax.legend(h, ["Renewable, low–central–high", "Nuclear, low–central–high"],
          frameon=False, fontsize=9, ncol=2, loc="upper center",
          bbox_to_anchor=(0.5, -0.11))
ax.set_title("What it costs to build, as a range rather than a number",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("Every published levelised cost is a range, and quoting the midpoint hides what the range is made of. For the\n"
            "renewables it is mostly resource and site. For nuclear it is mostly the discount rate: the five nuclear rows are\n"
            "not five technologies but one, financed and built five ways. For what each technology's output actually\n"
            "earned on a market, against what it cost to build, see figure 28a.4 — which does that on one market in one currency.",
            xy=(0, -0.185), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

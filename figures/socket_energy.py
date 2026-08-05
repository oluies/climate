#!/usr/bin/env python3
"""Figure 20.21 remade: energy taken from the socket against distance driven.
Input: data-refresh/socket-energy.csv from `mill Refresh.scala socketEnergy`."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

INK, MUTED = "#161d1b", "#8a8a85"
NOW, THEN, PETROL = "#1baf7a", "#8a8a85", "#7a5c3e"
d = duckdb.sql(f"""
    SELECT label, kwh_per_100km AS v, era, note
    FROM read_csv_auto('{sys.argv[1]}') ORDER BY v
""").fetchnumpy()

LABEL_DY = {"MacKay's G-Wiz": -11, "Electric fleet average": 9}
XMAX, YMAX = 200.0, 45.0
x = np.array([0.0, XMAX])
fig, ax = plt.subplots(figsize=(9.2, 5.8))

for i, lab in enumerate(d["label"]):
    v = float(d["v"][i])
    then = d["era"][i] == 2008
    col, ls = (THEN, (0, (5, 3))) if then else (NOW, "-")
    y = x * v / 100.0
    # Stop each line where it leaves the axes so the labels sit on the frame.
    xe = min(XMAX, YMAX / v * 100.0)
    ax.plot([0, xe], [0, xe * v / 100.0], color=col, lw=2.2 if not then else 1.8,
            ls=ls, zorder=3)
    ye = xe * v / 100.0
    ha, dx, dy = ("left", 4, 0) if xe >= XMAX - 1 else ("center", 0, 6)
    # The G-Wiz and the modern fleet average sit on exactly the same line, which
    # is the figure's point; nudge them apart so both stay readable.
    dy += LABEL_DY.get(lab, 0)
    ax.annotate(f"{lab}  ({v:g})", xy=(xe, ye), xytext=(dx, dy),
                textcoords="offset points", ha=ha, va="center" if ha == "left" else "bottom",
                fontsize=8.8, color=INK if not then else MUTED, zorder=5)

# MacKay's petrol baseline, for scale: it leaves the top of the chart almost at once.
v = 80.0
xe = YMAX / v * 100.0
ax.plot([0, xe], [0, YMAX], color=PETROL, lw=2.0, zorder=3)
ax.annotate(f"Petrol car (80)\nleaves this chart\nat {xe:.0f} km", xy=(xe, YMAX),
            xytext=(6, -4), textcoords="offset points", fontsize=8.8,
            color=PETROL, va="top", zorder=5)

ax.set_xlim(0, XMAX + 62); ax.set_ylim(0, YMAX)
ax.set_xticks(range(0, int(XMAX) + 1, 50))
ax.set_xlabel("distance driven (km)", fontsize=10.5)
ax.set_ylabel("energy taken from the socket (kWh)", fontsize=10.5)
ax.grid(color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right"): ax.spines[s].set_visible(False)
for s in ("bottom", "left"): ax.spines[s].set_color("#c9c9c4")
ax.set_title("What it costs at the wall, 2026", loc="left",
             fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("The slope of each line is its consumption in kWh per 100 km, and the number in brackets is that slope. MacKay\n"
            "measured a real G-Wiz over nineteen recharges and got 21; the real-world average across 342 European electric\n"
            "cars is also 21. Eighteen years bought no reduction in energy per kilometre at the fleet level — the gain was\n"
            "taken as speed, size, safety and range. What is available at the efficient end is a different matter entirely.",
            xy=(0, -0.155), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

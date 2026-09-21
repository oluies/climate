#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib", "numpy"]
# ///
"""Figure 20.21 remade, with MacKay's own chart folded into it.
His figure plots 19 G-Wiz recharges with guide lines at 16, 21 and 33 kWh per
100 km; those three numbers are carried here, the 21 as a line and the other two
as the edges of a band, so his measurement and today's cars share one pair of axes.
Input: data-refresh/socket-energy.csv from `mill Refresh.scala socketEnergy`."""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

INK, MUTED = "#161d1b", "#8a8a85"
NOW, THEN, PETROL, BAND = "#1baf7a", "#8a8a85", "#7a5c3e", "#d8d8d3"
rows = duckdb.sql(f"""
    SELECT label, kwh_per_100km AS v, era, kind, note
    FROM read_csv_auto('{sys.argv[1]}') ORDER BY v
""").fetchnumpy()
keep = rows["kind"] == "line"
d = {k: rows[k][keep] for k in rows}
band = sorted(float(v) for v, k in zip(rows["v"], rows["kind"]) if k == "band")

LABEL_DY = {"MacKay's G-Wiz": -11, "Electric fleet average": 9}
XMAX, YMAX = 200.0, 45.0
x = np.array([0.0, XMAX])
fig, ax = plt.subplots(figsize=(9.2, 5.8))

# MacKay's own figure, folded in: the wedge between his best and his worst
# recharge of the same car. Its width is the point - one vehicle, one driver,
# one city, and a factor of two between the good days and the bad ones.
lo, hi = band
xb = np.linspace(0, XMAX, 200)
ax.fill_between(xb, xb * lo / 100.0, np.minimum(xb * hi / 100.0, YMAX),
                color=BAND, alpha=0.55, lw=0, zorder=1)
for v in band:
    # The edges carry no label: the top one would land under the pickup's, and
    # the annotation below names both numbers anyway.
    xe = min(XMAX, YMAX / v * 100.0)
    ax.plot([0, xe], [0, xe * v / 100.0], color=MUTED, lw=0.9, alpha=0.8, zorder=2)
ax.annotate("The grey wedge is MacKay's own spread: across nineteen recharges of\n"
            "one G-Wiz his best was 16 kWh per 100 km and his worst 33.",
            xy=(122, 6.4), fontsize=8.8, color=MUTED, va="top", zorder=5)

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
ax.annotate("This replaces MacKay's own figure 20.21 and carries its three numbers. The slope of each line is its consumption in\n"
            "kWh per 100 km, and the number in brackets is that slope. MacKay measured a real G-Wiz over nineteen recharges and\n"
            "got 21; the real-world average across 342 European electric cars is also 21. Eighteen years bought no reduction in\n"
            "energy per kilometre at the fleet level — the gain was taken as speed, size, safety and range. What is available at\n"
            "the efficient end is a different matter entirely, and the width of his wedge is a warning about any single figure here.",
            xy=(0, -0.155), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None}); print("wrote", sys.argv[2])

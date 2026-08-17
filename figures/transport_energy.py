#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib"]
# ///
"""Figure 20.23 remade: passenger transport energy against speed, 2008 and 2026.
Input: data-refresh/transport-energy.csv from `mill Refresh.scala transportEnergy`."""
import sys, duckdb, matplotlib.pyplot as plt

INK, MUTED = "#161d1b", "#8a8a85"
CAT = {"land": "#1baf7a", "water": "#2a78d6", "air": "#eb6834"}
# Ordering and the era split stay in SQL; fetchnumpy keeps this out of pandas.
d = duckdb.sql(f"""
    SELECT era::INT AS era, category, mode, speed_kmh, kwh, kwh_lo, kwh_hi, fill
    FROM read_csv_auto('{sys.argv[1]}') ORDER BY era, kwh
""").fetchnumpy()

# Hand-placed so nothing collides; offsets are in points from the marker.
OFF = {
    "Bicycle": (0, 10, "center"), "Full 8-car train": (0, 10, "center"),
    "Coach (full)": (0, -13, "center"), "Croydon tram": (-8, 0, "right"),
    "G-Wiz (real use)": (-8, 0, "right"), "London bus": (0, 10, "center"),
    "Car (1 occupant)": (10, 9), "Honda FCX (hydrogen)": (10, -9),
    "BMW Hydrogen 7": (0, 10, "center"), "747 (full)": (-9, 0, "right"),
    "Liner (Rijndam)": (-8, 0, "right"), "E-bike": (0, -13, "center"),
    "787/A350 (full)": (9, 0), "Candela C-8 (2 aboard)": (10, 0),
    "Diesel ferry it replaced": (-14, 0, "right"),
}
# Six modes land within a factor of two of each other around 40-50 km/h, which is
# the interesting coincidence but makes an unreadable pile. These get leader lines
# out to the empty band on the right; positions are in data coordinates.
CALLOUT = {
    "EV (real-world average)": (250, 24.0), "Candela P-12 (30 seats)": (250, 17.0),
    "Efficient EV (Model 3)": (250, 12.0), "Electric car (Roadster)": (250, 8.5),
    "Underground": (250, 6.0), "Candela C-8 (6 aboard)": (250, 4.2),
}
# CALLOUT wins over OFF in the loop below, so an entry in both would be a silent no-op,
# and a key matching no mode (a rename in Refresh.scala) would silently fall back to the default.
assert not (OFF.keys() & CALLOUT.keys()), "a mode is in both OFF and CALLOUT"
stale = (OFF.keys() | CALLOUT.keys()) - set(d["mode"])
assert not stale, f"label placement for modes not in the data: {sorted(stale)}"

fig, ax = plt.subplots(figsize=(9.6, 6.4))
ax.set_xscale("log"); ax.set_yscale("log")
ax.grid(True, which="major", color="#ededea", lw=0.9, zorder=0)
ax.grid(True, which="minor", color="#f5f5f2", lw=0.6, zorder=0)

for i, mode in enumerate(d["mode"]):
    era, cat, fill = d["era"][i], d["category"][i], d["fill"][i]
    x, y = float(d["speed_kmh"][i]), float(d["kwh"][i])
    col = MUTED if era == 2008 else CAT[cat]
    lo, hi = float(d["kwh_lo"][i]), float(d["kwh_hi"][i])
    if hi > 0:  # a band rather than a number
        ax.plot([x, x], [lo, hi], color=col, lw=2.0, alpha=0.5, zorder=3)
        for e in (lo, hi):
            ax.plot([x * 0.94, x * 1.06], [e] * 2, color=col, lw=2.0, alpha=0.5, zorder=3)
    ax.plot(x, y, marker="o", ms=8.5, zorder=4, color=col,
            mfc=col if fill == "typical" else "white",
            mec=col, mew=1.8, ls="none")
    lbl = INK if era == 2026 else MUTED
    if mode in CALLOUT:
        ax.annotate(mode, xy=(x, y), xytext=CALLOUT[mode], textcoords="data",
                    fontsize=8.6, color=lbl, ha="left", va="center", zorder=5,
                    arrowprops=dict(arrowstyle="-", color="#c9c9c4", lw=0.8,
                                    shrinkA=2, shrinkB=6))
    else:
        o = OFF.get(mode, (8, 0))
        ax.annotate(mode, xy=(x, y), xytext=o[:2], textcoords="offset points",
                    fontsize=8.6, color=lbl, zorder=5,
                    ha=o[2] if len(o) > 2 else "left", va="center")

ax.set_xlim(7, 2200); ax.set_ylim(0.4, 420)
ax.set_xticks([10, 20, 50, 100, 200, 500, 1000])
ax.set_xticklabels(["10", "20", "50", "100", "200", "500", "1000"])
ax.set_yticks([1, 2, 5, 10, 20, 50, 100, 200])
ax.set_yticklabels(["1", "2", "5", "10", "20", "50", "100", "200"])
ax.set_xlabel("speed (km/h)", fontsize=10.5)
ax.set_ylabel("energy (kWh per 100 passenger-km)", fontsize=10.5)
for s in ("top", "right"): ax.spines[s].set_visible(False)
for s in ("bottom", "left"): ax.spines[s].set_color("#c9c9c4")

h = [plt.Line2D([0], [0], marker="o", ls="none", ms=8.5, color=MUTED, mfc=MUTED),
     plt.Line2D([0], [0], marker="o", ls="none", ms=8.5, color=CAT["land"], mfc=CAT["land"]),
     plt.Line2D([0], [0], marker="o", ls="none", ms=8.5, color=CAT["water"], mfc=CAT["water"]),
     plt.Line2D([0], [0], marker="o", ls="none", ms=8.5, color=CAT["air"], mfc=CAT["air"]),
     plt.Line2D([0], [0], marker="o", ls="none", ms=8.5, color=INK, mfc="white", mew=1.8)]
# Below the axis: the plot area is full of labelled points.
ax.legend(h, ["MacKay's 2008 figures", "2026: land", "2026: water", "2026: air",
              "hollow = every seat used"],
          frameon=False, fontsize=9, ncol=5, loc="upper center",
          bbox_to_anchor=(0.5, -0.10), handletextpad=0.4, columnspacing=1.4)
ax.set_title("Energy requirements of passenger transport, 2008 and 2026",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("Both axes are logarithmic, unlike MacKay's original. The 2008 points are his own figures from chapters 5 and 20.\n"
            "The water points are the corner that moved: hydrofoiling lifts the hull clear, and a 25-knot passenger vessel now\n"
            "sits where in 2008 only trains and coaches could. The Candela figures are computed from the manufacturer's own\n"
            "battery capacity and quoted range, so they are consumption floors; the diesel band is backed out of the same\n"
            "manufacturer's savings claim, which is quoted at 66%, 80% and 84% by different sources.",
            xy=(0, -0.175), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

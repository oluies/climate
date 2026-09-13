#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure C.5a: MacKay's figure C.5 redrawn — the thrust a jumbo jet needs
against its speed, as ordinary drag plus the drag that comes with making lift.

Input: data-refresh/plane-thrust.csv from `mill Refresh.scala
appendixBCModelCurves`."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
rows = [(float(r["speed_ms"]), float(r["drag_kn"]), float(r["lift_kn"]),
         float(r["total_kn"])) for r in csv.DictReader(open(sys.argv[1]))]
rows.sort()
best = min(rows, key=lambda r: r[3])

fig, ax = plt.subplots(figsize=(7.6, 4.6))
ax.plot([r[0] for r in rows], [r[3] for r in rows], color="#bf4433", lw=2.2,
        label="Total thrust required", zorder=4)
ax.plot([r[0] for r in rows], [r[1] for r in rows], color="#2f7d4f", lw=1.5,
        label="Ordinary drag, grows as speed²", zorder=3)
ax.plot([r[0] for r in rows], [r[2] for r in rows], color="#1f6f9c", lw=1.5,
        label="Lift-related drag, falls as speed²", zorder=3)
ax.plot([best[0]], [best[3]], "o", color="#bf4433", ms=5, zorder=5)
ax.annotate(f"optimum {best[0]:.0f} m/s, {best[3]:.0f} kN",
            xy=(best[0], best[3]), xytext=(best[0] + 18, best[3] - 42),
            fontsize=9, color=INK,
            arrowprops=dict(arrowstyle="->", color=MUTED, lw=0.9))
ax.set_xlim(100, 400)
ax.set_ylim(0, 260)
ax.set_xlabel("Speed, m/s", fontsize=10.5)
ax.set_ylabel("Thrust, kN", fontsize=10.5)
ax.grid(color=GRID, lw=0.8)
ax.set_axisbelow(True)
for s in ("top", "right"):
    ax.spines[s].set_visible(False)
for s in ("bottom", "left"):
    ax.spines[s].set_color("#c9c9c4")
ax.tick_params(length=0, labelsize=9.5)
ax.legend(frameon=False, fontsize=9.5, loc="upper center")
ax.set_title("Why a plane has a speed it wants to fly at",
             loc="left", fontsize=13, fontweight="bold", pad=12, color=INK)
ax.annotate("The numbers are the ones MacKay's own caption to this figure prints: a 747 of 319 tonnes with a 64.4 m wingspan, frontal\n"
            "area 180 m², drag coefficient 0.03, in air of 0.41 kg/m³ — the density at ten kilometres. The sausage of air the wings throw\n"
            "down has the area of a square of the wingspan, which figure C.7 states. His caption gives the optimum as 220 m/s.",
            xy=(0, -0.20), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
print("wrote", sys.argv[2], f"| optimum {best[0]:.0f} m/s at {best[3]:.0f} kN")

#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure 26.16a: what a Swedish battery earns, by market, against what it costs.

Everything is euros per megawatt of connected power per year, and everything is
money available to cover capital -- so the arbitrage bars are net of the cost of
charging, which the capacity payments do not have. The grouping is the argument:
the left pair is paid for energy delivered, the right pair for power available.
Input: data-refresh/se-battery-revenue.csv."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
ENERGY, POWER, BAND = "#bf4433", "#1f9bc4", "#8a8a85"

d = {r["post"]: float(r["varde"]) for r in csv.DictReader(open(sys.argv[1]))}
USD, H, LIFE, WACC, OM = d["usd_per_eur"], 4.0, 15, 0.07, 0.02
crf = WACC * (1 + WACC) ** LIFE / ((1 + WACC) ** LIFE - 1)
cost = lambda e: e * 1000 * H * (crf + OM) / 1000        # tusen EUR per MW och ar
c_lo, c_hi = cost(d["capex_global_usd_kwh"] / USD), cost(d["capex_sverige_eur_kwh"])
mwh = 365 * 0.90 * 0.88 * H                              # levererade MWh per MW och ar

# Netto efter laddning, alltsa pengar kvar till kapitalet - samma storhet som
# kapacitetsintakterna, som inte har nagon laddningskostnad.
arb = lambda i, k: (i - k) * mwh / 1000 + c_hi
# Namn, undre varde, ovre varde (None = enkel stapel), text inuti stapeln.
GROUPS = [
    ("Paid for energy delivered", ENERGY,
     [("Arbitrage SE3", arb(d["arb_se3_intakt"], d["arb_se3_kostnad"]), None, "one cycle a day"),
      ("Arbitrage SE4", arb(d["arb_se4_intakt"], d["arb_se4_kostnad"]), None, "one cycle a day")]),
    ("Paid for power available", POWER,
     [("FCR-D up", 7.0 * 0.9 * 8760 / 1000, None, "90% of hours"),
      ("aFRR up SE3", 29.4 * 0.4 * 8760 / 1000, 29.4 * 0.9 * 8760 / 1000,
       "40% to 90% of hours")]),
]

XL, XR = -0.62, 4.30                                     # plotkant och kostnadstextens kant
fig, ax = plt.subplots(figsize=(9.6, 5.4))
ax.axhspan(c_lo, c_hi, xmax=(XR - 0.16 - XL) / (5.55 - XL), color=BAND, alpha=0.18, zorder=1)
for y in (c_lo, c_hi):
    ax.plot([XL, XR - 0.16], [y, y], color=BAND, lw=1, zorder=2)
ax.text(XR, c_hi + 22, "what it costs to own", fontsize=10, color="#5c5c58", va="bottom")
ax.text(XR, c_hi + 3, f"{c_hi:.0f}  Swedish capex", fontsize=9, color="#5c5c58", va="bottom")
ax.text(XR, c_lo - 3, f"{c_lo:.0f}  BNEF global capex", fontsize=9, color="#5c5c58", va="top")

x = 0.0; ticks = []; labels = []
for gname, col, bars in GROUPS:
    x0 = x
    for name, lo, hi, inside in bars:
        if hi is None:
            ax.bar(x, lo, width=0.56, color=col, zorder=3)
            ax.text(x, lo + 5, f"{lo:.0f}", ha="center", fontsize=11.5,
                    fontweight="bold", color=INK)
            ax.text(x, lo / 2, inside, rotation=90, ha="center", va="center",
                    fontsize=8, color="white", zorder=5)
        else:
            ax.bar(x, hi - lo, bottom=lo, width=0.56, color=col, alpha=0.32, zorder=3)
            for y in (lo, hi):
                ax.plot([x - 0.28, x + 0.28], [y, y], color=col, lw=2.4, zorder=4)
            ax.text(x, hi + 5, f"{lo:.0f}–{hi:.0f}", ha="center", fontsize=11.5,
                    fontweight="bold", color=INK)
            ax.text(x, (lo + hi) / 2, inside, rotation=90, ha="center", va="center",
                    fontsize=8, color="#3c6a7d", zorder=5)
        ticks.append(x); labels.append(name); x += 1
    ax.text((x0 + x - 1) / 2, -0.125, gname, ha="center", fontsize=10.5,
            fontweight="bold", color=col, transform=ax.get_xaxis_transform())
    x += 0.4

ax.set_xticks(ticks); ax.set_xticklabels(labels, fontsize=10)
ax.set_ylabel("thousand euros per MW per year, after charging", fontsize=10.5)
ax.set_xlim(XL, 5.55); ax.set_ylim(-6, 262)
ax.set_yticks([0, 50, 100, 150, 200, 250])
ax.grid(axis="y", color=GRID, lw=1); ax.set_axisbelow(True)
ax.tick_params(length=0, labelsize=9.5)
for sp in ("top", "right", "bottom"):
    ax.spines[sp].set_visible(False)
ax.spines["left"].set_bounds(0, 250); ax.spines["left"].set_color("#c9c9c4")
ax.plot([XL, XR - 0.16], [0, 0], color="#c9c9c4", lw=1)
ax.set_title("Figure 26.16a. A Swedish battery is paid for its power, not its energy.",
             loc="left", fontsize=12.5, fontweight="bold", pad=40)
ax.annotate("Day-ahead arbitrage lands either side of what the machine costs to own. FCR-D alone does not cover it; aFRR\n"
            "does. Both reserve markets pay for power held ready, so neither rewards a fifth hour of storage.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("Capacity prices: Svenska kraftnät monthly reports, 2025. Arbitrage: Energy-Charts SE3/SE4 2025, net of charging. Capex:\n"
            "BloombergNEF December 2025 survey and a figure reported for Swedish projects. Levelising assumptions in the note.",
            xy=(0, -0.26), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

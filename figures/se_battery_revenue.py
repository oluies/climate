#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas"]
# ///
"""Figure 26.16a: what a Swedish battery earns, by market, against what it costs.
Everything in euros per megawatt of connected power per year, because that is
the unit the reserve markets pay in -- which is the point the figure makes.
Input: data-refresh/se-battery-revenue.csv."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
ARB, FCR, AFRR = "#bf4433", "#dfa03a", "#1f9bc4"

d = {r["post"]: float(r["varde"]) for r in csv.DictReader(open(sys.argv[1]))}
USD = d["usd_per_eur"]; HOURS = 4.0; LIFE, WACC, OM = 15, 0.07, 0.02
crf = WACC * (1 + WACC) ** LIFE / ((1 + WACC) ** LIFE - 1)
def annual(eur_kwh): return eur_kwh * 1000 * HOURS * (crf + OM)
cost_glob = annual(d["capex_global_usd_kwh"] / USD)
cost_se = annual(d["capex_sverige_eur_kwh"])

# Arbitrage: nettot per levererad MWh gangrat med arets levererade MWh per MW.
delivered = 365 * 0.90 * 0.88 * HOURS          # MWh per MW och ar
arb_se3 = (d["arb_se3_intakt"] - d["arb_se3_kostnad"]) * delivered + cost_se
arb_se4 = (d["arb_se4_intakt"] - d["arb_se4_kostnad"]) * delivered + cost_se

BARS = [
    ("Arbitrage\nSE3", arb_se3, ARB),
    ("Arbitrage\nSE4", arb_se4, ARB),
    ("FCR-D up\n7 EUR/MW/h, 90%", 7.0 * 0.9 * 8760, FCR),
    ("aFRR up SE3\n29 EUR/MW/h, 40%", 29.4 * 0.4 * 8760, AFRR),
    ("aFRR up SE3\n29 EUR/MW/h, 90%", 29.4 * 0.9 * 8760, AFRR),
]
fig, ax = plt.subplots(figsize=(9.2, 5.4))
for i, (lab, v, c) in enumerate(BARS):
    ax.bar(i, v / 1000, width=0.62, color=c, zorder=3)
    ax.text(i, v / 1000 + 5, f"{v/1000:.0f}", ha="center", fontsize=11.5,
            fontweight="bold", color=INK)
for y, lab, ls in ((cost_glob, f"cost at BNEF's global capex, {cost_glob/1000:.0f}", (0, (5, 3))),
                   (cost_se, f"cost at the Swedish capex, {cost_se/1000:.0f}", "-")):
    ax.axhline(y / 1000, color=INK, lw=1.5, ls=ls, zorder=5)
    ax.text(-0.44, y / 1000 + 4, lab, fontsize=9.5, color=INK, ha="left")
ax.set_xticks(range(len(BARS)))
ax.set_xticklabels([b[0] for b in BARS], fontsize=9.5)
ax.set_ylabel("thousand euros per MW of connected power, per year", fontsize=10.5)
ax.set_ylim(-30, 285)
ax.grid(axis="y", color=GRID, lw=1); ax.set_axisbelow(True)
ax.tick_params(length=0, labelsize=9.5)
for sp in ("top", "right", "bottom"):
    ax.spines[sp].set_visible(False)
ax.axhline(0, color="#c9c9c4", lw=1)
ax.set_title("Figure 26.16a. A Swedish battery is paid for its power, not its energy.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
ax.annotate("Day-ahead arbitrage does not cover the capital. The reserve markets do — and they pay\n"
            "per megawatt of availability, so nothing in them rewards a fifth hour of storage.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("Capacity prices: Svenska kraftnät monthly reports, 2025. Arbitrage: Energy-Charts SE3/SE4 2025. "
            "Capex: BloombergNEF\nDecember 2025 survey ($110/kWh, four-hour turnkey) and a figure reported for "
            "Swedish projects (€172/kWh).",
            xy=(0, -0.20), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

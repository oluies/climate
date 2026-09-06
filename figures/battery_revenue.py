#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib"]
# ///
"""Figure 26.16a: what a battery is paid by market, in Sweden and in Britain,
against what it costs to own.

Everything is thousand euros per megawatt of connected power per year, and
everything is money available to cover capital -- the arbitrage bars are net of
the cost of charging, which the capacity payments do not have. The grouping is
the argument: the left three are paid for energy delivered, the right four for
power held available.

Inputs: data-refresh/battery-prices.csv (computed by `mill Refresh.scala
chapter-26-batteries`) and data-refresh/se-battery-revenue.csv (capital costs
and Svenska kraftnat's capacity prices, both hand-entered)."""
import sys, csv, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Patch

INK, MUTED, GRID, BAND = "#161d1b", "#8a8a85", "#ededea", "#8a8a85"
SE, GB = "#bf4433", "#1f6f9c"

read = lambda p: {r["post"]: float(r["varde"]) for r in csv.DictReader(open(p))}
px, d = read(sys.argv[1]), read(sys.argv[2])
GBP, USD = px["gbp_per_eur"], px["usd_per_eur"]
H, LIFE, WACC, OM, ETA, DOD = 4.0, 15, 0.07, 0.02, 0.88, 0.90

crf = WACC * (1 + WACC) ** LIFE / ((1 + WACC) ** LIFE - 1)
cost = lambda e: e * H * (crf + OM)                      # tusen EUR per MW och ar
c_lo, c_hi = cost(d["capex_global_usd_kwh"] / USD), cost(d["capex_sverige_eur_kwh"])
mwh = 365 * DOD * ETA * H                                # levererade MWh per MW och ar

# Pengar kvar till kapitalet efter laddning, alltsa samma storhet som
# kapacitetsintakterna, som inte har nagon laddningskostnad.
arb = lambda lo, hi: (hi - lo / ETA) * mwh / 1000
cap = lambda p, a: p * a * 8760 / 1000
fcrd = sum(v for k, v in d.items() if k.startswith("fcrd_upp_")) / \
       sum(1 for k in d if k.startswith("fcrd_upp_"))

# Namn, undre varde, ovre varde (None = enkel stapel), farg, text inuti stapeln.
GROUPS = [
    ("Paid for energy delivered", [
        ("SE3", arb(px["se3_lag"], px["se3_hog"]), None, SE, "one cycle a day"),
        ("SE4", arb(px["se4_lag"], px["se4_hog"]), None, SE, "one cycle a day"),
        ("Britain", arb(px["gb_lag"] / GBP, px["gb_hog"] / GBP), None, GB, "one cycle a day")]),
    ("Paid for power available", [
        ("FCR-D up", cap(fcrd, 0.9), None, SE, ""),
        ("aFRR up", cap(d["afrr_upp_se3_mars"], 0.4),
         cap(d["afrr_upp_se3_mars"], 0.9), SE, ""),
        ("DC low", cap(px["gb_dcl"] / GBP, 0.9), None, GB, ""),
        ("DR low", cap(px["gb_drl"] / GBP, 0.4),
         cap(px["gb_drl"] / GBP, 0.9), GB, "")]),
]

XL, XR = -0.62, 7.55                                     # plotkant och kostnadstextens kant
fig, ax = plt.subplots(figsize=(11.0, 5.6))
ax.axhspan(c_lo, c_hi, xmax=(XR - 0.16 - XL) / (9.2 - XL), color=BAND, alpha=0.18, zorder=1)
for y in (c_lo, c_hi):
    ax.plot([XL, XR - 0.16], [y, y], color=BAND, lw=1, zorder=2)
ax.text(XR, c_hi + 22, "what it costs to own", fontsize=10, color="#5c5c58", va="bottom")
ax.text(XR, c_hi + 3, f"{c_hi:.0f}  Swedish capex", fontsize=9, color="#5c5c58", va="bottom")
ax.text(XR, c_lo - 3, f"{c_lo:.0f}  BNEF global capex", fontsize=9, color="#5c5c58", va="top")

x = 0.0; ticks = []; labels = []
for gname, bars in GROUPS:
    x0 = x
    for name, lo, hi, col, inside in bars:
        if hi is None:
            ax.bar(x, lo, width=0.6, color=col, zorder=3)
            ax.text(x, lo + 5, f"{lo:.0f}", ha="center", fontsize=11,
                    fontweight="bold", color=INK)
        else:
            ax.bar(x, hi - lo, bottom=lo, width=0.6, color=col, alpha=0.32, zorder=3)
            for y in (lo, hi):
                ax.plot([x - 0.3, x + 0.3], [y, y], color=col, lw=2.4, zorder=4)
            ax.text(x, hi + 5, f"{lo:.0f}–{hi:.0f}", ha="center", fontsize=11,
                    fontweight="bold", color=INK)
        if inside:
            ax.text(x, lo / 2, inside, rotation=90, ha="center", va="center",
                    fontsize=8, color="white", zorder=5)
        ticks.append(x); labels.append(name); x += 1
    ax.text((x0 + x - 1) / 2, -0.135, gname, ha="center", fontsize=10.5,
            fontweight="bold", color=INK, transform=ax.get_xaxis_transform())
    x += 0.4

ax.set_xticks(ticks); ax.set_xticklabels(labels, fontsize=9.5, linespacing=1.5)
ax.set_ylabel("thousand euros per MW per year, after charging", fontsize=10.5)
ax.set_xlim(XL, 9.2); ax.set_ylim(-6, 262)
ax.set_yticks([0, 50, 100, 150, 200, 250])
ax.grid(axis="y", color=GRID, lw=1); ax.set_axisbelow(True)
ax.tick_params(length=0, labelsize=9.5)
for sp in ("top", "right", "bottom"):
    ax.spines[sp].set_visible(False)
ax.spines["left"].set_bounds(0, 250); ax.spines["left"].set_color("#c9c9c4")
ax.plot([XL, XR - 0.16], [0, 0], color="#c9c9c4", lw=1)
ax.legend(handles=[Patch(facecolor=SE, label="Sweden"), Patch(facecolor=GB, label="Britain")],
          frameon=False, fontsize=10, loc="upper left", bbox_to_anchor=(0.005, 0.99))
ax.set_title("Figure 26.16a. A battery is paid for its power, not its energy.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
ax.annotate("Day-ahead arbitrage lands either side of what the machine costs to own, in both countries. Sweden's reserve markets pay roughly twice\n"
            "what Britain's nearest equivalents do, and only Sweden's aFRR covers the whole cost at any availability. No reserve market pays for a fifth hour.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("Arbitrage: mean over 2025 of each day's cheapest and dearest four hours, from Energy-Charts (SE3, SE4) and Elexon's market index (Britain), net of charging.\n"
            "Capacity: Svenska kraftnät's monthly reports and NESO's auction results for 2025, drawn at 90% availability and, for the two regulation products, over a\n"
            "40% to 90% range. Capex: BloombergNEF December 2025 and a figure reported for Swedish projects, levelised on the assumptions in the note.",
            xy=(0, -0.30), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[3], format="svg", bbox_inches="tight"); print("wrote", sys.argv[3])

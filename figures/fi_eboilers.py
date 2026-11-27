#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Finnish district-heating electric boilers against the tax on their electricity.
Input: the three CSVs from `mill Refresh.scala chapter28aFiBoilers`.
Usage: python fi_eboilers.py <fi-eboilers.csv> <fi-eboiler-capacity.csv> <fi-se-boiler-tax.csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

FI, SE, FEE, MUTED, TEXT = "#2a78d6", "#eb6834", "#a9c8ee", "#8a8a85", "#161d1b"

use = pd.read_csv(sys.argv[1])
cap = pd.read_csv(sys.argv[2]).set_index("start_year").mw
tax = pd.read_csv(sys.argv[3])

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, (a, b) = plt.subplots(1, 2, figsize=(10.4, 4.6), gridspec_kw={"width_ratios": [1.25, 1], "wspace": 0.42})

# Left: electricity into Finnish district-heating boilers, a year at a time.
a.bar(use.year, use.eboiler_gwh, color=FI, width=0.62, zorder=2)
for r in use.itertuples():
    if r.eboiler_gwh > 300:
        a.text(r.year, r.eboiler_gwh + 60, f"{r.eboiler_gwh:,.0f}".replace(",", " "),
               ha="center", va="bottom", fontsize=9, color=TEXT, fontweight="bold")
        a.text(r.year, r.eboiler_gwh - 70, f"{r.eboiler_gwh / r.dh_delivery_gwh * 100:.0f}%",
               ha="center", va="top", fontsize=8.5, color="white")
a.axvline(2022.5, color=MUTED, lw=1, ls="--", zorder=1)
a.text(2022.4, 2700, "tax cut\n1 July 2022", ha="right", va="top", fontsize=8.5, color=MUTED)
# The build-out under the axis: MW of boilers that started in each year.
for y in use.year:
    if y in cap.index and y >= 2022:
        a.text(y, -0.16, f"+{cap[y]:.0f}", transform=a.get_xaxis_transform(),
               ha="center", va="top", fontsize=8, color=FI)
a.text(2018.6, -0.16, "MW of boilers\nstarted that year", transform=a.get_xaxis_transform(),
       ha="left", va="top", fontsize=8, color=MUTED)
a.set_xticks(use.year)
a.set_ylim(0, 3200)
a.set_ylabel("GWh of electricity a year", fontsize=10)
a.yaxis.set_major_formatter(lambda v, _: f"{v:,.0f}".replace(",", " "))
a.grid(axis="x", visible=False)
for s in ("top", "right"): a.spines[s].set_visible(False)
a.set_title("Finland: electricity into district-heating boilers", loc="left", fontsize=10.5,
            fontweight="bold", pad=10)

# Right: what a megawatt-hour of that electricity is taxed.
y = list(range(len(tax)))[::-1]
for yi, r in zip(y, tax.itertuples()):
    c = FI if r.label.startswith("Finland") else SE
    b.barh(yi, r.tax_eur_mwh, color=c, height=0.55, zorder=2)
    end = r.tax_eur_mwh
    if r.fee_eur_mwh > 0:   # the transmission fee that stays after the tax cut
        b.barh(yi, r.fee_eur_mwh, left=r.tax_eur_mwh, color=FEE, height=0.55, zorder=2, hatch="///",
               edgecolor="white", lw=0)
        end += r.fee_eur_mwh
        b.text(end + 0.8, yi, f"€{r.tax_eur_mwh:.2f} tax\n+ €{r.fee_eur_mwh:.1f} grid fee",
               va="center", fontsize=8.5, color=c)
    else:
        b.text(end + 0.8, yi, f"€{r.tax_eur_mwh:.1f}", va="center", fontsize=9, color=c, fontweight="bold")
b.set_yticks(y)
b.set_yticklabels(tax.label, fontsize=9.5)
b.set_xlim(0, 50)
b.set_xlabel("euros per MWh of electricity", fontsize=10)
b.grid(axis="y", visible=False)
for s in ("top", "right", "left"): b.spines[s].set_visible(False)
b.tick_params(axis="y", length=0)
b.set_title("The tax on the boilers' electricity", loc="left", fontsize=10.5, fontweight="bold", pad=10)

fig.suptitle("Same hardware, different tax", x=0.07, ha="left", fontsize=12.5, fontweight="bold", y=1.03)
fig.text(0.07, -0.1, "Bar labels: the boilers' electricity as a share of district heat delivered. The hatched bar is the heating-season\n"
         "transmission fee left after the Finnish cut; Swedish bars are the standard energy tax alone, at 11 kronor to the euro.",
         fontsize=9, color=MUTED, ha="left", va="top")
fig.savefig(sys.argv[4], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[4])

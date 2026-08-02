#!/usr/bin/env python3
"""Where a heat pump saves money: the electricity-to-gas price ratio against the
seasonal performance factor needed to break even.
Input: data-refresh/heatpump-breakeven.csv from `mill Refresh.scala chapter07`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

WIN, LOSE, BAND, MUTED = "#1baf7a", "#e34948", "#eda100", "#8a8a85"
NAME = {"AT":"Austria","BE":"Belgium","BG":"Bulgaria","CZ":"Czechia","DK":"Denmark",
        "DE":"Germany","EE":"Estonia","IE":"Ireland","EL":"Greece","ES":"Spain",
        "FR":"France","HR":"Croatia","IT":"Italy","LV":"Latvia","LT":"Lithuania",
        "LU":"Luxembourg","HU":"Hungary","NL":"Netherlands","PL":"Poland","PT":"Portugal",
        "RO":"Romania","SI":"Slovenia","SK":"Slovakia","FI":"Finland","SE":"Sweden",
        "UK":"United Kingdom","NO":"Norway","EU27_2020":"EU average","RS":"Serbia",
        "TR":"Türkiye","UA":"Ukraine","MD":"Moldova","MK":"North Macedonia",
        "BA":"Bosnia","ME":"Montenegro","GE":"Georgia","AL":"Albania","XK":"Kosovo",
        "IS":"Iceland","LI":"Liechtenstein","MT":"Malta","CY":"Cyprus"}
SPF = 3.0        # the "ideal" case: a well-sized unit in an EPC-C-or-better home
MEASURED = 2.65  # mean SPF actually measured for air-source units, EST RHPP field trial
df = pd.read_csv(sys.argv[1])
df = df[df.country.isin(NAME)].copy()
df["name"] = df.country.map(NAME)
df = df.sort_values("ratio")
col = [WIN if r < MEASURED else LOSE for r in df.ratio]

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.6, 0.28 * len(df) + 2.2))
bars = ax.barh(df.name, df.ratio, color=col, height=0.66)
for b, v in zip(bars, df.ratio):
    ax.text(v + 0.06, b.get_y() + b.get_height() / 2, f"{v:.1f}",
            va="center", fontsize=8.5, color="#161d1b")

ax.axvspan(3, 5, color=BAND, alpha=0.16, zorder=0)
ax.axvline(SPF, color="#161d1b", lw=1.3, zorder=1)
ax.axvline(MEASURED, color="#161d1b", lw=1.1, ls=(0, (4, 3)), zorder=1)
ax.annotate("measured UK\naverage, 2.65", xy=(MEASURED, len(df) - 0.2),
            xytext=(-6, 0), textcoords="offset points", ha="right", va="bottom",
            fontsize=8.5, color=MUTED)
# Top rows are the shortest bars, so the right-hand side is clear up there.
ax.annotate("a heat pump at SPF 3\nbreaks even here", xy=(SPF, 3.0),
            xytext=(-6, 0), textcoords="offset points", ha="right",
            fontsize=8.5, color="#161d1b")
ax.annotate("range of modern\nheat pumps (3–5×)", xy=(4, 0.4), ha="center",
            fontsize=8.5, color="#8a6d00")

ax.set_xlabel("household electricity price ÷ household gas price (per kWh, taxes included)", fontsize=10.5)
ax.set_xlim(0, 6.4)
ax.grid(axis="y", visible=False)
for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
ax.tick_params(axis="y", length=0, labelsize=8.5)
ax.invert_yaxis()
h = [plt.Rectangle((0, 0), 1, 1, color=c) for c in (WIN, LOSE)]
ax.legend(h, ["Heat pump cheaper at the measured SPF of 2.65", "Gas cheaper"],
          frameon=False, fontsize=9, loc="upper right", bbox_to_anchor=(1, 0.93))
n_win = int((df.ratio < MEASURED).sum())
ax.set_title("Where a heat pump is cheaper to run than a gas boiler",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate(f"A heat pump wins when its seasonal performance factor beats the price ratio. At the 2.65 actually measured\n"
            f"across UK field trials it is cheaper in {n_win} of {len(df)} countries; at the 3.0 of a well-sized unit in a well-insulated\n"
            f"house, in {int((df.ratio < SPF).sum())}. The question is not the machine's efficiency but what a country charges for a\n"
            f"kilowatt-hour of electricity against one of gas.",
            xy=(0, -0.055 - 2.4 / len(df)), xycoords="axes fraction", va="top",
            fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
print("wrote", sys.argv[2], f"({n_win}/{len(df)} favour heat pumps at SPF 3)")

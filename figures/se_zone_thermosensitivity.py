#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Daily electricity demand per person against daily mean temperature, Sweden's
four bidding zones. Input: data-refresh/se-zone-thermosensitivity.csv and -fit.csv
from `mill Refresh.scala chapter25ThermosensitivitySeZones`.
Usage: python se_zone_thermosensitivity.py <scatter.csv> <fit.csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COLD, HOT, MID, TEXT, MUTED = "#2a78d6", "#eb6834", "#c9c9c4", "#161d1b", "#8a8a85"
# North to south, which is also coldest to mildest.
ORDER = ["SE1", "SE2", "SE3", "SE4"]
NAME = {"SE1": "SE1 Luleå", "SE2": "SE2 Sundsvall", "SE3": "SE3 Stockholm", "SE4": "SE4 Malmö"}

d = pd.read_csv(sys.argv[1])
fit = pd.read_csv(sys.argv[2]).set_index("zone")
assert set(ORDER) == set(fit.index), f"ORDER {ORDER} does not match {list(fit.index)}"
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
# Shared axes, in watts per person: the point of the figure is that the zones
# can be laid on one scale, which megawatts per zone cannot do.
fig, axes = plt.subplots(1, 4, figsize=(12.4, 4.2), sharex=True)
assert len(ORDER) == axes.size

for ax, z in zip(axes.flat, ORDER):
    f = fit.loc[z]
    p = d[d.zone == z]
    wpp = p.load_mw * 1e6 / f.population
    th, tc = f.t_heat, f.t_cool
    band = [COLD if t < th else HOT if t > tc else MID for t in p.temp_c]
    ax.scatter(p.temp_c, wpp, s=5, c=band, alpha=0.45, linewidths=0, rasterized=True)

    # The heating arm only: no zone has the thirty hot weekdays a summer arm needs.
    arm = p[p.temp_c < th]
    if len(arm) >= 30 and f.heat_mw_per_c >= 2 * f.heat_se:
        b = -f.heat_w_per_person
        y = arm.load_mw * 1e6 / f.population
        a = y.mean() - b * arm.temp_c.mean()
        lo = arm.temp_c.min()
        ax.plot([lo, th], [a + b * lo, a + b * th], color=COLD, lw=2.2, zorder=4)

    ax.text(0.97, 0.95, f"{f.heat_w_per_person:.0f} W per person\nper °C colder",
            transform=ax.transAxes, va="top", ha="right", fontsize=9, color=COLD,
            fontweight="bold")
    ax.text(0.97, 0.05, f"{f.population / 1e6:.2f} M people\nmean {f.mean_temp_c:.1f} °C",
            transform=ax.transAxes, va="bottom", ha="right", fontsize=8.5, color=MUTED)
    ax.set_title(NAME[z], loc="left", fontsize=11.5, fontweight="bold", pad=6)
    ax.set_xlim(-25, 25)
    ax.set_ylim(bottom=0)
    ax.tick_params(labelsize=8.5)
    ax.set_xlabel("Daily mean temperature (°C)", fontsize=9.5)
    for s in ("top", "right"): ax.spines[s].set_visible(False)

axes[0].set_ylabel("Daily mean demand (W per person)", fontsize=9.5)
fig.suptitle("One country, four climates: Swedish demand per person against temperature, 2021–2026",
             x=0.006, y=1.02, ha="left", fontsize=13, fontweight="bold")
fig.text(0.006, -0.06,
         "Weekdays only, August excluded. Lines are least squares on the days below 15 °C. Load is ENTSO-E per bidding zone;\n"
         "temperature is ERA5 weighted by each zone's towns; population is SCB 2024 by county, counties placed whole in one zone.\n"
         "SE1 and SE2 levels are mostly mines, smelters and pulp mills divided by few people; compare their slopes, not their levels.",
         ha="left", va="top", fontsize=9, color=MUTED)
fig.tight_layout()
fig.savefig(sys.argv[3], format="svg", bbox_inches="tight", dpi=110, metadata={"Date": None})
print("wrote", sys.argv[3])

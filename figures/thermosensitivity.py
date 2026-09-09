#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Daily electricity demand against daily mean temperature, six countries.
Input: data-refresh/thermosensitivity.csv and -fit.csv from
`mill Refresh.scala chapter25Thermosensitivity`.
Usage: python thermosensitivity.py <scatter.csv> <fit.csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

COLD, HOT, MID, TEXT, MUTED = "#2a78d6", "#eb6834", "#c9c9c4", "#161d1b", "#8a8a85"
# Winter-dominated first, summer-dominated last: the panels are the argument.
ORDER = ["Sweden", "France", "Great Britain", "Germany", "Spain", "Italy"]

d = pd.read_csv(sys.argv[1])
fit = pd.read_csv(sys.argv[2]).set_index("country")
# zip() over a hardcoded ORDER would silently drop a seventh country added to
# LANDS in Refresh.scala, so the mismatch is an error rather than a missing panel.
assert set(ORDER) == set(fit.index), f"ORDER {ORDER} does not match {list(fit.index)}"
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, axes = plt.subplots(2, 3, figsize=(11.4, 6.6))
assert len(ORDER) == axes.size

for ax, name in zip(axes.flat, ORDER):
    p, f = d[d.country == name], fit.loc[name]
    th, tc = f.t_heat, f.t_cool
    band = [COLD if t < th else HOT if t > tc else MID for t in p.temp_c]
    # 8000 points as vector circles make a 1.4 MB SVG; rasterising the scatter
    # alone keeps the axes, lines and text as text.
    ax.scatter(p.temp_c, p.load_gw, s=5, c=band, alpha=0.45, linewidths=0,
               rasterized=True)

    # Each arm's line is drawn only across the days it was fitted on, and only
    # when the slope is more than two standard errors from zero.
    for lo, hi, slope, se, col, arm in (
            (p.temp_c.min(), th, -f.heat_gw_per_c, f.heat_se, COLD, p[p.temp_c < th]),
            (tc, p.temp_c.max(), f.cool_gw_per_c, f.cool_se, HOT, p[p.temp_c > tc])):
        # Strict bounds, matching the fit in Refresh.scala: a handful of days sit
        # exactly on 15.0 or 20.0 and inclusive bounds would move the drawn line
        # off the fitted one.
        if len(arm) < 30 or abs(slope) < 2 * se:
            continue
        b = slope
        a = arm.load_gw.mean() - b * arm.temp_c.mean()
        ax.plot([lo, hi], [a + b * lo, a + b * hi], color=col, lw=2.2, zorder=4)

    lab_c = f"{f.heat_gw_per_c:.2f} GW per °C colder\n({f.heat_pct:.1f}% of mean load)"
    lab_h = (f"{f.cool_gw_per_c:.2f} GW per °C hotter\n({f.cool_pct:.1f}%)"
             if abs(f.cool_gw_per_c) >= 2 * f.cool_se
             else f"no summer arm\n(n = {int(f.cool_n)} days)")
    # Both labels along the bottom: the cold arm's own points occupy the top
    # left of every panel, which is where a winter label would naturally go.
    ax.text(0.03, 0.05, lab_c, transform=ax.transAxes, va="bottom", ha="left",
            fontsize=8.5, color=COLD, fontweight="bold")
    ax.text(0.97, 0.05, lab_h, transform=ax.transAxes, va="bottom", ha="right",
            fontsize=8.5, color=HOT if abs(f.cool_gw_per_c) >= 2 * f.cool_se else MUTED,
            fontweight="bold")
    ax.set_title(name, loc="left", fontsize=11.5, fontweight="bold", pad=6)
    ax.set_xlim(-6, 34)
    ax.tick_params(labelsize=8.5)
    for s in ("top", "right"): ax.spines[s].set_visible(False)

for ax in axes[1]: ax.set_xlabel("Daily mean temperature (°C)", fontsize=9.5)
for ax in axes[:, 0]: ax.set_ylabel("Daily mean demand (GW)", fontsize=9.5)
fig.suptitle("What one degree buys: daily electricity demand against temperature, 2021–2026",
             x=0.006, y=1.005, ha="left", fontsize=13, fontweight="bold")
fig.text(0.006, -0.035,
         "Weekdays only, August excluded. Lines are least squares on the days below 15 °C and above 20 °C.\n"
         "Load is ENTSO-E; Great Britain is NESO national demand with distribution-connected solar and wind added back.",
         ha="left", va="top", fontsize=9, color=MUTED)
fig.tight_layout()
fig.savefig(sys.argv[3], format="svg", bbox_inches="tight", dpi=110)
print("wrote", sys.argv[3])

#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 1.7: world CO2 per person against the paths MacKay's figure 1.8 requires.
Input: data-refresh/co2-per-capita-world.csv.

The two trajectories are drawn as MacKay describes them -- global emissions
falling 70% or 85% from 2007 to 2050 -- not reproduced from Baer and
Mastrandrea's own model runs, and the figure says so."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

INK, MUTED, GRID, ACT = "#161d1b", "#8a8a85", "#ededea", "#161d1b"
P70, P85 = "#eda100", "#1baf7a"

df = pd.read_csv(sys.argv[1]).sort_values("year")
df = df[df.year >= 1990]
base_y, base = 2007, float(df.loc[df.year == 2007, "t_per_person"].iloc[0])
end = df.iloc[-1]

sns.set_theme(style="whitegrid", rc={"grid.color": GRID, "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.2))
for cut, col, lab in ((0.70, P70, "70% cut by 2050"), (0.85, P85, "85% cut by 2050")):
    xs = [base_y, 2050]; ys = [base, base * (1 - cut)]
    ax.plot(xs, ys, color=col, lw=2.2, ls=(0, (5, 3)), label=lab)
    ax.annotate(f"{lab}\n{base*(1-cut):.1f} t", xy=(2050, base * (1 - cut)),
                xytext=(6, 0), textcoords="offset points", fontsize=9.5,
                color=col, va="center", linespacing=1.4)
ax.plot(df.year, df.t_per_person, color=ACT, lw=2.6, label="what actually happened")
ax.plot([base_y], [base], "o", ms=7, color=INK, zorder=5)
ax.annotate(f"{base_y}: {base:.2f} t", xy=(base_y, base), xytext=(-10, 12),
            textcoords="offset points", fontsize=9.5, color=INK, ha="right")
ax.annotate(f"{int(end.year)}: {end.t_per_person:.2f} t, up 1%",
            xy=(end.year, end.t_per_person), xytext=(8, 4), textcoords="offset points",
            fontsize=10, color=INK, fontweight="bold")
ax.set_ylabel("world CO$_2$ emissions, tonnes per person per year", fontsize=10.5)
ax.set_xlim(1990, 2058); ax.set_ylim(0, 5.6)
ax.set_xticks(range(1990, 2051, 10))
ax.legend(frameon=False, fontsize=9.5, loc="lower left")
ax.set_title("Figure 1.7. The trajectories, and the line we are actually on.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
ax.annotate("Seventeen of the forty-three years are gone and emissions per person have not started\n"
            "down. Both scenarios also assumed CO$_2$ would peak below 425 ppm; it passed that in 2025.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("Paths drawn as MacKay describes them, linearly from 2007, not reproduced from Baer and "
            "Mastrandrea's model. Source: Global Carbon Budget.",
            xy=(0, -0.16), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

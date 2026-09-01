#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 1.4: atmospheric CO2 for the last twelve centuries, carrying MacKay's
figure 1.4 and the upper panel of figure 1.15 forward. Input: data-refresh/co2-concentration.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

INK, MUTED, GRID, LINE = "#161d1b", "#8a8a85", "#ededea", "#b8402e"
df = pd.read_csv(sys.argv[1]).sort_values("year")
df = df[df.year >= 900]
end = df.iloc[-1]
pre = df[df.year <= 1750].ppm.mean()
mk = df[df.year <= 2007].iloc[-1]          # dar MacKays diagram slutar

sns.set_theme(style="whitegrid", rc={"grid.color": GRID, "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.0))
ax.axhline(pre, color=MUTED, lw=1.2, ls=(0, (5, 3)))
ax.annotate(f"pre-industrial, about {pre:.0f} ppm", xy=(940, pre), xytext=(0, 6),
            textcoords="offset points", fontsize=9.5, color=MUTED)
ax.plot(df.year, df.ppm, color=LINE, lw=2.2)
ax.plot([mk.year], [mk.ppm], "o", ms=7, color=INK, zorder=5)
ax.annotate(f"where MacKay's chart ends\n{int(mk.year)}: {mk.ppm:.0f} ppm",
            xy=(mk.year, mk.ppm), xytext=(-16, -46), textcoords="offset points",
            fontsize=9.5, color=INK, ha="right", linespacing=1.4,
            arrowprops=dict(arrowstyle="-", color=INK, lw=1))
ax.annotate(f"{int(end.year)}: {end.ppm:.0f}", xy=(end.year, end.ppm), xytext=(-6, 8),
            textcoords="offset points", fontsize=10.5, color=LINE, ha="right",
            fontweight="bold")
ax.set_ylabel("CO$_2$ concentration, parts per million", fontsize=10.5)
ax.set_xlim(900, 2060); ax.set_ylim(250, 445)
ax.set_xticks(range(1000, 2001, 200))
ax.set_title("Figure 1.4. Carbon dioxide, eighteen years further on.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
r1800 = df[df.year <= 1800].iloc[-1].ppm
r1960 = df[df.year <= 1960].iloc[-1].ppm
ax.annotate(f"The line MacKay draws ends at {mk.ppm:.0f} ppm. It has since risen by another "
            f"{end.ppm - mk.ppm:.0f}, more\nthan the {r1960 - r1800:.0f} it rose between 1800 and 1960.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("Ice cores to 1958, then direct measurement at Mauna Loa. Source: Our World in Data, "
            "after Bereiter et al. and NOAA.",
            xy=(0, -0.16), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

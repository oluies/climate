#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 1.5a: the early history of coal, MacKay's figure 1.5 redrawn large.
Same window and same two series; the point is only that it can be read.
Input: data-refresh/coal-long-run.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

INK, MUTED, GRID, UK, WORLD = "#161d1b", "#8a8a85", "#ededea", "#4a3aa7", "#161d1b"
df = pd.read_csv(sys.argv[1]).sort_values("year")
df = df[(df.year >= 1700) & (df.year <= 1910)]

sns.set_theme(style="whitegrid", rc={"grid.color": GRID, "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.0))
# Varldsserien ar decennievis fore 1900; rita varje serie over sina egna
# punkter sa att glesheten inte bryter linjen.
wd = df.dropna(subset=["world_twh"]); ud = df.dropna(subset=["uk_twh"])
ax.plot(wd.year, wd.world_twh, color=WORLD, lw=2.4, label="World")
ax.plot(ud.year, ud.uk_twh, color=UK, lw=2.4, label="United Kingdom")
ax.fill_between(ud.year, ud.uk_twh, color=UK, alpha=0.13)
for yr in (1800, 1830, 1850, 1870):
    v = df.loc[df.year == yr, "uk_twh"]
    if not v.empty:
        ax.annotate(f"{yr}", xy=(yr, v.iloc[0]), xytext=(0, 9), textcoords="offset points",
                    fontsize=9, color=UK, ha="center")
ax.set_ylabel("coal production, TWh per year", fontsize=10.5)
ax.set_xlim(1700, 1910); ax.set_ylim(0, 9800)
ax.set_xticks(range(1700, 1911, 50))
ax.legend(frameon=False, fontsize=10, loc="upper left")
ax.set_title("Figure 1.5a. The same early history, drawn large enough to read.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
ax.annotate("British production doubled roughly every twenty years from 1800: the dates MacKay\n"
            "marks are labelled on the British curve.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("Coal production in terawatt-hours of primary energy; the world series begins in 1800. "
            "Source: Our World in Data.",
            xy=(0, -0.16), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

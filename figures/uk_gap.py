#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 1.3a: what happened to the energy gap. MacKay's figure 1.3 is EdF's
2008 projection of nuclear, coal and oil capacity falling away. The projection
period is now entirely in the past, so it can be replaced by the outturn.

This plots generation rather than capacity, which is a different quantity and is
said so on the figure: capacity by plant type is not published as a clean annual
series back to 1985, and generation answers the question the figure asks --
whether the lights went out -- at least as directly.
Input: data-refresh/uk-electricity-mix.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
ORDER = ["Coal", "Nuclear", "Gas", "Bioenergy", "Hydropower", "Wind", "Solar"]
COL = {"Coal": "#4a4a46", "Nuclear": "#7a5c3e", "Gas": "#c98a3a",
       "Bioenergy": "#6b4a9e", "Hydropower": "#2e6fd6", "Wind": "#1baf7a",
       "Solar": "#eda100"}

d = pd.read_csv(sys.argv[1])
w = d.pivot(index="year", columns="source", values="twh").fillna(0).sort_index()
w = w[[c for c in ORDER if c in w.columns]]
first, last = int(w.index.min()), int(w.index.max())

sns.set_theme(style="whitegrid", rc={"grid.color": GRID, "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.2))
ax.stackplot(w.index, *[w[c] for c in w.columns], labels=list(w.columns),
             colors=[COL[c] for c in w.columns], alpha=0.92)
ax.axvline(2008, color=INK, lw=1.2, ls=(0, (4, 3)))
ax.annotate("MacKay writes, and figure 1.3 projects from here", xy=(2008, 425),
            xytext=(-8, 0), textcoords="offset points", fontsize=9.5, color=INK,
            ha="right", va="top")
t0, t1 = w.loc[2008].sum(), w.loc[last].sum()
ax.annotate(f"{last}: {t1:.0f} TWh, a quarter less than 2008",
            xy=(last, t1), xytext=(-6, 10), textcoords="offset points",
            fontsize=9.5, color=INK, ha="right", fontweight="bold")
ax.set_ylabel("UK electricity generation, TWh per year", fontsize=10.5)
ax.set_xlim(first, last); ax.set_ylim(0, 430)
ax.set_xticks(range(1990, last + 1, 10))
ax.legend(frameon=False, fontsize=9, loc="lower left", ncol=4)
ax.set_title("Figure 1.3a. The gap closed, but not from the supply side.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
ax.annotate("Coal and nuclear fell from 176 TWh in 2008 to 36. Wind and solar rose from 7 to 105 —\n"
            "and total generation fell 25%, which closed more of the gap than either.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("Generation, not capacity: capacity by plant type is not published as a clean annual series. "
            "Source: Energy Institute Statistical Review.",
            xy=(0, -0.16), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

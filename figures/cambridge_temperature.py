#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "numpy", "pandas", "seaborn"]
# ///
"""Figure 7.8 redone: Cambridge daily temperature, MacKay's 2006 against 2025.
Input: data-refresh/cambridge-temperature.csv from `mill Refresh.scala chapter07Temp`."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt, numpy as np

OLD, NEW, MUTED = "#8a8a85", "#e34948", "#8a8a85"
MSTART = np.cumsum([0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30]) + 1
M = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
df = pd.read_csv(sys.argv[1])

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 4.8))
for y, col in ((2006, OLD), (2025, NEW)):
    d = df[df.year == y].sort_values("day")
    ax.fill_between(d.day, d.tmin, d.tmax, color=col, alpha=0.16, lw=0)
    ax.plot(d.day, d.tmean.rolling(7, center=True, min_periods=1).mean(),
            color=col, lw=2.0, label=f"{y}   mean {d.tmean.mean():.1f} °C")

# 15.5 C is the base for heating degree days: below it, houses need heat.
ax.axhline(15.5, color="#4a3aa7", lw=1.2, ls=(0, (5, 3)))
ax.annotate("15.5 °C — below this a house needs heating", xy=(8, 15.5),
            xytext=(0, 5), textcoords="offset points", fontsize=9, color="#4a3aa7")

ax.set_ylabel("daily temperature (°C)", fontsize=10.5)
ax.set_xticks(MSTART); ax.set_xticklabels(M)
ax.set_xlim(1, 366); ax.set_ylim(-6, 32)
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.legend(frameon=False, fontsize=9.5, loc="upper left", ncol=2)
ax.set_title("Cambridge temperature: MacKay's 2006 against 2025",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
hd = {y: float((15.5 - df[df.year == y].tmean).clip(lower=0).sum()) for y in (2006, 2025)}
ax.annotate(f"Lines are 7-day means, bands the daily range. Heating degree days fell from "
            f"{hd[2006]:.0f} in 2006 to {hd[2025]:.0f} in 2025, a drop of "
            f"{(1 - hd[2025] / hd[2006]) * 100:.0f}% — part of why British gas demand fell without\n"
            f"anyone insulating anything. Summer still barely reaches the point where cooling would pay.",
            xy=(0, -0.155), xycoords="axes fraction", va="top", fontsize=9.5, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight")
print("wrote", sys.argv[2], f"| HDD 2006 {hd[2006]:.0f}, 2025 {hd[2025]:.0f}")

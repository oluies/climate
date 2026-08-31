#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Figure 1.7: UK and world coal production, 1700 to 2025, carrying MacKay's
own version forward. Input: data-refresh/coal-long-run.csv.

MacKay stops in 1960 and remarks that showing the next fifty years on the same
scale would need a book a metre tall. This is that book, on one page: the world
curve is drawn to scale and the British one is the flat line along the bottom."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

INK, MUTED, GRID = "#161d1b", "#8a8a85", "#ededea"
UK, WORLD = "#4a3aa7", "#161d1b"

df = pd.read_csv(sys.argv[1]).sort_values("year")
df = df[df.year >= 1700]
uk_pk = df.loc[df.uk_twh.idxmax()]
end = df.iloc[-1]

sns.set_theme(style="whitegrid", rc={"grid.color": GRID, "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(8.8, 5.2))

# Varldsserien ar gles fore 1900; rita varje serie over sina egna punkter.
wd = df.dropna(subset=["world_twh"]); ud = df.dropna(subset=["uk_twh"])
ax.plot(wd.year, wd.world_twh / 1000, color=WORLD, lw=2.4, label="World")
ax.plot(ud.year, ud.uk_twh / 1000, color=UK, lw=2.4, label="United Kingdom")
ax.fill_between(ud.year, ud.uk_twh / 1000, color=UK, alpha=0.13)

ax.set_ylabel("coal production, thousand TWh per year", fontsize=10.5)
ax.set_xlim(1700, int(end.year)); ax.set_ylim(0, 55)
ax.set_xticks(range(1700, 2026, 50))
ax.legend(frameon=False, fontsize=10, loc="upper left")

ax.annotate(f"UK peak {int(uk_pk.year)}: {uk_pk.uk_twh/1000:.1f}",
            xy=(uk_pk.year, uk_pk.uk_twh / 1000), xytext=(-10, 26),
            textcoords="offset points", fontsize=9.5, color=UK, ha="right",
            fontweight="bold",
            arrowprops=dict(arrowstyle="-", color=UK, lw=1))
ax.annotate(f"{int(end.year)}: {end.world_twh/1000:.0f}",
            xy=(end.year, end.world_twh / 1000), xytext=(-6, 8),
            textcoords="offset points", fontsize=9.5, color=INK, ha="right",
            fontweight="bold")
ax.annotate(f"UK {int(end.year)}: {end.uk_twh:.0f} TWh, effectively nil",
            xy=(end.year, 0.4), xytext=(-8, 14), textcoords="offset points",
            fontsize=9.5, color=UK, ha="right")

ax.set_title("Figure 1.7. What happened after 1960.",
             loc="left", fontsize=12.5, fontweight="bold", pad=42)
ax.annotate("MacKay stops in 1960 and says showing the next fifty years to scale would need a\n"
            "book a metre tall. Here it is on one page: Britain's curve is the flat line.",
            xy=(0, 1.015), xycoords="axes fraction", va="bottom", fontsize=9.5, color=MUTED)
ax.annotate("Coal production in terawatt-hours of primary energy. Source: Our World in Data, "
            "from the Energy Institute and historical statistics.",
            xy=(0, -0.16), xycoords="axes fraction", va="top", fontsize=8.5, color=MUTED)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

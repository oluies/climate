#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib"]
# ///
"""China's nuclear build against Germany's phase-out, 1965-2025.
Input: data-refresh/nuclear-history.csv from `mill Refresh.scala nuclearHistory`."""
import sys, duckdb, matplotlib.pyplot as plt

INK, MUTED = "#161d1b", "#8a8a85"
CN, DE, FR = "#e34948", "#2a78d6", "#8a8a85"
q = f"read_csv_auto('{sys.argv[1]}')"


def series(country):
    d = duckdb.sql(f"SELECT year, twh FROM {q} WHERE country = '{country}' ORDER BY year").fetchnumpy()
    return d["year"].astype(float), d["twh"].astype(float)


cy, cv = series("China")
dy, dv = series("Germany")
fy, fv = series("France")

fig, ax = plt.subplots(figsize=(9.4, 5.4))
ax.fill_between(dy, dv, color=DE, alpha=0.22, zorder=2)
ax.fill_between(cy, cv, color=CN, alpha=0.22, zorder=3)
ax.plot(dy, dv, color=DE, lw=2.2, zorder=4)
ax.plot(cy, cv, color=CN, lw=2.2, zorder=5)
# France's all-time peak as a reference: China passed it, which is the scale of the thing.
fpk = float(fv.max())
ax.axhline(fpk, color=FR, lw=1.2, ls=(0, (5, 3)), zorder=1)
ax.annotate(f"France's all-time peak, {fpk:.0f} TWh in {int(fy[fv.argmax()])}",
            xy=(1968, fpk), xytext=(0, 5), textcoords="offset points",
            fontsize=9, color=FR)

# The crossover, computed rather than eyeballed.
both = {int(y): v for y, v in zip(dy, dv)}
cross = next((int(y) for y, v in zip(cy, cv) if int(y) in both and v > both[int(y)]), None)
assert cross, "no crossover found in the data"
ax.annotate(f"China passes Germany, {cross}", xy=(cross, both[cross]),
            xytext=(-10, 62), textcoords="offset points", ha="right", fontsize=9.2,
            color=INK, arrowprops=dict(arrowstyle="-", color="#c9c9c4", lw=0.9))

ax.annotate(f"China\n{cv[-1]:.0f} TWh", xy=(cy[-1], cv[-1]), xytext=(-10, -46),
            textcoords="offset points", ha="right", va="top",
            fontsize=11, fontweight="bold", color=CN)
ax.annotate(f"Germany\npeak {dv.max():.0f} TWh in {int(dy[dv.argmax()])},\nzero from 2023",
            xy=(1987, 36), fontsize=10.5, color=DE, ha="center", fontweight="bold")

ax.set_xlim(1965, 2029); ax.set_ylim(0, 520)
ax.set_ylabel("nuclear generation (TWh a year)", fontsize=10.5)
ax.grid(axis="y", color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right"): ax.spines[s].set_visible(False)
for s in ("bottom", "left"): ax.spines[s].set_color("#c9c9c4")
ax.set_title("Two decisions about nuclear power, 1965–2025",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("Germany's fleet reached 171 TWh in 2001 and was shut down completely in April 2023. China's passed it in "
            f"{cross}\nand has now passed France's all-time peak: no country has ever generated more nuclear electricity than "
            "China does\nnow except the United States. Neither trajectory was decided by uranium, geology or reactor physics.",
            xy=(0, -0.14), xycoords="axes fraction", va="top", fontsize=9.4, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Svenska kraftnat's ancillary-service cost by product, net, 2023 to 2025.
Data from Refresh.scala seReserveCost -> se-reserve-cost.csv; this only draws it.
Usage: python se_reserve_cost.py <csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

df = pd.read_csv(sys.argv[1])
wide = df.pivot(index="year", columns="product", values="msek")[["FCR", "aFRR", "mFRR"]]
COL = {"FCR": "#1f6fd1", "aFRR": "#c9ccd4", "mFRR": "#e34948"}
INK = "#52514e"
years = list(wide.index)
totals = wide.sum(axis=1)

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(7.4, 3.6))

# Axis limits first, so the in-bar label test can be expressed as a fraction of the
# axis rather than as a magic MSEK number: a segment shorter than this cannot hold
# 9.5pt digits without spilling into its neighbour.
ymax = float(totals.max()) * 1.20
ax.set_ylim(0, ymax)
fits = ymax * 0.055

base = [0.0] * len(wide)
for col in wide.columns:
    ax.bar(years, wide[col], bottom=base, width=0.54, color=COL[col], label=None, zorder=3)
    base = [b + v for b, v in zip(base, wide[col])]

# Labels in two passes. A segment tall enough holds its value inside; the rest go in a
# column beside the bar, de-collided against each other, because two short segments in the
# same bar otherwise land within a few pixels. The series labels get a column further out
# again: an earlier version shared a column with the fallback labels and drew "377" on
# top of "aFRR".
SEP = ymax * 0.052                      # one line of 9pt text, in data units
for yr in years:
    run, outside = 0.0, []
    for col in wide.columns:
        v = float(wide.loc[yr, col]); mid = run + v / 2; run += v
        txt = f"{int(round(v)):,}".replace(",", " ")
        if v >= fits:
            ax.text(yr, mid, txt, ha="center", va="center", fontsize=9.5,
                    color="white" if col != "aFRR" else INK, fontweight="bold")
        else:
            outside.append({"mid": mid, "txt": txt, "col": col})
    if not outside:
        continue
    # Spread the labels apart, re-centre the group on its own midpoints rather than
    # only pushing upward, and keep the whole group inside the bar so it can never
    # collide with the total above it.
    target = [o["mid"] for o in outside]
    for i in range(1, len(target)):
        target[i] = max(target[i], target[i - 1] + SEP)
    shift = (sum(o["mid"] for o in outside) - sum(target)) / len(target)
    target = [ty + shift for ty in target]
    top = float(totals.loc[yr])
    if target[-1] > top:
        target = [ty - (target[-1] - top) for ty in target]
    if target[0] < 0:
        target = [ty - target[0] for ty in target]
    for o, ty in zip(outside, target):
        # The leader stays anchored on the segment it names even when the text is
        # displaced, and is tinted with that product's colour so the pairing survives.
        ax.annotate(o["txt"], xy=(yr + 0.29, o["mid"]), xytext=(yr + 0.42, ty),
                    textcoords="data", va="center", ha="left", fontsize=9, color=INK,
                    arrowprops=dict(arrowstyle="-", color=COL[o["col"]], lw=0.9,
                                    shrinkA=0, shrinkB=3))

for x, tot in zip(years, totals):
    ax.text(x, tot + ymax * 0.022, f"{int(round(tot)):,}".replace(",", " "), ha="center",
            fontsize=10, color=INK)

# Direct series labels, in their own column clear of the fallback column above.
last = wide.iloc[-1]
run = 0.0
for col in wide.columns:
    ax.text(years[-1] + 0.78, run + last[col] / 2, col, va="center", fontsize=10,
            color=COL[col], fontweight="bold")
    run += last[col]

ax.set_xticks(years)
ax.set_xlim(years[0] - 0.45, years[-1] + 1.35)
ax.set_ylabel("MSEK a year, net")
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.text(0.0, 1.14, "What Sweden pays, by reserve product",
        transform=ax.transAxes, ha="left", fontsize=12.5, fontweight="bold")
ax.text(0.0, 1.045, "Containment fell to a fifth while manual restoration went up seventyfold",
        transform=ax.transAxes, ha="left", fontsize=9.5, color=INK)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

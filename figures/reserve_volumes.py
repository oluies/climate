#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["matplotlib", "pandas", "seaborn"]
# ///
"""Svenska kraftnat's reserve volume requirement by product, 2025 against 2030.
Data from Refresh.scala reserveVolumes -> reserve-volumes.csv; this only draws it.
Usage: python reserve_volumes.py <csv> <out.svg>"""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt

df = pd.read_csv(sys.argv[1])
NOW, THEN, INK = "#c9ccd4", "#1f6fd1", "#52514e"

sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(7.4, 3.9))

y = range(len(df))
h = 0.38
ax.barh([v + h / 2 for v in y], df["mw2025"], height=h, color=NOW, zorder=3)
ax.barh([v - h / 2 for v in y], df["mw2030"], height=h, color=THEN, zorder=3)

# Tag the row with the most right-hand slack, so the tags cannot be pushed off the axis
# if the rows are reordered or a longer bar is added.
tagrow = int(df[["mw2025", "mw2030"]].max(axis=1).idxmin())

# The two series are labelled directly on the top row rather than with a legend box:
# a legend inside the axes collides with the 1400 MW mFRR bar at lower right.
for i, r in df.iterrows():
    tag25, tag30 = ("  2025", "  2030") if i == tagrow else ("", "")
    ax.text(r["mw2025"] + 18, i + h / 2, f"{int(r['mw2025'])}{tag25}", va="center",
            fontsize=9, color=INK)
    ax.text(r["mw2030"] + 18, i - h / 2, f"{int(r['mw2030'])}{tag30}", va="center", fontsize=9,
            color=THEN, fontweight="bold" if r["mw2030"] != r["mw2025"] else "normal")

ax.set_yticks(list(y)); ax.set_yticklabels(df["product"])
ax.invert_yaxis()
ax.set_xlabel("megawatts, Sweden's share of the Nordic requirement")
# Headroom must hold the value label beside the longest bar, which is an absolute
# offset plus text width, so a purely proportional margin shrinks too far on small data.
_mx = float(df[["mw2025", "mw2030"]].max().max())
ax.set_xlim(0, _mx + max(_mx * 0.10, 200))
ax.grid(axis="y", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)

split = (df["category"] == "Containment").sum()
ax.axhline(split - 0.5, color="#c9c9c4", lw=1.0)
# Group captions sit OUTSIDE the axes: get_yaxis_transform puts x in axes fraction and
# y in data coordinates, so a caption can never overlap a bar or its value label however
# long the bars get. An earlier version anchored them at x = 1600 in data units and the
# restoration caption landed on the 1400 MW mFRR label.
for lab, col, lo, hi in [("containment\nFCR-D set by the\nreference incident", INK, 0, split),
                         ("restoration\nnormal imbalance, plus\nthe reference\nincident in mFRR", THEN, split, len(df))]:
    ax.text(1.015, (lo + hi - 1) / 2, lab, transform=ax.get_yaxis_transform(),
            ha="left", va="center", fontsize=9, color=col, linespacing=1.4)
ax.text(0.0, 1.10, "What Svenska kraftnat must hold, 2025 and 2030",
        transform=ax.transAxes, ha="left", fontsize=12.5, fontweight="bold")
ax.text(0.0, 1.035, "Containment is flat to 2030; the reference incident sizes FCR-D and mFRR alike",
        transform=ax.transAxes, ha="left", fontsize=9.5, color=INK)

fig.savefig(sys.argv[2], format="svg", bbox_inches="tight", metadata={"Date": None})
print("wrote", sys.argv[2])

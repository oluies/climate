#!/usr/bin/env python3
"""GB capture-price bars, seaborn. Data from Refresh.scala gbCapture -> gb-capture.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt
df = pd.read_csv(sys.argv[1]).sort_values("capture", ascending=False)
avg = float(df["systemavg"].iloc[0])
COL = {"Solar": "#eda100", "Wind": "#1baf7a", "Nuclear": "#4a3aa7", "Biomass": "#e34948", "Gas": "#eb6834"}
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(7.4, 3.2))
order = df["source"].tolist()
sns.barplot(data=df, y="source", x="capture", hue="source", order=order, hue_order=order,
            palette=COL, legend=False, saturation=1, ax=ax)
ax.axvline(avg, color="#52514e", ls=(0, (4, 3)), lw=1.2, zorder=0)
ax.text(avg, 1.02, f"system average {avg:.0f}", transform=ax.get_xaxis_transform(),
        ha="center", va="bottom", color="#52514e", fontsize=9)
for c in ax.containers:
    ax.bar_label(c, fmt="%.0f", padding=4, fontsize=10, color="#52514e")
ax.set_xlabel("GBP / MWh"); ax.set_ylabel(""); ax.margins(x=0.14)
ax.grid(axis="y", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title("GB 2025 capture price by source", loc="left", fontsize=12.5, fontweight="bold", pad=16)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

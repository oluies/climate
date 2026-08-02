#!/usr/bin/env python3
"""GB electricity demand over a winter week (MacKay's Fig K.3), seaborn.
Data from Refresh.scala chapterKDemand -> gb-demand-week.csv."""
import sys, pandas as pd, seaborn as sns, matplotlib.pyplot as plt
import matplotlib.dates as mdates
df = pd.read_csv(sys.argv[1], parse_dates=["ts"])
sns.set_theme(style="whitegrid", rc={"grid.color": "#ededea", "axes.edgecolor": "#c9c9c4"})
fig, ax = plt.subplots(figsize=(9, 3.8))
ax.plot(df["ts"], df["gw"], color="#2a78d6", lw=1.6)
ax.set_ylabel("GW (national demand)"); ax.set_xlabel(""); ax.set_ylim(0, None)
ax.xaxis.set_major_locator(mdates.DayLocator())
ax.xaxis.set_major_formatter(mdates.DateFormatter("%a %d %b"))
ax.grid(axis="x", visible=False)
for s in ("top", "right"): ax.spines[s].set_visible(False)
ax.set_title("GB electricity demand over a winter week, 13–19 January 2025",
             loc="left", fontsize=12.5, fontweight="bold", pad=12)
fig.autofmt_xdate(rotation=0, ha="center")
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

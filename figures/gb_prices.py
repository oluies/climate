#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["duckdb", "matplotlib", "numpy"]
# ///
"""Figure 26.5b: half-hourly GB market-index prices on three days of 2026.

MacKay's figure 26.5 showed three days of 2006-07 to explain how pumped storage
pays for itself: buy at the overnight trough, sell at the evening peak. This is
the same picture two decades later, and the shape of the argument has changed —
the spread is wider, and on a sunny spring day the trough goes below zero.

Input: data-refresh/gb-prices.csv from `mill Refresh.scala gbPrices`.
"""
import sys, duckdb, matplotlib.pyplot as plt, numpy as np

INK, MUTED = "#161d1b", "#8a8a85"
# Indexed by series order, not keyed on literal dates, so a different set of
# days cannot silently fall through to one indistinguishable colour.
PALETTE = ["#4a3aa7", "#8a8a85", "#1baf7a", "#eb6834", "#eda100"]

d = duckdb.sql(f"""
    -- Cast: DuckDB reads the day column as a timestamp, which breaks both the
    -- colour lookup and the legend labels.
    SELECT strftime(CAST(day AS DATE), '%Y-%m-%d') AS day, label, period, price
    FROM read_csv_auto('{sys.argv[1]}') ORDER BY day, period
""").fetchnumpy()

series = {}
for day, label, period, price in zip(d["day"], d["label"], d["period"], d["price"]):
    series.setdefault(str(day), {"label": str(label), "x": [], "y": []})
    # Settlement period 1 starts at 00:00; plot at the hour it covers.
    series[str(day)]["x"].append((int(period) - 1) / 2.0)
    series[str(day)]["y"].append(float(price))

# Fail rather than wrap the palette, which would repeat a colour silently.
assert len(series) <= len(PALETTE), f"{len(series)} days but only {len(PALETTE)} colours"

fig, ax = plt.subplots(figsize=(9.2, 5.4))
for i, (day, s) in enumerate(series.items()):
    lo, hi = min(s["y"]), max(s["y"])
    ax.plot(s["x"], s["y"], color=PALETTE[i], lw=2.2, zorder=3,
            label=f"{day}  —  £{lo:.0f} to £{hi:.0f}, spread £{hi-lo:.0f}")

ax.axhline(0, color="#c9c9c4", lw=1.2, zorder=2)
# Only label the zero line when something actually goes below it, and place the
# label at the deepest trough rather than at a hard-coded point that a different
# day's y-range would leave floating or clipped.
troughs = [(min(s["y"]), s["x"][s["y"].index(min(s["y"]))], i)
           for i, s in enumerate(series.values())]
lo, at_x, at_i = min(troughs)
if lo < 0:
    # Flip the label inside the axes when the trough sits near either edge,
    # rather than letting it run off the bottom or the right-hand side.
    y0, y1 = ax.get_ylim()
    near_floor = (lo - y0) < 0.12 * (y1 - y0)
    dy, va = (8, "bottom") if near_floor else (-4, "top")
    dx, ha = (-6, "right") if at_x > 21 else (6, "left")
    ax.annotate("paid to consume", xy=(at_x, lo), xytext=(dx, dy),
                textcoords="offset points", fontsize=9,
                color=PALETTE[at_i], va=va, ha=ha)

# From the data, not hard-coded: a clock-change day is 23 or 25 hours long and
# would otherwise be clipped. gbPrices refuses those days, so this is belt and
# braces rather than the primary guard.
xmax = max(max(s["x"]) for s in series.values()) + 0.5
ax.set_xlim(0, xmax)
ax.set_xticks(range(0, int(xmax) + 1, 3))
ax.set_xticklabels([f"{h:02d}" for h in range(0, int(xmax) + 1, 3)], fontsize=10)
ax.set_xlabel("hour of day", fontsize=10.5)
ax.set_ylabel("£ per MWh", fontsize=10.5)
ax.grid(color="#ededea", lw=0.9, zorder=0)
for s in ("top", "right"): ax.spines[s].set_visible(False)
for s in ("bottom", "left"): ax.spines[s].set_color("#c9c9c4")
ax.legend(loc="upper left", frameon=False, fontsize=9.5)
ax.set_title("What storage arbitrages: GB half-hourly prices, three days of 2026",
             loc="left", fontsize=12.5, fontweight="bold", pad=14)
ax.annotate("MacKay's figure 26.5 showed the same thing for 2006–07, to explain how pumped storage pays for itself: buy in the\n"
            "overnight trough, sell into the evening peak. The mechanism is unchanged and the numbers are not. A January day\n"
            "spans £218 between its cheapest and dearest half-hour. On the April day the trough is below zero — a generator is\n"
            "paying to run, and a store is paid to fill. That is the return storage earns, and it is why Britain built 7 GW of\n"
            "batteries. It is also why they are two-hour batteries: the spread is captured within a day, and nothing in this\n"
            "picture rewards holding energy from one week to the next.",
            xy=(0, -0.20), xycoords="axes fraction", va="top", fontsize=9.3, color=MUTED)
fig.savefig(sys.argv[2], format="svg", bbox_inches="tight"); print("wrote", sys.argv[2])

#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.9"
# dependencies = ["pandas", "matplotlib", "openpyxl"]
# ///
"""
Peak oil 101, uppdaterad — regenerates the production charts from
Lars Wilderäng's "Peak oil 101" (Cornucopia?, mars 2012) against the
latest Energy Institute Statistical Review of World Energy.

    https://cornucopia.se/2012/03/peak-oil-101-for-moderated/

The original charts were EIA all-liquids series ending in 2011. This
redraws the same sixteen countries from the Statistical Review, which
publishes a consistent 1965-onwards record every June, so the article
can be refreshed in one command each year.

Standalone by design: one file, no install, no repository.

Med uv (rekommenderas - hamtar beroendena sjalv, installerar ingenting globalt):

    uv run peak_oil_101.py                       # svenska, alla diagram
    uv run peak_oil_101.py --lang en             # English labels
    uv run peak_oil_101.py --xlsx path/to.xlsx   # workbook already downloaded
    uv run peak_oil_101.py --countries Norge Danmark

Eller klassiskt:

    pip install pandas matplotlib openpyxl
    python peak_oil_101.py

Download the workbook ("all data" xlsx) from
    https://www.energyinst.org/statistical-review
and pass it with --xlsx, or drop it beside this script as
    ei-stats-review-all-data.xlsx

Public domain. Do what you like with it.
"""
import argparse, os, sys

SHEET = "Oil Production - barrels"
PRICE_SHEET = "Oil crude prices since 1861"
FIRST_YEAR = 1965          # column B of the production sheet
START_YEAR = 1980          # the original charts begin here
DEFAULT_XLSX = "ei-stats-review-all-data.xlsx"

# (label in the workbook, Swedish name, English name) in the article's order.
# New Zealand appears in the 2012 article but the Statistical Review does not
# break it out (it falls inside "Other Asia Pacific"), so it cannot be redrawn
# from this source. Fifteen of the article's sixteen countries remain.
COUNTRIES = [
    ("Indonesia",            "Indonesien",   "Indonesia"),
    ("Mexico",               "Mexiko",       "Mexico"),
    ("Argentina",            "Argentina",    "Argentina"),
    ("Ecuador",              "Ecuador",      "Ecuador"),
    ("Venezuela",            "Venezuela",    "Venezuela"),
    ("Denmark",              "Danmark",      "Denmark"),
    ("Norway",               "Norge",        "Norway"),
    ("Iraq",                 "Irak",         "Iraq"),
    ("Kuwait",               "Kuwait",       "Kuwait"),
    ("Algeria",              "Algeriet",     "Algeria"),
    ("Libya",                "Libyen",       "Libya"),
    ("Nigeria",              "Nigeria",      "Nigeria"),
    ("Australia",            "Australien",   "Australia"),
    ("Brunei",               "Brunei",       "Brunei"),
    ("Malaysia",             "Malaysia",     "Malaysia"),
]
WORLD = "Total World"
# Marked "(OPEC)" in the original chart titles.
OPEC = {"Venezuela", "Kuwait", "Algeria", "Libya", "Nigeria", "Iraq", "Ecuador"}

TXT = {
    "sv": dict(
        title="Oljeproduktion", and_="och oljepris", prod="Prod", price="Pris",
        world="Världens oljeproduktion",
        leg_prod="Oljeproduktion (tusen fat per dag)",
        leg_price="Oljepris (USD/fat, årsmedel)",
        sub="Topp {py}. {yr}: {pct:.0f}% av toppen.",
        subrise="Toppåret är {py} — det senaste året i serien.",
        wsub="Enskilda länder toppar. Summan har inte gjort det.",
        src="Datakälla: Energy Institute Statistical Review {yr}",
        cnt="{n} av {tot} producenter ligger under sin egen topp",
    ),
    "en": dict(
        title="Oil production", and_="and oil price", prod="Prod", price="Price",
        world="World oil production",
        leg_prod="Oil production (thousand barrels per day)",
        leg_price="Oil price (USD/bbl, annual mean)",
        sub="Peak {py}. {yr}: {pct:.0f}% of peak.",
        subrise="The peak year is {py} — the last in the series.",
        wsub="Individual countries peak. The total has not.",
        src="Data: Energy Institute Statistical Review {yr}",
        cnt="{n} of {tot} producers are below their own peak",
    ),
}

# Taken from the 2012 charts: pale peach bars for production, a darker orange
# line for the Brent price on a second axis, red for the price-axis label.
BAR, LINE, PRICE_LBL = "#FAC090", "#E36C0A", "#C00000"
INK, MUTED, RULE, GRID = "#000000", "#595959", "#808080", "#D9D9D9"


def load(xlsx):
    """Return {country: {year: kb/d}} and the last data year.

    The sheet carries growth and share columns after the year block, and they
    repeat the final year in the header row, so the year columns are taken
    only while the header keeps increasing.
    """
    import pandas as pd
    head = pd.read_excel(xlsx, sheet_name=SHEET, header=None, skiprows=2, nrows=1)
    years, prev = {}, None
    for c in range(1, head.shape[1]):
        v = pd.to_numeric(head.iloc[0, c], errors="coerce")
        if pd.isna(v):
            continue
        y = int(v)
        if y < FIRST_YEAR or y > 2100 or (prev is not None and y <= prev):
            break
        years[c] = y
        prev = y
    if not years:
        sys.exit("Kunde inte lasa arsrubrikerna - har arbetsbokens layout andrats?")
    last = max(years.values())

    raw = pd.read_excel(xlsx, sheet_name=SHEET, header=None, skiprows=3)
    raw = raw[raw[0].notna()]
    series = {}
    for _, row in raw.iterrows():
        name = str(row[0]).strip()
        vals = {}
        for c, y in years.items():
            v = pd.to_numeric(row[c], errors="coerce")
            if pd.notna(v) and v > 0:
                vals[y] = float(v)
        if len(vals) >= 20:
            series[name] = vals
    return series, last


def load_prices(xlsx):
    """Annual Brent (money of the day) from the crude-price sheet."""
    import pandas as pd
    raw = pd.read_excel(xlsx, sheet_name=PRICE_SHEET, header=None, skiprows=4)
    out = {}
    for _, row in raw.iterrows():
        y = pd.to_numeric(row[0], errors="coerce")
        v = pd.to_numeric(row[1], errors="coerce")
        if pd.notna(y) and pd.notna(v) and 1861 <= y <= 2100:
            out[int(y)] = float(v)
    return out


def chart(name, vals, prices, t, latest, outdir, fmt, dpi, opec=False):
    """One chart in the layout of the 2012 originals: production as bars on the
    left axis, the Brent price as a line on the right."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    yrs = [y for y in sorted(vals) if y >= START_YEAR]
    ys = [vals[y] for y in yrs]
    all_yrs = sorted(vals)
    peak_y = max(all_yrs, key=lambda y: vals[y])
    peak_v, cur_v = vals[peak_y], vals[all_yrs[-1]]
    pct = cur_v / peak_v * 100
    rising = peak_y >= all_yrs[-1] - 1

    fig, ax = plt.subplots(figsize=(10.0, 4.6))
    ax.bar(yrs, ys, color=BAR, width=0.78, zorder=2)

    ax2 = ax.twinx()
    pyrs = [y for y in yrs if y in prices]
    ax2.plot(pyrs, [prices[y] for y in pyrs], color=LINE, lw=2.4, zorder=3)

    # Axis captions sit above their axes, as in the originals, rather than
    # rotated alongside them - set_ylabel would collide with the top tick.
    ax.annotate(t["prod"], xy=(0, 1.015), xycoords="axes fraction",
                ha="left", va="bottom", fontsize=9, color=INK)
    ax.annotate(t["price"], xy=(1, 1.015), xycoords="axes fraction",
                ha="right", va="bottom", fontsize=9, color=PRICE_LBL)
    ax2.tick_params(axis="y", colors=PRICE_LBL, labelsize=8.5)
    ax.tick_params(axis="y", labelsize=8.5)
    ax.set_ylim(0, max(ys) * 1.18)
    ax2.set_ylim(0, max(prices[y] for y in pyrs) * 1.18)
    ax.set_xlim(min(yrs) - 0.7, max(yrs) + 0.7)
    ax.set_xticks(yrs)
    ax.set_xticklabels([str(y) for y in yrs], rotation=90, fontsize=7.5)
    ax.grid(axis="y", color=GRID, lw=0.7, zorder=1)
    ax.set_axisbelow(True)
    for s in ax.spines.values():
        s.set_color(RULE); s.set_linewidth(0.8)
    for s in ax2.spines.values():
        s.set_visible(False)

    label = name + (" (OPEC)" if opec else "")
    ax.set_title(f"{t['title']} {label} {t['and_']} {min(yrs)}-{max(yrs)}",
                 fontsize=13, fontweight="bold", color=INK, pad=14)
    # Put the caption block wherever the chart is emptiest: compare the tallest
    # bar and the highest price point in the left and right thirds.
    n = len(yrs)
    def busy(lo, hi):
        seg = yrs[int(n * lo):int(n * hi)]
        b = max((vals[y] / max(ys) for y in seg), default=0)
        pr = max((prices[y] / max(prices[q] for q in pyrs) for y in seg if y in prices),
                 default=0)
        return max(b, pr)
    x, ha = (0.02, "left") if busy(0, 0.45) < busy(0.55, 1.0) else (0.98, "right")
    ax.annotate(t["src"].format(yr=latest + 1), xy=(x, 0.985), xycoords="axes fraction",
                ha=ha, va="top", fontsize=8, color=INK)
    sub = (t["subrise"].format(py=peak_y) if rising
           else t["sub"].format(py=peak_y, yr=all_yrs[-1], pct=pct))
    ax.annotate(sub, xy=(x, 0.905), xycoords="axes fraction", ha=ha, va="top",
                fontsize=8.5, color=LINE if not rising else "#1F7A3D", fontweight="bold")

    h = [plt.Rectangle((0, 0), 1, 1, color=BAR),
         plt.Line2D([0], [0], color=LINE, lw=2.4)]
    fig.legend(h, [t["leg_prod"], t["leg_price"]], loc="lower center", ncol=2,
               frameon=False, fontsize=8.5, bbox_to_anchor=(0.5, -0.06))

    path = os.path.join(outdir, f"{name.lower().replace(' ', '_')}.{fmt}")
    fig.savefig(path, format=fmt, dpi=dpi, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)
    return path, peak_y, pct, rising


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--xlsx", default=DEFAULT_XLSX)
    ap.add_argument("--outdir", default="peak-oil-101")
    ap.add_argument("--lang", choices=["sv", "en"], default="sv")
    ap.add_argument("--format", default="png", choices=["png", "svg"])
    ap.add_argument("--dpi", type=int, default=140)
    ap.add_argument("--countries", nargs="*", default=None,
                    help="subset, by the name shown on the chart")
    a = ap.parse_args()

    if not os.path.exists(a.xlsx):
        sys.exit(f"Hittar inte {a.xlsx}.\nLadda ner 'all data' xlsx fran "
                 "https://www.energyinst.org/statistical-review och ange den med --xlsx.")
    os.makedirs(a.outdir, exist_ok=True)
    t = TXT[a.lang]
    idx = 1 if a.lang == "sv" else 2

    series, latest = load(a.xlsx)
    prices = load_prices(a.xlsx)
    print(f"Statistical Review laddad, serien slutar {latest}.\n")

    wanted = [c for c in COUNTRIES if a.countries is None or c[idx] in a.countries]
    past = 0
    for key, sv, en in wanted:
        name = sv if a.lang == "sv" else en
        if key not in series:
            print(f"  !! {key} saknas i arbetsboken - hoppar over")
            continue
        path, py, pct, rising = chart(name, series[key], prices, t, latest,
                                      a.outdir, a.format, a.dpi,
                                      opec=key in OPEC)
        past += 0 if rising else 1
        print(f" {'^' if rising else ' '} {name:14} topp {py}  {pct:5.1f}%  ->  {path}")

    if a.countries is None and WORLD in series:
        w = dict(t); w["sub"] = t["wsub"]; w["subrise"] = t["wsub"]
        path, py, pct, _ = chart(t["world"], series[WORLD], prices, w, latest,
                                 a.outdir, a.format, a.dpi)
        print(f"   {t['world']:14} {'':16}  ->  {path}")

    print("\n" + t["cnt"].format(n=past, tot=len(wanted)))


if __name__ == "__main__":
    main()

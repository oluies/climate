#!/usr/bin/env python3
"""
Peak oil 101, uppdaterad — regenerates the production charts from
Lars Wilderäng's "Peak oil 101" (Cornucopia?, mars 2012) against the
latest Energy Institute Statistical Review of World Energy.

    https://cornucopia.se/2012/03/peak-oil-101-for-moderated/

The original charts were EIA all-liquids series ending in 2011. This
redraws the same sixteen countries from the Statistical Review, which
publishes a consistent 1965-onwards record every June, so the article
can be refreshed in one command each year.

Standalone by design: one file, three dependencies, no repository.

    pip install pandas matplotlib openpyxl
    python peak_oil_101.py                       # svenska, alla diagram
    python peak_oil_101.py --lang en             # English labels
    python peak_oil_101.py --xlsx path/to.xlsx   # workbook already downloaded
    python peak_oil_101.py --countries Norge Danmark

Download the workbook ("all data" xlsx) from
    https://www.energyinst.org/statistical-review
and pass it with --xlsx, or drop it beside this script as
    ei-stats-review-all-data.xlsx

Public domain. Do what you like with it.
"""
import argparse, os, sys

SHEET = "Oil Production - barrels"
FIRST_YEAR = 1965          # column B of that sheet
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

TXT = {
    "sv": dict(
        y="tusen fat per dag", peak="topp", now="nu",
        of_peak="av toppen", world="Världens oljeproduktion",
        wsub="Enskilda länder toppar. Summan har inte gjort det —\nnya provinser har hittills mer än ersatt de som faller.",
        sub="Topp {py} ({pv:,.0f} kfat/d). {yr}: {cv:,.0f}, {pct:.0f}% av toppen.",
        subrise="Toppåret är {py}, som är det senaste året i serien.",
        src="Källa: Energy Institute, Statistical Review of World Energy {yr}.\nDiagram återskapade från Cornucopia? “Peak oil 101” (2012).",
        cnt="{n} av {tot} producenter ligger under sin egen topp",
    ),
    "en": dict(
        y="thousand barrels per day", peak="peak", now="now",
        of_peak="of peak", world="World oil production",
        wsub="Individual countries peak. The total has not —\nnew provinces have so far more than replaced those falling.",
        sub="Peak {py} ({pv:,.0f} kb/d). {yr}: {cv:,.0f}, {pct:.0f}% of peak.",
        subrise="The peak year is {py}, the last year in the series.",
        src="Source: Energy Institute, Statistical Review of World Energy {yr}.\nCharts rebuilt from Cornucopia? “Peak oil 101” (2012).",
        cnt="{n} of {tot} producers are below their own peak",
    ),
}

INK, PAST, RISING, MUTED, RULE = "#161d1b", "#7a5c3e", "#1baf7a", "#8a8a85", "#c9c9c4"


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


def chart(name, vals, t, latest, outdir, fmt, dpi):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    yrs = sorted(vals)
    ys = [vals[y] for y in yrs]
    peak_y = max(yrs, key=lambda y: vals[y])
    peak_v, cur_v = vals[peak_y], vals[yrs[-1]]
    pct = cur_v / peak_v * 100
    rising = peak_y >= yrs[-1] - 1
    col = RISING if rising else PAST

    fig, ax = plt.subplots(figsize=(7.2, 4.0))
    ax.fill_between(yrs, ys, color=col, alpha=0.16, lw=0)
    ax.plot(yrs, ys, color=col, lw=2.1)
    ax.plot([peak_y], [peak_v], "o", color=col, ms=6)
    ax.annotate(f"{t['peak']} {peak_y}", xy=(peak_y, peak_v), xytext=(0, 9),
                textcoords="offset points", ha="center", fontsize=9, color=col)
    if not rising:
        ax.annotate(f"{yrs[-1]}: {pct:.0f}% {t['of_peak']}", xy=(yrs[-1], cur_v),
                    xytext=(-6, 10), textcoords="offset points", ha="right",
                    fontsize=9, color=col)

    ax.set_ylabel(t["y"], fontsize=10)
    ax.set_ylim(0, peak_v * 1.28)
    ax.set_xlim(min(yrs), max(yrs))
    ax.grid(axis="y", color="#ededea")
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    ax.spines["left"].set_color(RULE); ax.spines["bottom"].set_color(RULE)
    ax.set_title(name, loc="left", fontsize=13, fontweight="bold", pad=24)
    sub = (t["subrise"].format(py=peak_y) if rising
           else t["sub"].format(py=peak_y, pv=peak_v, yr=yrs[-1], cv=cur_v, pct=pct))
    ax.annotate(sub.replace(",", " "), xy=(0, 1.015), xycoords="axes fraction",
                va="bottom", fontsize=9.5, color=MUTED)
    ax.annotate(t["src"].format(yr=latest + 1), xy=(0, -0.20), xycoords="axes fraction",
                va="top", fontsize=7.5, color=MUTED)

    path = os.path.join(outdir, f"{name.lower().replace(' ', '_')}.{fmt}")
    fig.savefig(path, format=fmt, dpi=dpi, bbox_inches="tight")
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
    print(f"Statistical Review laddad, serien slutar {latest}.\n")

    wanted = [c for c in COUNTRIES if a.countries is None or c[idx] in a.countries]
    past = 0
    for key, sv, en in wanted:
        name = sv if a.lang == "sv" else en
        if key not in series:
            print(f"  !! {key} saknas i arbetsboken - hoppar over")
            continue
        path, py, pct, rising = chart(name, series[key], t, latest,
                                      a.outdir, a.format, a.dpi)
        past += 0 if rising else 1
        flag = "^" if rising else " "
        print(f" {flag} {name:14} topp {py}  {pct:5.1f}%  ->  {path}")

    if a.countries is None and WORLD in series:
        vals = series[WORLD]
        yrs = sorted(vals)
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        fig, ax = plt.subplots(figsize=(7.2, 4.0))
        ax.fill_between(yrs, [vals[y] for y in yrs], color=INK, alpha=0.10, lw=0)
        ax.plot(yrs, [vals[y] for y in yrs], color=INK, lw=2.2)
        ax.set_ylabel(t["y"], fontsize=10)
        ax.set_ylim(0, max(vals.values()) * 1.20)
        ax.set_xlim(min(yrs), max(yrs))
        ax.grid(axis="y", color="#ededea")
        for s in ("top", "right"):
            ax.spines[s].set_visible(False)
        ax.spines["left"].set_color(RULE); ax.spines["bottom"].set_color(RULE)
        ax.set_title(t["world"], loc="left", fontsize=13, fontweight="bold", pad=34)
        ax.annotate(t["wsub"], xy=(0, 1.015), xycoords="axes fraction",
                    va="bottom", fontsize=9.5, color=MUTED)
        ax.annotate(t["src"].format(yr=latest + 1), xy=(0, -0.20),
                    xycoords="axes fraction", va="top", fontsize=7.5, color=MUTED)
        p = os.path.join(a.outdir, f"varlden.{a.format}")
        fig.savefig(p, format=a.format, dpi=a.dpi, bbox_inches="tight")
        plt.close(fig)
        print(f"   {'Varlden':14} {'':16}  ->  {p}")

    print("\n" + t["cnt"].format(n=past, tot=len(wanted)))


if __name__ == "__main__":
    main()

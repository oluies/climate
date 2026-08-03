//| mvnDeps:
//| - org.duckdb:duckdb_jdbc:1.5.5.0

// Reusable data-refresh script for the "Without the Hot Air" 2026 revision.
// Mill single-file Scala script (Mill 1.1+). Fetches an open dataset, loads it
// into DuckDB, computes current values, and regenerates a figure as SVG.
//
// Usage (from the repo root):  mill Refresh.scala uk
//
// Each figure is one @main. Add more by copying the `uk` pattern with a different
// country / source list / output path. Data: Our World in Data (Ember + Energy
// Institute), CC BY. The generated SVG is our own work under the edition's licence.

import java.sql.DriverManager
import scala.collection.mutable.ArrayBuffer

val CSV_URL = "https://ourworldindata.org/grapher/electricity-prod-source-stacked.csv?csvType=full"

// source name in the OWID file -> line colour
val SOURCES = List(
  "Coal" -> "#333333", "Gas" -> "#c0392b", "Nuclear" -> "#8e44ad",
  "Wind" -> "#1a7f6b", "Solar" -> "#e1a731", "Hydropower" -> "#2e86c1",
  "Bioenergy" -> "#8a6d3b"
)

case class YrRow(year: Int, v: Map[String, Double])

def fetchRows(country: String, fromYear: Int): List[YrRow] = {
  val dataDir = os.pwd / "data-refresh"
  os.makeDir.all(dataDir)
  val csv = dataDir / "owid-electricity.csv"
  if (!os.exists(csv)) {
    println("fetching OWID electricity data ...")
    os.write.over(csv, requests.get(CSV_URL).text())
  }
  val conn = DriverManager.getConnection("jdbc:duckdb:")
  try {
    val cols = SOURCES.map { case (s, _) => s""""$s"""" }.mkString(", ")
    val sql =
      s"""SELECT "Year", $cols
          FROM read_csv_auto('${csv.toString.replace("'", "''")}')
          WHERE "Entity" = '$country' AND "Year" >= $fromYear
          ORDER BY "Year""""
    val rs = conn.createStatement().executeQuery(sql)
    val rows = ArrayBuffer[YrRow]()
    while (rs.next()) {
      val y = rs.getInt(1)
      val m = SOURCES.zipWithIndex.map { case ((s, _), i) =>
        val o = rs.getObject(i + 2)
        s -> (if (o == null) 0.0 else o.toString.toDouble)
      }.toMap
      rows += YrRow(y, m)
    }
    rows.toList
  } finally conn.close()
}

def renderSvg(title: String, rows: List[YrRow]): String = {
  val yMin = rows.map(_.year).min; val yMax = rows.map(_.year).max
  val W = 900; val H = 520; val ml = 60; val mr = 150; val mt = 44; val mb = 52
  val pw = W - ml - mr; val ph = H - mt - mb
  val maxV = math.max(1.0, rows.flatMap(_.v.values).max)
  val yTop = math.ceil(maxV / 40) * 40
  def sx(y: Int) = ml + (y - yMin).toDouble / (yMax - yMin) * pw
  def sy(v: Double) = mt + (1 - v / yTop) * ph
  val b = new StringBuilder
  b ++= s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $W $H" font-family="system-ui,-apple-system,sans-serif">\n"""
  b ++= s"""<rect width="$W" height="$H" fill="#ffffff"/>\n"""
  b ++= s"""<text x="$ml" y="26" font-size="15" font-weight="700" fill="#161d1b">$title</text>\n"""
  var gy = 0.0
  while (gy <= yTop) {
    val y = sy(gy)
    b ++= f"""<line x1="$ml" y1="$y%.1f" x2="${ml + pw}" y2="$y%.1f" stroke="#e6e9e6"/>\n"""
    b ++= f"""<text x="${ml - 8}" y="${y + 4}%.1f" font-size="11" text-anchor="end" fill="#7b8683">${gy}%.0f</text>\n"""
    gy += 80
  }
  b ++= f"""<text x="16" y="${mt + ph / 2}" font-size="11" fill="#7b8683" transform="rotate(-90 16 ${mt + ph / 2})" text-anchor="middle">TWh per year</text>\n"""
  var yr = (math.ceil(yMin / 5.0).toInt) * 5
  while (yr <= yMax) {
    val x = sx(yr)
    b ++= f"""<text x="$x%.1f" y="${mt + ph + 20}" font-size="11" text-anchor="middle" fill="#7b8683">$yr</text>\n"""
    yr += 5
  }
  // avoid overlapping end-labels: nudge each to its own slot
  val ends = SOURCES.map { case (s, c) => (s, c, sy(rows.last.v(s))) }.sortBy(_._3)
  var prevY = -100.0
  val placed = ends.map { case (s, c, y0) =>
    val y = if (y0 - prevY < 13) prevY + 13 else y0
    prevY = y; (s, c, y)
  }
  for ((s, c) <- SOURCES) {
    val pts = rows.map(r => f"${sx(r.year)}%.1f,${sy(r.v(s))}%.1f").mkString(" ")
    b ++= s"""<polyline points="$pts" fill="none" stroke="$c" stroke-width="2.2"/>\n"""
  }
  for ((s, c, y) <- placed)
    b ++= f"""<text x="${ml + pw + 8}" y="${y + 4}%.1f" font-size="11" fill="$c">$s</text>\n"""
  b ++= "</svg>\n"
  b.toString
}

@main
def uk(): Unit = {
  // The f-interpolator uses the default locale; on a Swedish machine that means a
  // decimal comma, which corrupts every SVG coordinate. Force a dot.
  java.util.Locale.setDefault(java.util.Locale.US)
  val rows = fetchRows("United Kingdom", 1985)
  if (rows.isEmpty) { System.err.println("FEL: no United Kingdom rows in the data"); sys.exit(1) }
  val yMax = rows.map(_.year).max

  val outImg = os.pwd / "without-hot-air" / "Images" / "fig-uk-electricity-mix.svg"
  os.write.over(outImg, renderSvg(s"UK electricity generation by source, 1985–$yMax", rows))

  // values table for chapter footnotes
  val keyYears = List(2008, yMax)
  val tbl = new StringBuilder
  tbl ++= s"UK electricity generation (TWh). Source: Our World in Data (Ember + Energy Institute). Latest complete year in the data: $yMax.\n\n"
  tbl ++= "| Source | " + keyYears.mkString(" | ") + " |\n|---|" + ("---|" * keyYears.size) + "\n"
  for ((s, _) <- SOURCES) {
    val cells = keyYears.map(ky => rows.find(_.year == ky).map(r => f"${r.v(s)}%.0f").getOrElse("-"))
    tbl ++= s"| $s | " + cells.mkString(" | ") + " |\n"
  }
  os.write.over(os.pwd / "data-refresh" / "uk-electricity-values.md", tbl.toString)
  val csvU = new StringBuilder; csvU ++= "year,source,twh\n"
  for (r <- rows; (s, _) <- SOURCES) csvU ++= s"${r.year},$s,${r.v(s).round}\n"
  os.write.over(os.pwd / "data-refresh" / "uk-electricity-mix.csv", csvU.toString)

  def at(y: Int, s: String) = rows.find(_.year == y).map(_.v(s)).getOrElse(Double.NaN)
  println(f"Coal:  2008 ${at(2008, "Coal")}%5.0f TWh  ->  $yMax ${at(yMax, "Coal")}%5.0f TWh")
  println(f"Wind:  2008 ${at(2008, "Wind")}%5.0f TWh  ->  $yMax ${at(yMax, "Wind")}%5.0f TWh")
  println(f"Solar: 2008 ${at(2008, "Solar")}%5.0f TWh  ->  $yMax ${at(yMax, "Solar")}%5.0f TWh")
  println(s"wrote $outImg")
}

// ---- Cost decline: two side-by-side panels, base year vs latest ----
// Global weighted-average LCOE (IRENA via OWID) plus the solar module price.

@main
def costs(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  os.makeDir.all(dir)
  def fetch(slug: String, file: String): os.Path = {
    val p = dir / file
    if (!os.exists(p)) os.write.over(p, requests.get(s"https://ourworldindata.org/grapher/$slug.csv?csvType=full", readTimeout = 60000).text())
    p
  }
  val modCsv = fetch("solar-pv-prices", "owid-solar-module.csv")
  val lcoeCsv = fetch("levelized-cost-of-energy", "owid-lcoe.csv")

  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  def one(sql: String): Double = { val r = st.executeQuery(sql); r.next(); r.getDouble(1) }
  val mod2008 = one(s"""SELECT "Solar PV module cost" FROM read_csv_auto('$modCsv') WHERE Entity='World' AND Year=2008""")
  val modYr = one(s"""SELECT max(Year) FROM read_csv_auto('$modCsv') WHERE Entity='World' AND "Solar PV module cost" IS NOT NULL""").toInt
  val modNow = one(s"""SELECT "Solar PV module cost" FROM read_csv_auto('$modCsv') WHERE Entity='World' AND Year=$modYr""")

  val base = one(s"""SELECT min(Year) FROM read_csv_auto('$lcoeCsv') WHERE Entity='World' AND "Solar photovoltaic" IS NOT NULL""").toInt
  val last = one(s"""SELECT max(Year) FROM read_csv_auto('$lcoeCsv') WHERE Entity='World' AND "Solar photovoltaic" IS NOT NULL""").toInt
  val techs = List(("Solar PV", "Solar photovoltaic", "#e1a731"), ("Onshore wind", "Onshore wind", "#1a7f6b"), ("Offshore wind", "Offshore wind", "#2e86c1"))
  def lcoe(col: String, yr: Int): Double =
    one(s"""SELECT "$col"*1000 FROM read_csv_auto('$lcoeCsv') WHERE Entity='World' AND Year=$yr""") // $/kWh -> $/MWh
  val rows = techs.map { case (label, col, color) => (label, color, lcoe(col, base), lcoe(col, last)) }
  conn.close()

  val tbl = new StringBuilder
  tbl ++= f"Global weighted-average LCOE (USD/MWh), IRENA via Our World in Data. Solar module cost (USD/W): $mod2008%.2f (2008) to $modNow%.2f ($modYr).\n\n"
  tbl ++= s"| Technology | $base | $last |\n|---|---|---|\n"
  for ((l, _, b, n) <- rows) tbl ++= f"| $l | ${b}%.0f | ${n}%.0f |\n"
  os.write.over(dir / "cost-decline-values.md", tbl.toString)
  // Tidy CSV for the seaborn renderer (data in Scala/DuckDB, charting in Python).
  val csv = new StringBuilder; csv ++= "tech,year,lcoe\n"
  for ((l, _, b, n) <- rows) { csv ++= s"$l,$base,${b.round}\n"; csv ++= s"$l,$last,${n.round}\n" }
  os.write.over(dir / "cost-decline.csv", csv.toString)
  print(tbl.toString)
  println(f"solar module USD/W: $mod2008%.2f (2008) -> $modNow%.2f ($modYr); wrote data-refresh/cost-decline.csv")
  println("render the figure: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/cost_decline.py data-refresh/cost-decline.csv without-hot-air/Images/fig-cost-decline.svg")
}

def renderCostSvg(base: Int, last: Int, rows: List[(String, String, Double, Double)]): String = {
  val W = 820; val H = 380; val mt = 66; val mb = 54; val ml = 62
  val panelW = 300; val gap = 60; val p1 = ml; val p2 = ml + panelW + gap
  val ph = H - mt - mb
  val maxV = math.ceil(rows.flatMap(r => List(r._3, r._4)).max / 50) * 50
  def by(v: Double) = mt + (1 - v / maxV) * ph
  val n = rows.size
  def panel(x0: Int, year: Int, pick: ((String, String, Double, Double)) => Double, b: StringBuilder): Unit = {
    b ++= s"""<text x="${x0 + panelW / 2}" y="${mt - 14}" font-size="14" font-weight="700" text-anchor="middle" fill="#161d1b">$year</text>\n"""
    val bw = panelW.toDouble / n * 0.56; val step = panelW.toDouble / n
    for ((r, i) <- rows.zipWithIndex) {
      val v = pick(r); val cx = x0 + i * step + step / 2; val bx = cx - bw / 2
      b ++= f"""<rect x="$bx%.1f" y="${by(v)}%.1f" width="$bw%.1f" height="${mt + ph - by(v)}%.1f" fill="${r._2}" rx="2"/>\n"""
      b ++= f"""<text x="$cx%.1f" y="${by(v) - 5}%.1f" font-size="11" text-anchor="middle" fill="#46534f">${v}%.0f</text>\n"""
      b ++= f"""<text x="$cx%.1f" y="${mt + ph + 16}" font-size="10.5" text-anchor="middle" fill="#7b8683">${r._1}</text>\n"""
    }
  }
  val b = new StringBuilder
  b ++= s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $W $H" font-family="system-ui,-apple-system,sans-serif">\n"""
  b ++= s"""<rect width="$W" height="$H" fill="#ffffff"/>\n"""
  b ++= s"""<text x="$ml" y="26" font-size="15" font-weight="700" fill="#161d1b">Levelized cost of electricity, global weighted average (USD per MWh)</text>\n"""
  var gy = 0.0
  while (gy <= maxV) {
    val y = by(gy)
    b ++= f"""<line x1="$ml" y1="$y%.1f" x2="${p2 + panelW}" y2="$y%.1f" stroke="#ecefec"/>\n"""
    b ++= f"""<text x="${ml - 8}" y="${y + 4}%.1f" font-size="10.5" text-anchor="end" fill="#7b8683">${gy}%.0f</text>\n"""
    gy += 100
  }
  panel(p1, base, r => r._3, b)
  panel(p2, last, r => r._4, b)
  b ++= "</svg>\n"
  b.toString
}

// ---- Chapter K: UK primary energy history (stacked area) from OWID ----

@main
def chapterK(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  os.makeDir.all(dir)
  val src = dir / "owid-energy-by-source.csv"
  if (!os.exists(src))
    os.write.over(src, requests.get("https://ourworldindata.org/grapher/energy-consumption-by-source-and-country.csv?csvType=full", readTimeout = 60000).text())
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  val sql =
    s"""SELECT "Year" AS y,
         round(COALESCE("Coal",0))    AS Coal,
         round(COALESCE("Oil",0))     AS Oil,
         round(COALESCE("Gas",0))     AS Gas,
         round(COALESCE("Nuclear",0)) AS Nuclear,
         round(COALESCE("Wind",0)+COALESCE("Solar",0)+COALESCE("Hydropower",0)
               +COALESCE("Other renewables",0)+COALESCE("Biofuels",0)) AS Renewables
       FROM read_csv_auto('${src.toString.replace("'", "''")}')
       WHERE "Entity"='United Kingdom' AND "Year">=1965 ORDER BY "Year""""
  val rs = st.executeQuery(sql)
  val cats = List("Coal", "Oil", "Gas", "Nuclear", "Renewables")
  val out = new StringBuilder; out ++= "year,category,twh\n"
  var last = 0
  while (rs.next()) {
    val y = rs.getInt(1); last = y
    for ((c, i) <- cats.zipWithIndex) out ++= s"$y,$c,${rs.getInt(i + 2)}\n"
  }
  conn.close()
  os.write.over(dir / "uk-primary-energy.csv", out.toString)
  println(s"wrote data-refresh/uk-primary-energy.csv (1965-$last)")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/uk_primary_energy.py data-refresh/uk-primary-energy.csv without-hot-air/Images/fig-uk-primary-energy.svg")
}

// ---- Chapter K: UK electricity per person (MacKay's kWh/d/p units) ----

@main
def chapterKElec(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  os.makeDir.all(dir)
  val elec = dir / "owid-electricity.csv"
  if (!os.exists(elec)) os.write.over(elec, requests.get(CSV_URL, readTimeout = 60000).text())
  val popf = dir / "owid-population.csv"
  if (!os.exists(popf)) os.write.over(popf, requests.get("https://ourworldindata.org/grapher/population.csv?csvType=full", readTimeout = 60000).text())
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  val cols = SOURCES.map { case (s, _) => s""""$s"""" }.mkString(", ")
  val sql =
    s"""WITH pop AS (SELECT "Year" y, "Population" p FROM read_csv_auto('$popf') WHERE "Entity"='United Kingdom'),
             lastp AS (SELECT p FROM pop ORDER BY y DESC LIMIT 1)
        SELECT e."Year" AS y, $cols, COALESCE(pop.p, (SELECT p FROM lastp)) AS population
        FROM read_csv_auto('${elec.toString.replace("'", "''")}') e LEFT JOIN pop ON pop.y = e."Year"
        WHERE e."Entity"='United Kingdom' AND e."Year">=1985 ORDER BY e."Year""""
  val rs = st.executeQuery(sql)
  val out = new StringBuilder; out ++= "year,source,kwhdp\n"
  var last = 0
  while (rs.next()) {
    val y = rs.getInt("y"); last = y
    val pop = rs.getDouble("population")
    for ((s, _) <- SOURCES) {
      val twh = Option(rs.getObject(s)).map(_.toString.toDouble).getOrElse(0.0)
      val kwhdp = if (pop > 0) twh * 1e9 / pop / 365.0 else 0.0
      out ++= f"$y,$s,$kwhdp%.2f\n"
    }
  }
  conn.close()
  os.write.over(dir / "uk-electricity-percapita.csv", out.toString)
  println(s"wrote data-refresh/uk-electricity-percapita.csv (1985-$last)")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/uk_electricity_percapita.py data-refresh/uk-electricity-percapita.csv without-hot-air/Images/fig-uk-electricity-percapita.svg")
}

// ---- Chapter K: GB demand over a winter week (MacKay's Fig K.3) from Elexon ----

@main
def chapterKDemand(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val url = "https://data.elexon.co.uk/bmrs/api/v1/demand/outturn?settlementDateFrom=2025-01-13&settlementDateTo=2025-01-19&format=json"
  val js = ujson.read(cachedGet(url, dir / "gb-demand-week.json"))
  val out = new StringBuilder; out ++= "ts,gw\n"
  for (r <- js("data").arr.sortBy(_("startTime").str))
    out ++= f"${r("startTime").str},${r("initialDemandOutturn").num / 1000.0}%.2f\n"
  os.write.over(dir / "gb-demand-week.csv", out.toString)
  println("wrote data-refresh/gb-demand-week.csv")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/gb_demand_week.py data-refresh/gb-demand-week.csv without-hot-air/Images/fig-gb-demand-week.svg")
}

// ---- Chapter J: world energy, from the EI Statistical Review workbook ----
// The Energy Institute's 2026 edition (published 30 June 2026, data for 2025).
// The workbook is behind a registration form, so it is a manual download rather
// than a fetch: put EI-Stats-Review-ALL-data.xlsx in data-refresh/ as
// ei-stats-review-all-data.xlsx. Note the sheet layout: each fuel block repeats,
// and the YEAR LABEL SITS AT THE END of its block (col H = 2024, col O = 2025 on
// "TES by fuel"), which is the opposite of the obvious reading.

val EI_XLSX = "data-refresh/ei-stats-review-all-data.xlsx"
val GJ_PER_CAPITA_TO_KWH_PER_DAY = 1e9 / 3.6e6 / 365.0   // 1 GJ/person/year in kWh/d

@main
def chapterJ(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val xlsx = os.pwd / os.RelPath(EI_XLSX)
  if (!os.exists(xlsx)) {
    System.err.println(s"FEL: $EI_XLSX saknas. Ladda ner arbetsboken fran energyinst.org/statistical-review och lagg den dar.")
    sys.exit(1)
  }
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  st.execute("INSTALL excel"); st.execute("LOAD excel")
  def sheet(name: String, range: String) =
    s"read_xlsx('${xlsx.toString.replace("'", "''")}', sheet='$name', header=false, all_varchar=true, range='$range')"

  // -- energy supply by fuel, 2024 beside 2025 --
  val fuels = List(("Oil", "B", "I"), ("Gas", "C", "J"), ("Coal", "D", "K"),
                   ("Nuclear", "E", "L"), ("Hydro", "F", "M"), ("Renewables", "G", "N"))
  val cols = fuels.map { case (n, a, b) => s"""TRY_CAST("$a" AS DOUBLE) AS ${n}_24, TRY_CAST("$b" AS DOUBLE) AS ${n}_25""" }.mkString(", ")
  val rs = st.executeQuery(
    s"""SELECT $cols, TRY_CAST("H" AS DOUBLE) t24, TRY_CAST("O" AS DOUBLE) t25 FROM ${sheet("TES by fuel", "A4:O99")} WHERE A = 'Total World'""")
  rs.next()
  val energy = fuels.map { case (n, _, _) => (n, rs.getDouble(s"${n}_24"), rs.getDouble(s"${n}_25")) }
  val (t24, t25) = (rs.getDouble("t24"), rs.getDouble("t25"))
  val se = new StringBuilder; se ++= "source,ej_2024,ej_2025,growth_ej\n"
  for ((n, a, b) <- energy) se ++= f"$n,$a%.2f,$b%.2f,${b - a}%.2f\n"
  os.write.over(dir / "world-energy-2025.csv", se.toString)

  // -- electricity generation by fuel; oil, gas and coal collapse to one bar --
  val eg = List(("Oil", "B", "J"), ("Gas", "C", "K"), ("Coal", "D", "L"), ("Nuclear", "E", "M"),
                ("Hydro", "F", "N"), ("Renewables", "G", "O"), ("Other", "H", "P"))
  val ecols = eg.map { case (n, a, b) => s"""TRY_CAST("$a" AS DOUBLE) AS ${n}_24, TRY_CAST("$b" AS DOUBLE) AS ${n}_25""" }.mkString(", ")
  val rs2 = st.executeQuery(
    s"""SELECT $ecols, TRY_CAST("I" AS DOUBLE) t24, TRY_CAST("Q" AS DOUBLE) t25 FROM ${sheet("Elec generation by fuel", "A4:Q69")} WHERE A = 'Total World'""")
  rs2.next()
  val elec = eg.map { case (n, _, _) => (n, rs2.getDouble(s"${n}_24"), rs2.getDouble(s"${n}_25")) }
  val et24 = rs2.getDouble("t24"); val et25 = rs2.getDouble("t25")
  def sumOf(names: Set[String], f: ((String, Double, Double)) => Double) = elec.filter(x => names(x._1)).map(f).sum
  val fos24 = sumOf(Set("Oil", "Gas", "Coal"), _._2); val fos25 = sumOf(Set("Oil", "Gas", "Coal"), _._3)
  // Split the renewables bar into wind, solar and the rest; the interesting fact
  // of 2025 is inside it (solar passing wind), and the aggregate would hide it.
  val ren3 = List(("Wind", "B", "G"), ("Solar", "C", "H"), ("Other renewables", "E", "J"))
  val rcols = ren3.map { case (n, a, b) => s"""TRY_CAST("$a" AS DOUBLE) AS "${n}_24", TRY_CAST("$b" AS DOUBLE) AS "${n}_25"""" }.mkString(", ")
  val rs4 = st.executeQuery(
    s"""SELECT $rcols FROM ${sheet("Renewables Generation by Source", "A4:K140")} WHERE A = 'Total World'""")
  rs4.next()
  val rensplit = ren3.map { case (n, _, _) => (n, rs4.getDouble(s"${n}_24"), rs4.getDouble(s"${n}_25")) }

  val ee = new StringBuilder; ee ++= "source,twh_2024,twh_2025\n"
  ee ++= f"Fossil,$fos24%.0f,$fos25%.0f\n"
  for ((n, a, b) <- elec if Set("Nuclear", "Hydro", "Other")(n)) ee ++= f"$n,$a%.0f,$b%.0f\n"
  for ((n, a, b) <- rensplit) ee ++= f"$n,$a%.0f,$b%.0f\n"
  os.write.over(dir / "world-electricity-2025.csv", ee.toString)
  val renSum = rensplit.map(_._3).sum
  val renAgg = elec.find(_._1 == "Renewables").map(_._3).getOrElse(0.0)
  if (math.abs(renSum - renAgg) > 5)
    System.err.println(f"VARNING: wind+solar+other = $renSum%.0f TWh men Renewables-kolumnen = $renAgg%.0f TWh")

  // -- electricity generation by region, 1985 to 2025: what China built --
  // This sheet starts at 1985 in column B, so column i holds year 1984 + i.
  val elecRegions = List("Total World", "China", "US", "Total Europe", "India", "Total Africa")
  val egSel = (0 until 41).map(i => s"""TRY_CAST("${colName(i + 1)}" AS DOUBLE)""").mkString(", ")
  val rs6 = st.executeQuery(
    s"""SELECT A, $egSel FROM ${sheet("Electricity Generation - TWh", "A4:AP140")}
        WHERE A IN (${elecRegions.map(r => s"'$r'").mkString(", ")})""")
  val eg2 = new StringBuilder; eg2 ++= "region,year,twh\n"
  val elecSeries = scala.collection.mutable.Map[String, Map[Int, Double]]()
  while (rs6.next()) {
    val name = rs6.getString(1).replace("Total ", "")
    val m = scala.collection.mutable.Map[Int, Double]()
    for (i <- 0 until 41) {
      val v = rs6.getObject(i + 2)
      if (v != null) { val d = v.toString.toDouble; m(1985 + i) = d; eg2 ++= f"$name,${1985 + i},$d%.0f\n" }
    }
    elecSeries(name) = m.toMap
  }
  os.write.over(dir / "world-electricity-history.csv", eg2.toString)

  // -- CO2 from energy: who is driving the increase, and since when --
  // Column AK is 2000 and BJ is 2025 on this sheet; BK and BL are growth rates.
  val co2Regions = List("Total World", "China", "India", "US", "Total Europe", "Total Africa",
                        "Total Middle East", "Total S. & Cent. America")
  val rs5 = st.executeQuery(
    s"""SELECT A, TRY_CAST("AK" AS DOUBLE) y2000, TRY_CAST("BJ" AS DOUBLE) y2025
        FROM ${sheet("CO2 from Energy", "A4:BL140")}
        WHERE A IN (${co2Regions.map(r => s"'$r'").mkString(", ")})""")
  val co2 = scala.collection.mutable.ArrayBuffer[(String, Double, Double)]()
  while (rs5.next()) co2 += ((rs5.getString(1).replace("Total ", ""), rs5.getDouble(2), rs5.getDouble(3)))
  val sc = new StringBuilder; sc ++= "region,mt_2000,mt_2025,change_mt\n"
  for ((n, a, b) <- co2.sortBy(x => -(x._3 - x._2))) sc ++= f"$n,$a%.0f,$b%.0f,${b - a}%.0f\n"
  os.write.over(dir / "world-co2-since-2000.csv", sc.toString)

  // -- supply per person, in MacKay's kWh/d, 1965 to 2025 --
  val regions = List("Total World", "US", "Total Europe", "China", "India", "Total Africa", "Sweden", "United Kingdom")
  // Column names are quoted: the workbook runs past column "AS", which is a keyword.
  val pcSel = (0 until 61).map(i => s"""TRY_CAST("${colName(i + 1)}" AS DOUBLE)""").mkString(", ")
  val rs3 = st.executeQuery(
    s"SELECT A, $pcSel FROM ${sheet("TES per Capita", "A4:BL124")} WHERE A IN (${regions.map(r => s"'$r'").mkString(", ")})")
  val pc = new StringBuilder; pc ++= "region,year,kwh_per_day\n"
  while (rs3.next()) {
    val name = rs3.getString(1)
    for (i <- 0 until 61) {
      val v = rs3.getObject(i + 2)
      if (v != null) pc ++= f"${name.replace("Total ", "")},${1965 + i},${v.toString.toDouble * GJ_PER_CAPITA_TO_KWH_PER_DAY}%.1f\n"
    }
  }
  os.write.over(dir / "world-tes-percapita.csv", pc.toString)

  // Every country, as Parquet, for the DuckDB-WASM chart in the chapter. The
  // reader's browser queries this file directly; nothing is precomputed for it.
  val assets = os.pwd / "book" / "assets"; os.makeDir.all(assets)
  val allSel = (0 until 61).map(i => s"""TRY_CAST("${colName(i + 1)}" AS DOUBLE) AS "y${1965 + i}"""").mkString(", ")
  val unpiv = (0 until 61).map(i => s"""SELECT replace(A, 'Total ', '') AS region, ${1965 + i} AS year,
      round(TRY_CAST("${colName(i + 1)}" AS DOUBLE) * $GJ_PER_CAPITA_TO_KWH_PER_DAY, 2) AS kwh_per_day
      FROM ${sheet("TES per Capita", "A4:BL124")} WHERE A IS NOT NULL""").mkString(" UNION ALL ")
  st.execute(
    s"""COPY (SELECT * FROM ($unpiv) WHERE kwh_per_day IS NOT NULL ORDER BY region, year)
        TO '${(assets / "tes-percapita.parquet").toString.replace("'", "''")}' (FORMAT parquet)""")
  println(s"wrote book/assets/tes-percapita.parquet")
  conn.close()

  val fossil25 = energy.filter(x => Set("Oil", "Gas", "Coal")(x._1)).map(_._3).sum
  val growth = t25 - t24
  val fossilGrowth = energy.filter(x => Set("Oil", "Gas", "Coal")(x._1)).map(x => x._3 - x._2).sum
  val ren = energy.find(_._1 == "Renewables").get
  println(f"supply      $t24%.1f -> $t25%.1f EJ  (+$growth%.2f, ${growth / t24 * 100}%.2f%%)")
  println(f"fossil      $fossil25%.1f EJ = ${fossil25 / t25 * 100}%.1f%% of supply; grew $fossilGrowth%.2f EJ = ${fossilGrowth / growth * 100}%.0f%% of growth")
  println(f"renewables  ${ren._2}%.2f -> ${ren._3}%.2f EJ (+${ren._3 - ren._2}%.2f, ${(ren._3 - ren._2) / ren._2 * 100}%.1f%%) - largest single contributor: ${energy.forall(x => x._1 == "Renewables" || x._3 - x._2 < ren._3 - ren._2)}")
  val cw = co2.find(_._1 == "World").get; val cc = co2.find(_._1 == "China").get
  println(f"CO2 2025    ${cw._3}%.0f Mt; since 2000 the world rose ${cw._3 - cw._2}%.0f Mt, China ${cc._3 - cc._2}%.0f Mt = ${(cc._3 - cc._2) / (cw._3 - cw._2) * 100}%.0f%% of it")
  val cn = elecSeries("China"); val wd = elecSeries("World")
  val cnAdd = cn(2025) - cn(2000); val wdAdd = wd(2025) - wd(2000)
  println(f"China elec  ${cn(1985)}%.0f (1985) -> ${cn(2025)}%.0f TWh (2025), ${cn(2025) / cn(1985)}%.0fx; ${cn(2025) / wd(2025) * 100}%.0f%% of world")
  println(f"            added $cnAdd%.0f TWh since 2000 = ${cnAdd / wdAdd * 100}%.0f%% of world growth; US+Europe generate ${elecSeries("US")(2025) + elecSeries("Europe")(2025)}%.0f TWh today")
  println(f"electricity $et24%.0f -> $et25%.0f TWh (+${et25 - et24}%.0f); fossil $fos24%.0f -> $fos25%.0f (${fos25 - fos24}%+.0f), now ${fos25 / et25 * 100}%.1f%%")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/world_energy_2025.py data-refresh/world-energy-2025.csv without-hot-air/Images/fig-world-energy-2025.svg")
  println("  python figures/world_electricity_2025.py data-refresh/world-electricity-2025.csv without-hot-air/Images/fig-world-electricity-2025.svg")
  println("  python figures/world_percapita.py data-refresh/world-tes-percapita.csv without-hot-air/Images/fig-world-percapita.svg")
}

// Excel column name for a 0-based index (0 -> A, 25 -> Z, 26 -> AA).
def colName(i: Int): String = {
  var n = i; var s = ""
  while (n >= 0) { s = ('A' + n % 26).toChar.toString + s; n = n / 26 - 1 }
  s
}

// ---- Chapter 3: how far electrification of cars has actually got ----
// Share of new cars sold that are electric (BEV + PHEV), IEA via Our World in Data.

@main
def chapter03(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val src = dir / "owid-ev-share.csv"
  if (!os.exists(src))
    os.write.over(src, requests.get(
      "https://ourworldindata.org/grapher/electric-car-sales-share.csv?csvType=full",
      readTimeout = 60000).text())
  val want = List("Norway", "Sweden", "China", "United Kingdom", "World", "United States")
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val rs = conn.createStatement().executeQuery(
    s"""SELECT "Entity" AS country, "Year" AS y,
               "Share of new cars that are electric"::DOUBLE AS share
        FROM read_csv_auto('${src.toString.replace("'", "''")}')
        WHERE "Entity" IN (${want.map(w => s"'$w'").mkString(", ")}) AND "Year" >= 2015
        ORDER BY "Entity", "Year"""")
  val out = new StringBuilder; out ++= "country,year,share\n"
  val latest = scala.collection.mutable.Map[String, (Int, Double)]()
  while (rs.next()) {
    val c = rs.getString(1); val y = rs.getInt(2); val v = rs.getDouble(3)
    out ++= f"$c,$y,$v%.1f\n"
    if (!latest.contains(c) || latest(c)._1 < y) latest(c) = (y, v)
  }
  conn.close()
  os.write.over(dir / "ev-share.csv", out.toString)
  for (c <- want; (y, v) <- latest.get(c)) println(f"$c%-16s $y: $v%5.1f%% of new cars")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/ev_share.py data-refresh/ev-share.csv without-hot-air/Images/fig-ev-share.svg")
}

// ---- Chapter J figure 4: US states beside European countries ----
// MacKay's figure J.4 plots the American states against regions around Europe.
// Table J.5 carries no state-level rows, so the state data comes from the Census
// Bureau directly: population estimates as CSV, land area from the gazetteer zip.

@main
def chapterJ4(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)

  val popCsv = dir / "us-state-population.csv"
  if (!os.exists(popCsv))
    os.write.over(popCsv, requests.get(
      "https://www2.census.gov/programs-surveys/popest/datasets/2020-2024/state/totals/NST-EST2024-ALLDATA.csv",
      readTimeout = 60000).text())

  // The gazetteer ships as a zip with a single tab-separated member.
  val gazTxt = dir / "us-state-area.tsv"
  if (!os.exists(gazTxt)) {
    val bytes = requests.get(
      "https://www2.census.gov/geo/docs/maps-data/data/gazetteer/2024_Gazetteer/2024_Gaz_state_national.zip",
      readTimeout = 120000).bytes
    val zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))
    var e = zis.getNextEntry()
    while (e != null && !e.getName.endsWith(".txt")) e = zis.getNextEntry()
    if (e == null) { System.err.println("FEL: hittade ingen .txt i gazetteer-zippen"); sys.exit(1) }
    os.write.over(gazTxt, new String(zis.readAllBytes(), "UTF-8"))
  }

  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  // SUMLEV 040 is a state; ALAND is land area in square metres.
  val rs = st.executeQuery(
    s"""SELECT p."NAME" AS region, p."POPESTIMATE2024"::BIGINT AS population,
               (g."ALAND"::DOUBLE / 1e6)::BIGINT AS area_km2
        FROM read_csv_auto('${popCsv.toString.replace("'", "''")}') p
        JOIN read_csv_auto('${gazTxt.toString.replace("'", "''")}', delim='\t', header=true) g
          ON trim(g."NAME") = p."NAME"
        WHERE p."SUMLEV" = 40
        ORDER BY population DESC""")
  val out = new StringBuilder; out ++= "region,population,area_km2,kind\n"
  var n = 0
  while (rs.next()) { out ++= s"${rs.getString(1)},${rs.getLong(2)},${rs.getLong(3)},US state\n"; n += 1 }
  conn.close()

  // European countries come from the chapter's own table, already at 2023.
  val EURO = Set("Albania", "Austria", "Belarus", "Belgium", "Bosnia & Herzegovina", "Bulgaria",
    "Croatia", "Czech Republic", "Denmark", "England", "Estonia", "Finland", "France", "Germany",
    "Greece", "Hungary", "Iceland", "Ireland", "Italy", "Latvia", "Lithuania", "Malta", "Moldova",
    "Netherlands", "Norway", "Poland", "Portugal", "Republic of Macedonia", "Romania", "Scotland",
    "Serbia & Montenegro", "Slovakia", "Slovenia", "Spain", "Sweden", "Switzerland", "Ukraine", "Wales")
  for (line <- os.read.lines(dir / "populations-areas.csv").drop(1)) {
    val c = line.split(",")
    if (c.length >= 3 && EURO(c(0))) out ++= s"${c(0)},${c(1)},${c(2)},European country\n"
  }
  os.write.over(dir / "states-and-europe.csv", out.toString)
  println(s"wrote data-refresh/states-and-europe.csv ($n US states + European countries)")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/states_and_europe.py data-refresh/states-and-europe.csv without-hot-air/Images/fig-states-and-europe.svg")
}

// ---- Chapter 25: Germany's net electricity trade (export surplus -> import) ----
// OWID "Net electricity imports" (Ember): imports minus exports, TWh per year.
// Positive = net importer, negative = net exporter.

@main
def deTrade(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val src = dir / "owid-net-electricity-imports.csv"
  if (!os.exists(src))
    os.write.over(src, requests.get("https://ourworldindata.org/grapher/net-electricity-imports.csv?csvType=full", readTimeout = 60000).text())
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val rs = conn.createStatement().executeQuery(
    s"""SELECT "Year" AS y, "Net electricity imports" AS twh
        FROM read_csv_auto('${src.toString.replace("'", "''")}')
        WHERE "Entity"='Germany' AND "Year">=1990 AND "Net electricity imports" IS NOT NULL
        ORDER BY "Year"""")
  val out = new StringBuilder; out ++= "year,twh\n"
  var last = 0; var flip = 0; var prev = 0.0; var minY = 0; var minV = 0.0
  while (rs.next()) {
    val y = rs.getInt(1); val v = rs.getDouble(2); last = y
    if (y > 1990 && prev < 0 && v > 0) flip = y // keep the most recent flip: the 1990s wobbled
    if (v < minV) { minV = v; minY = y }
    prev = v
    out ++= f"$y,$v%.2f\n"
  }
  conn.close()
  os.write.over(dir / "de-net-trade.csv", out.toString)
  println(s"wrote data-refresh/de-net-trade.csv (1990-$last)")
  println(f"largest export surplus: $minY ${-minV}%.1f TWh  |  turned net importer: $flip")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/de_net_trade.py data-refresh/de-net-trade.csv without-hot-air/Images/fig-de-net-trade.svg")
}

// ---- GB capture prices (the cannibalization figure) from Elexon BMRS ----
// Half-hourly GB generation by fuel type and the market-index price (APXMIDP),
// joined on the settlement period. Capture price = sum(generation*price)/sum(generation);
// value factor = capture / time-weighted average price. Free, no ENTSO-E key.

import java.time.LocalDate

def gbCat(psr: String): String = psr match {
  case "Wind Offshore" | "Wind Onshore" => "Wind"
  case "Solar"     => "Solar"
  case "Nuclear"   => "Nuclear"
  case "Fossil Gas" => "Gas"
  case "Biomass"   => "Biomass"
  case _           => null
}

def cachedGet(url: String, cache: os.Path): String =
  if (os.exists(cache)) os.read(cache)
  else {
    val t = requests.get(url, readTimeout = 120000, connectTimeout = 30000).text()
    os.makeDir.all(cache / os.up); os.write.over(cache, t); t
  }

@main
def gbCapture(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val year = 2025
  val cache = os.pwd / "data-refresh" / "gb-cache"

  println("GB generation by fuel type (Elexon, monthly) ...")
  val gen = scala.collection.mutable.ArrayBuffer[(String, String, Double)]()
  for (m <- 1 to 12) {
    val d0 = LocalDate.of(year, m, 1); val d1 = d0.plusMonths(1)
    val url = s"https://data.elexon.co.uk/bmrs/api/v1/generation/actual/per-type?from=${d0}T00:00Z&to=${d1}T00:00Z&format=json"
    for (p <- ujson.read(cachedGet(url, cache / s"gen-$year-$m.json"))("data").arr) {
      val ts = p("startTime").str
      for (row <- p("data").arr) {
        val cat = gbCat(row("psrType").str)
        if (cat != null) gen += ((ts, cat, row("quantity").num))
      }
    }
  }
  println("GB market-index price APXMIDP (Elexon, weekly) ...")
  val price = scala.collection.mutable.ArrayBuffer[(String, Double)]()
  var d = LocalDate.of(year, 1, 1)
  while (d.getYear == year) {
    val e = d.plusDays(7)
    val url = s"https://data.elexon.co.uk/bmrs/api/v1/balancing/pricing/market-index?from=${d}T00:00Z&to=${e}T00:00Z&format=json"
    for (x <- ujson.read(cachedGet(url, cache / s"price-$d.json"))("data").arr if x("dataProvider").str == "APXMIDP")
      price += ((x("startTime").str, x("price").num))
    d = e
  }
  println(s"  gen rows ${gen.size}, price rows ${price.size}")

  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  conn.setAutoCommit(false)
  val st = conn.createStatement()
  st.execute("CREATE TABLE gen(ts VARCHAR, source VARCHAR, mw DOUBLE)")
  st.execute("CREATE TABLE price(ts VARCHAR, p DOUBLE)")
  val pg = conn.prepareStatement("INSERT INTO gen VALUES (?,?,?)")
  for ((ts, s, mw) <- gen) { pg.setString(1, ts); pg.setString(2, s); pg.setDouble(3, mw); pg.addBatch() }
  pg.executeBatch()
  val pp = conn.prepareStatement("INSERT INTO price VALUES (?,?)")
  for ((ts, p) <- price.groupBy(_._1).map { case (k, v) => (k, v.head._2) }) { pp.setString(1, ts); pp.setDouble(2, p); pp.addBatch() }
  pp.executeBatch()
  conn.commit()

  val ar = st.executeQuery("SELECT avg(p) FROM price WHERE ts IN (SELECT DISTINCT ts FROM gen)")
  ar.next(); val avgP = ar.getDouble(1)
  val rs = st.executeQuery("SELECT g.source, sum(g.mw*pr.p)/sum(g.mw) FROM gen g JOIN price pr ON g.ts = pr.ts GROUP BY g.source")
  val capMap = scala.collection.mutable.Map[String, Double]()
  while (rs.next()) capMap(rs.getString(1)) = rs.getDouble(2)
  conn.close()

  val order = List("Gas", "Nuclear", "Biomass", "Wind", "Solar").filter(capMap.contains)
  val tbl = new StringBuilder
  tbl ++= s"GB $year capture price and value factor by source. Reference: time-weighted average market-index price (APXMIDP) = ${f"$avgP%.1f"} GBP/MWh. Source: Elexon BMRS.\n\n"
  tbl ++= "| Source | Capture (GBP/MWh) | Value factor |\n|---|---|---|\n"
  for (s <- order) tbl ++= f"| $s | ${capMap(s)}%.1f | ${capMap(s) / avgP}%.2f |\n"
  os.write.over(os.pwd / "data-refresh" / "gb-capture-values.md", tbl.toString)
  val csvG = new StringBuilder; csvG ++= "source,capture,systemavg\n"
  for (s <- order) csvG ++= f"$s,${capMap(s)}%.1f,${avgP}%.1f\n"
  os.write.over(os.pwd / "data-refresh" / "gb-capture.csv", csvG.toString)
  print(tbl.toString)
  println(f"average market price ${avgP}%.1f GBP/MWh; wrote figure and values")
}

def renderCaptureSvg(year: Int, order: List[String], cap: Map[String, Double], avg: Double): String = {
  val colors = Map("Gas" -> "#c0392b", "Nuclear" -> "#8e44ad", "Biomass" -> "#8a6d3b", "Wind" -> "#1a7f6b", "Solar" -> "#e1a731")
  val W = 760; val H = 340; val ml = 92; val mr = 96; val mt = 48; val mb = 34
  val pw = W - ml - mr; val ph = H - mt - mb
  val maxV = math.max(avg, cap.values.max) * 1.18
  def bx(v: Double) = ml + v / maxV * pw
  val b = new StringBuilder
  b ++= s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $W $H" font-family="system-ui,-apple-system,sans-serif">\n"""
  b ++= s"""<rect width="$W" height="$H" fill="#ffffff"/>\n"""
  b ++= s"""<text x="$ml" y="26" font-size="15" font-weight="700" fill="#161d1b">GB $year capture price by source (GBP per MWh)</text>\n"""
  val gap = ph.toDouble / order.size; val bh = gap * 0.58
  for ((s, i) <- order.zipWithIndex) {
    val y = mt + i * gap + (gap - bh) / 2
    b ++= f"""<rect x="$ml" y="$y%.1f" width="${bx(cap(s)) - ml}%.1f" height="$bh%.1f" fill="${colors(s)}" rx="2"/>\n"""
    b ++= f"""<text x="${ml - 8}" y="${y + bh / 2 + 4}%.1f" font-size="12.5" text-anchor="end" fill="#161d1b">$s</text>\n"""
    b ++= f"""<text x="${bx(cap(s)) + 6}%.1f" y="${y + bh / 2 + 4}%.1f" font-size="11.5" fill="#46534f">${cap(s)}%.0f  (${cap(s) / avg}%.2f×)</text>\n"""
  }
  val ax = bx(avg)
  b ++= f"""<line x1="$ax%.1f" y1="${mt - 6}" x2="$ax%.1f" y2="${mt + ph + 4}" stroke="#161d1b" stroke-dasharray="4 3"/>\n"""
  b ++= f"""<text x="$ax%.1f" y="${mt + ph + 26}" font-size="11.5" text-anchor="middle" fill="#161d1b">system average ${avg}%.0f</text>\n"""
  b ++= "</svg>\n"
  b.toString
}

// ---- Chapter 6 solar figures ----
// Three charts from the EI Statistical Review workbook, replacing the OWID
// iframes with figures in the book's own units. Note the sheet layouts differ:
// "Solar Installed Capacity" starts its year header at column B = 2000, while
// "Solar Generation - TWh" starts at B = 1965. Both are read positionally.
@main
def chapter06(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val xlsx = (dir / "ei-stats-review-all-data.xlsx").toString.replace("'", "''")
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  st.execute("LOAD excel")

  def series(sheet: String, region: String, firstYear: Int, lastYear: Int, scale: Double) = {
    // Quote every identifier: column AS collides with the SQL keyword.
    val cols = (firstYear to lastYear).map(y => "\"" + colName(1 + (y - firstYear)) + "\"").mkString(",")
    val rs = st.executeQuery(
      s"""SELECT $cols FROM read_xlsx('$xlsx', sheet='$sheet', header=false,
          all_varchar=true, range='A4:${colName(1 + (lastYear - firstYear))}200')
          WHERE A='$region'""")
    val v = collection.mutable.ArrayBuffer[(Int, Double)]()
    if (rs.next()) for (i <- firstYear to lastYear) {
      val s = rs.getString(i - firstYear + 1)
      if (s != null && s.nonEmpty) v += ((i, s.toDouble / scale))
    }
    v.toSeq
  }

  // 1. World installed capacity in GW, against MacKay's 1250 GW fantasy.
  val capRegions = Seq("Total World", "China", "Germany", "United Kingdom")
  val cap = new StringBuilder; cap ++= "region,year,gw\n"
  for (r <- capRegions; (y, v) <- series("Solar Installed Capacity", r, 2000, 2025, 1000.0))
    cap ++= f"$r,$y,$v%.2f\n"
  os.write.over(dir / "solar-capacity.csv", cap.toString)

  // 2. Generation per person in kWh/d, the book's units. Population is taken
  // year by year from the OWID series already in this directory: using one
  // present-day figure across a 25-year series understates the early years
  // badly (world population was 6.15bn in 2000, not 8.23bn).
  val owid = (dir / "owid-population.csv").toString.replace("'", "''")
  val entity = Map("Total World" -> "World", "China" -> "China",
                   "Germany" -> "Germany", "United Kingdom" -> "United Kingdom")
  def population(region: String): Map[Int, Double] = {
    val rs = st.executeQuery(
      s"""SELECT "Year", "Population"
          FROM read_csv_auto('$owid') WHERE "Entity" = '${entity(region)}'
            AND "Year" BETWEEN 2000 AND 2025 ORDER BY "Year"""")
    val m = collection.mutable.Map[Int, Double]()
    while (rs.next()) m(rs.getInt(1)) = rs.getDouble(2)
    // The OWID series ends in 2023. Extend to 2025 at the mean growth rate of
    // the last three years so the final two points are not simply dropped;
    // the extrapolation moves the per-person figures by well under 1%.
    val last = m.keys.max
    val g = math.pow(m(last) / m(last - 3), 1.0 / 3.0)
    for (y <- (last + 1) to 2025) m(y) = m(y - 1) * g
    m.toMap
  }
  val pc = new StringBuilder; pc ++= "region,year,kwh_per_day\n"
  for (r <- capRegions) {
    val pop = population(r)
    for ((y, twh) <- series("Solar Generation - TWh", r, 1965, 2025, 1.0)
         if y >= 2000 && pop.contains(y))
      pc ++= f"$r,$y,${twh * 1e9 / pop(y) / 365}%.4f\n"
  }
  os.write.over(dir / "solar-percapita.csv", pc.toString)

  // 3. Power per unit area. Hand-entered from the sources in chapter 6's notes:
  // these are four specific installations, not a series, and there is no
  // workbook to read them from.
  os.write.over(dir / "solar-power-density.csv",
    """label,wm2,kind
      |Bavaria Solarpark 2008,5.0,PV
      |Ivanpah (Mojave desert),6.9,Solar thermal
      |MacKay's fantasy farm,10.0,Assumption
      |Cleve Hill (Kent) 2025,10.8,PV
      |""".stripMargin)
  conn.close()
  println("wrote data-refresh/solar-capacity.csv, solar-percapita.csv, solar-power-density.csv")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/solar_capacity.py data-refresh/solar-capacity.csv without-hot-air/Images/fig-solar-capacity.svg")
}

// ---- Chapter 6: MacKay's own figures, redone ----
// Irradiance comes from PVGIS (JRC), which is free and needs no key. PVGIS
// returns JSON; it is written to disk and queried with DuckDB rather than
// parsed in Scala, which keeps the data work in SQL as everywhere else here.
val PVGIS = "https://re.jrc.ec.europa.eu/api/v5_2"

def pvgisMonthly(cache: os.Path, lat: Double, lon: Double): Unit =
  if (!os.exists(cache)) {
    os.makeDir.all(cache / os.up)
    os.write.over(cache, requests.get(
      // ERA5 rather than the regional default: it is the only database that
      // covers Europe, the Americas and Africa on one basis, which a
      // cross-continent comparison figure needs.
      f"$PVGIS/MRcalc?lat=$lat%.4f&lon=$lon%.4f&horirrad=1&startyear=2016&endyear=2020&raddatabase=PVGIS-ERA5&outputformat=json",
      readTimeout = 90000, connectTimeout = 30000).text())
  }

// Daylight length from the standard sunrise equation, for the sunniness ratio.
def daylightHours(latDeg: Double, dayOfYear: Int): Double = {
  val lat = math.toRadians(latDeg)
  val decl = math.toRadians(23.44) * math.sin(2 * math.Pi * (dayOfYear - 81) / 365.0)
  val c = -math.tan(lat) * math.tan(decl)
  if (c >= 1) 0.0 else if (c <= -1) 24.0 else 2 * math.toDegrees(math.acos(c)) / 15.0
}

@main
def chapter06Figs(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  val DAYS = Array(31, 28.25, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

  def monthlyWm2(name: String, lat: Double, lon: Double): Seq[(Int, Double)] = {
    val f = dir / "pvgis" / s"${name.toLowerCase.replace(" ", "-")}.json"
    pvgisMonthly(f, lat, lon)
    val rs = st.executeQuery(
      s"""SELECT month, avg("H(h)_m") FROM (
            SELECT unnest(outputs.monthly, recursive := true)
            FROM read_json_auto('${f.toString.replace("'", "''")}'))
          GROUP BY month ORDER BY month""")
    val v = collection.mutable.ArrayBuffer[(Int, Double)]()
    while (rs.next()) {
      val m = rs.getInt(1)
      // kWh/m2 over the month -> mean W/m2
      v += ((m, rs.getDouble(2) * 1000.0 / (DAYS(m - 1) * 24.0)))
    }
    v.toSeq
  }

  // --- Figure 6.2: the seasonal swing, London and Edinburgh ---
  val seasonal = Seq(("London", 51.507, -0.128), ("Edinburgh", 55.953, -3.188))
  val f62 = new StringBuilder; f62 ++= "place,month,wm2\n"
  for ((n, la, lo) <- seasonal; (m, w) <- monthlyWm2(n, la, lo)) f62 ++= f"$n,$m,$w%.2f\n"
  os.write.over(dir / "solar-seasonal.csv", f62.toString)

  // --- Figure 6.16: annual mean, a spread of locations ---
  val places = Seq(
    ("Edinburgh", "Europe", 55.953, -3.188), ("Manchester", "Europe", 53.48, -2.24),
    ("London", "Europe", 51.507, -0.128), ("Cambridge", "Europe", 52.205, 0.119),
    ("Berlin", "Europe", 52.52, 13.405), ("Paris", "Europe", 48.857, 2.352),
    ("Munich", "Europe", 48.135, 11.582), ("Madrid", "Europe", 40.417, -3.704),
    ("Rome", "Europe", 41.903, 12.496), ("Athens", "Europe", 37.984, 23.728),
    ("Vancouver", "N. America", 49.283, -123.121), ("Seattle", "N. America", 47.606, -122.33),
    ("Chicago", "N. America", 41.878, -87.63), ("New York", "N. America", 40.713, -74.006),
    ("Los Angeles", "N. America", 34.052, -118.244), ("Phoenix", "N. America", 33.448, -112.074),
    ("Cairo", "Africa", 30.044, 31.236), ("Nairobi", "Africa", -1.286, 36.817),
    ("Johannesburg", "Africa", -26.204, 28.047), ("Ouarzazate", "Africa", 30.92, -6.91))
  val f616 = new StringBuilder; f616 ++= "place,region,wm2\n"
  for ((n, r, la, lo) <- places) {
    val ms = monthlyWm2(n, la, lo)
    val annual = ms.map { case (m, w) => w * DAYS(m - 1) }.sum / DAYS.sum
    f616 ++= f"$n,$r,$annual%.1f\n"
  }
  os.write.over(dir / "solar-locations.csv", f616.toString)

  // --- Figure 6.13: sunniness, sunshine hours as a fraction of daylight ---
  // MacKay used Cambridge. That station (Cambridge NIAB) stopped reporting
  // sunshine in 2010, so Oxford — which has the longest continuous record still
  // running — carries the series to the present alongside it.
  val stations = Seq(("Cambridge", "cambridge", 52.245), ("Oxford", "oxford", 51.761))
  val f613 = new StringBuilder; f613 ++= "station,year,sun_hours,daylight_hours,fraction\n"
  val rowRe = raw"^\s*(\d{4})\s+(\d{1,2})\s+\S+\s+\S+\s+\S+\s+\S+\s+([\d.]+)\*?#?".r
  for ((label, slug, lat) <- stations) {
    val metf = dir / s"$slug-metoffice.txt"
    if (!os.exists(metf))
      os.write.over(metf, requests.get(
        s"https://www.metoffice.gov.uk/pub/data/weather/uk/climate/stationdata/${slug}data.txt",
        readTimeout = 90000).text())
    val dayl = (1 to 12).map { m =>
      val start = (1 until m).map(i => DAYS(i - 1)).sum.toInt
      (1 to DAYS(m - 1).toInt).map(d => daylightHours(lat, start + d)).sum
    }
    val sun = collection.mutable.Map[Int, (Double, Double)]().withDefaultValue((0.0, 0.0))
    for (line <- os.read.lines(metf)) rowRe.findFirstMatchIn(line).foreach { m =>
      val y = m.group(1).toInt; val mo = m.group(2).toInt; val h = m.group(3).toDouble
      val (sh, dh) = sun(y); sun(y) = (sh + h, dh + dayl(mo - 1))
    }
    val complete = sun.filter { case (_, (_, dh)) => dh > dayl.sum - 1 }.toSeq.sortBy(_._1)
    for ((y, (sh, dh)) <- complete) f613 ++= f"$label,$y,$sh%.1f,$dh%.1f,${sh / dh}%.4f\n"
    val recent = complete.takeRight(10).map { case (_, (sh, dh)) => sh / dh }
    println(f"$label%-10s ${complete.size} complete years ${complete.head._1}-${complete.last._1}, "
          + f"last ten mean ${recent.sum / recent.size * 100}%.1f%%")
  }
  os.write.over(dir / "sunniness.csv", f613.toString)
  conn.close()
  println("wrote solar-seasonal.csv, solar-locations.csv, sunniness.csv")
}

// ---- Chapter N: which countries have already peaked ----
// The empirical core of the peak argument. For every producer, the year of
// maximum output and what it produces now as a share of that maximum. Both
// sheets put 1965 in column B; identifiers are quoted because the span
// includes column AS, which collides with the SQL keyword.
@main
def chapterN(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val xlsx = (dir / "ei-stats-review-all-data.xlsx").toString.replace("'", "''")
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement(); st.execute("LOAD excel")
  val out = new StringBuilder; out ++= "fuel,country,peak_year,peak,current,pct_of_peak\n"

  // Aggregates and groupings are excluded: the question is about countries.
  val skip = Set("Total", "of which", "Other", "European Un", "OECD", "Non-OECD",
                 "OPEC", "Non-OPEC", "USSR", "Middle East", "Central America")
  // The two sheets start in different years: oil at 1965, gas at 1970.
  for ((fuel, sheet, y0, floor) <- Seq(("Oil", "Oil Production - barrels", 1965, 200.0),
                                       ("Gas", "Gas Production - Bcm", 1970, 5.0))) {
    val cols = (y0 to 2025).map(y => "\"" + colName(1 + (y - y0)) + "\"").mkString(",")
    val rs = st.executeQuery(
      s"""SELECT A, $cols FROM read_xlsx('$xlsx', sheet='$sheet', header=false,
          all_varchar=true, range='A4:${colName(1 + (2025 - y0))}130') WHERE A IS NOT NULL""")
    var n = 0
    while (rs.next()) {
      val name = rs.getString(1).trim
      if (!skip.exists(name.contains)) {
        val vals = (y0 to 2025).flatMap { y =>
          val s = rs.getString(y - y0 + 2)
          if (s == null || s.isEmpty) None else Some((y, s.toDouble))
        }
        if (vals.size >= 30 && vals.last._2 >= floor) {
          val (py, pv) = vals.maxBy(_._2); val cur = vals.last._2
          out ++= f"$fuel,$name,$py,$pv%.1f,$cur%.1f,${cur / pv * 100}%.1f\n"; n += 1
        }
      }
    }
    println(s"$fuel: $n producers")
  }
  conn.close()
  os.write.over(dir / "peaks-by-country.csv", out.toString)
  println("wrote data-refresh/peaks-by-country.csv")
  println("render (once per fuel): uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/peaks_by_country.py data-refresh/peaks-by-country.csv without-hot-air/Images/fig-peak-oil-by-country.svg Oil")
  println("  python figures/peaks_by_country.py data-refresh/peaks-by-country.csv without-hot-air/Images/fig-peak-gas-by-country.svg Gas")
}

// ---- Chapter 7: the heat-pump break-even ----
// A heat pump beats a gas boiler when its seasonal performance factor exceeds
// the ratio of the electricity price to the gas price. Household prices come
// from Eurostat, band DC for electricity (2500-4999 kWh/yr) and D2 for gas
// (20-199 GJ/yr), including all taxes and levies, which is what a household
// actually pays. Written to JSON and read with DuckDB, as elsewhere here.
@main
def chapter07(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val base = "https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data"
  def grab(ds: String, cons: String, cache: os.Path): Unit =
    if (!os.exists(cache)) os.write.over(cache, requests.get(
      s"$base/$ds?format=JSON&lang=EN&lastTimePeriod=1&currency=EUR&unit=KWH&tax=I_TAX&nrg_cons=$cons",
      readTimeout = 90000).text())
  grab("nrg_pc_204", "KWH2500-4999", dir / "eurostat-elec-price.json")
  grab("nrg_pc_202", "GJ20-199", dir / "eurostat-gas-price.json")

  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  // JSON-stat keys its values by position, and DuckDB reads the index as a
  // struct rather than a map, so both branches are read as JSON and the
  // position is looked up per country.
  def period(f: os.Path): String = {
    val rs = st.executeQuery(
      s"""SELECT unnest(json_keys(dimension->'time'->'category'->'index'))
          FROM read_json('${f.toString.replace("'", "''")}', columns={dimension:'JSON'})""")
    if (rs.next()) rs.getString(1) else "?"
  }

  def read(f: os.Path): Map[String, Double] = {
    val path = f.toString.replace("'", "''")
    val rs = st.executeQuery(
      s"""WITH j AS (SELECT dimension, value FROM
                       read_json('$path', columns={dimension:'JSON', value:'JSON'})),
              g AS (SELECT unnest(json_keys(dimension->'geo'->'category'->'index')) AS geo,
                           dimension, value FROM j)
          SELECT geo, CAST(value->>('$$."' || (dimension->'geo'->'category'->'index'->>geo) || '"')
                       AS DOUBLE) AS price
          FROM g""")
    val m = collection.mutable.Map[String, Double]()
    while (rs.next()) {
      val v = rs.getDouble(2)
      if (!rs.wasNull && v > 0) m(rs.getString(1)) = v
    }
    m.toMap
  }
  // Eurostat publishes the two datasets on different schedules, and each is
  // fetched with lastTimePeriod=1, so they can drift apart. Dividing a price
  // from one half-year by a price from another would be silently wrong.
  val (pe, pg) = (period(dir / "eurostat-elec-price.json"), period(dir / "eurostat-gas-price.json"))
  require(pe == pg, s"Eurostat periods differ: electricity $pe, gas $pg - refetch both")
  println(s"Eurostat household prices, period $pe")
  val elec = read(dir / "eurostat-elec-price.json")
  val gas = read(dir / "eurostat-gas-price.json")
  val out = new StringBuilder; out ++= "country,elec_eur_kwh,gas_eur_kwh,ratio\n"
  // EA and EU27 are aggregates of members plotted individually; excluding them
  // keeps the count in the figure a count of countries.
  val aggregates = Set("EA", "EU27_2020", "EU28", "EU27")
  val keep = (elec.keySet intersect gas.keySet) -- aggregates
  for (c <- keep.toSeq.sorted if gas(c) > 0) {
    val r = elec(c) / gas(c)
    out ++= f"$c,${elec(c)}%.4f,${gas(c)}%.4f,$r%.2f\n"
  }
  // Eurostat has no post-Brexit UK gas price, so the UK row is the Ofgem
  // default-tariff cap for July-September 2026: 26.11p electricity and 7.33p
  // gas per kWh including 5% VAT, a ratio of 3.56. On the April-June cap
  // (24.67p and 5.74p) the ratio is 4.30, so the UK sits above break-even on
  // either. Countries with little or no gas distribution - Finland, Norway,
  // Poland among them - have no Eurostat gas price and cannot appear at all.
  out ++= "UK,0.3013,0.0846,3.56\n"
  conn.close()
  os.write.over(dir / "heatpump-breakeven.csv", out.toString)
  println(s"wrote data-refresh/heatpump-breakeven.csv (${keep.size + 1} countries incl. UK)")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/heatpump_breakeven.py data-refresh/heatpump-breakeven.csv without-hot-air/Images/fig-heatpump-breakeven.svg")
}

// ---- Figure 7.8: Cambridge daily temperature, MacKay's 2006 against now ----
// Open-Meteo's ERA5 reanalysis archive: free, no key, and consistent between
// the two years, which matters more here than station-exact values.
@main
def chapter07Temp(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val conn = java.sql.DriverManager.getConnection("jdbc:duckdb:")
  val st = conn.createStatement()
  val out = new StringBuilder; out ++= "year,day,tmean,tmax,tmin\n"
  for (y <- Seq(2006, 2025)) {
    val f = dir / s"cambridge-temp-$y.json"
    if (!os.exists(f)) os.write.over(f, requests.get(
      "https://archive-api.open-meteo.com/v1/archive?latitude=52.205&longitude=0.119" +
      s"&start_date=$y-01-01&end_date=$y-12-31" +
      "&daily=temperature_2m_mean,temperature_2m_max,temperature_2m_min&timezone=Europe%2FLondon",
      readTimeout = 90000).text())
    val rs = st.executeQuery(
      s"""SELECT generate_subscripts(daily.temperature_2m_mean, 1) AS d,
                 unnest(daily.temperature_2m_mean) AS tmean,
                 unnest(daily.temperature_2m_max)  AS tmax,
                 unnest(daily.temperature_2m_min)  AS tmin
          FROM read_json_auto('${f.toString.replace("'", "''")}')""")
    var n = 0; var sum = 0.0
    while (rs.next()) {
      out ++= f"$y,${rs.getInt(1)},${rs.getDouble(2)}%.2f,${rs.getDouble(3)}%.2f,${rs.getDouble(4)}%.2f\n"
      sum += rs.getDouble(2); n += 1
    }
    println(f"$y: $n days, annual mean ${sum / n}%.2f C")
  }
  conn.close()
  os.write.over(dir / "cambridge-temperature.csv", out.toString)
  println("wrote data-refresh/cambridge-temperature.csv")
}

// ---- LCOE against realised capture price, Great Britain ----
// The cost of building against what the output actually earned. LCOE is DESNZ
// Electricity Generation Costs 2025, central capex, 2024 prices, projects
// commissioning 2035 (hand-entered: the report publishes these as charts, and
// only the capex-sensitivity tables carry the numbers). Capture prices come
// from the gbCapture step over Elexon settlement data for 2025.
@main
def lcoeVsCapture(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  val cap = os.read.lines(dir / "gb-capture.csv").drop(1).map(_.split(",")).
    map(a => a(0) -> a(1).toDouble).toMap
  // (label, DESNZ technology, low, central, high, which capture series applies)
  val rows = Seq(
    ("Large-scale solar", 36.0, 44.0, 50.0, "Solar"),
    ("Onshore wind",      33.0, 41.0, 55.0, "Wind"),
    ("Offshore wind (fixed)", 50.0, 59.0, 72.0, "Wind"),
    ("Offshore wind (floating)", 65.0, 91.0, 121.0, "Wind"),
    ("Gas CCGT",          38.0, 45.0, 53.0, "Gas"),
    ("Gas with CCUS",     85.0, 101.0, 117.0, "Gas"))
  val out = new StringBuilder
  out ++= "technology,lcoe_low,lcoe_central,lcoe_high,capture,capture_source,margin\n"
  for ((label, lo, mid, hi, src) <- rows) {
    val c = cap(src)
    out ++= f"$label,$lo%.0f,$mid%.0f,$hi%.0f,$c%.1f,$src,${c - mid}%.1f\n"
  }
  os.write.over(dir / "lcoe-vs-capture.csv", out.toString)
  println("wrote data-refresh/lcoe-vs-capture.csv")
  for ((label, _, mid, _, src) <- rows)
    println(f"  $label%-24s LCOE $mid%3.0f  capture ${cap(src)}%5.1f  ${if (cap(src) > mid) "clears" else "SHORT"}")
  println("render: uv run --with seaborn --with pandas --with matplotlib --python 3.12 \\")
  println("  python figures/lcoe_vs_capture.py data-refresh/lcoe-vs-capture.csv without-hot-air/Images/fig-lcoe-vs-capture.svg")
}

// ---- The stacks: MacKay's 2008 balance sheet against the 2026 revision ----
// Consumption items are the per-person figures each chapter arrives at.
// Production shows MacKay's maximum-conceivable ceilings against what Britain
// actually generated in 2025 (Energy Institute, divided by 68.4 million).
@main
def stacks(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val out = new StringBuilder; out ++= "stack,column,item,kwh_per_day\n"
  def add(stack: String, col: String, rows: Seq[(String, Double)]) =
    for ((item, v) <- rows if v > 0) out ++= f"$stack,$col,$item,$v%.3f\n"

  val red2008 = Seq("Stuff" -> 48.0, "Cars" -> 40.0, "Planes" -> 30.0,
    "Heating and cooling" -> 37.0, "Transporting stuff" -> 12.0, "Food and farming" -> 12.0,
    "Gadgets" -> 5.0, "Light" -> 4.0, "Defence" -> 4.0, "Universities" -> 0.24)
  val red2026 = Seq("Stuff" -> 48.0, "Cars" -> 40.0, "Planes" -> 30.0,
    "Heating and cooling" -> 13.0, "Transporting stuff" -> 12.0, "Food and farming" -> 7.0,
    "Gadgets" -> 5.0, "Light" -> 0.8, "Defence" -> 4.0, "Universities" -> 0.24,
    "Data centres" -> 0.5, "NHS estate" -> 0.44)
  add("Consumption", "MacKay 2008", red2008)
  add("Consumption", "2026 revision", red2026)

  val greenMax = Seq("Solar" -> 50.0, "Offshore wind" -> 48.0, "Onshore wind" -> 20.0,
    "Tide" -> 11.0, "Wave" -> 4.0, "Geothermal" -> 2.0, "Hydro" -> 1.5)
  // 2025 generation: wind 87.1 TWh, solar 20.0, hydro 5.1, over 68.4 million people.
  val pop = 68.4e6
  def perDay(twh: Double) = twh * 1e9 / pop / 365.0
  val greenNow = Seq("Onshore wind" -> perDay(87.1), "Solar" -> perDay(20.0),
    "Hydro" -> perDay(5.1), "Tide" -> 0.004, "Wave" -> 0.0, "Geothermal" -> 0.0)
  add("Production", "MacKay's ceiling", greenMax)
  add("Production", "Britain 2025", greenNow)

  os.write.over(dir / "stacks.csv", out.toString)
  println("wrote data-refresh/stacks.csv")
  for (c <- Seq("MacKay 2008", "2026 revision", "MacKay's ceiling", "Britain 2025")) {
    val tot = out.toString.linesIterator.drop(1).filter(_.split(",")(1) == c)
      .map(_.split(",")(3).toDouble).sum
    println(f"  $c%-18s $tot%6.1f kWh/d")
  }
}

// ---- Cartoon Britain, 2008 and 2026 ----
// MacKay's chapter 19 simplification: heating, transport, electricity. The 2026
// column is derived from the Statistical Review — gas and oil consumption less
// the gas burned in power stations, and electricity generation as delivered.
@main
def cartoonBritain(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  val pop = 68.4e6; val EJ = 1e18 / 3.6e6
  def perDay(kwh: Double) = kwh / pop / 365.0
  val gasAll = perDay(2.20 * EJ)      // all UK gas
  val oilAll = perDay(2.71 * EJ)      // all UK oil
  val elecTWh = 91.0 + 36 + 86 + 19 + 6 + 41
  val elec = perDay(elecTWh * 1e9)
  val gasToPower = perDay(91.0 / 0.50 * 1e9)   // CCGT at ~50%
  val out = new StringBuilder; out ++= "year,category,kwh_per_day,note\n"
  out ++= "2008,Heating,40,delivered\n2008,Transport,40,delivered\n"
  out ++= "2008,Electricity,18,delivered\n2008,Electricity fossil input,45,input\n"
  out ++= f"2026,Heating,${gasAll - gasToPower}%.1f,delivered\n"
  out ++= f"2026,Transport,$oilAll%.1f,delivered\n"
  out ++= f"2026,Electricity,$elec%.1f,delivered\n"
  out ++= f"2026,Electricity fossil input,$gasToPower%.1f,input\n"
  os.write.over(dir / "cartoon-britain.csv", out.toString)
  println("wrote data-refresh/cartoon-britain.csv")
  println(f"  heating (gas less power stations)  ${gasAll - gasToPower}%.1f kWh/d")
  println(f"  transport (all oil)                  $oilAll%.1f kWh/d")
  println(f"  electricity delivered                $elec%.1f kWh(e)/d")
  println(f"  fossil input to electricity          $gasToPower%.1f kWh/d  (MacKay: 45)")
}

// ---- Figure 20.23 remade: passenger transport energy against speed ----
// MacKay's summary diagram, with the 2008 points taken from his own text and
// the 2026 points added. Water is the corner that moved: hydrofoiling puts a
// 25-knot passenger vessel in the same band as the London Underground, which
// in 2008 was somewhere only trains and coaches reached.
@main
def transportEnergy(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val NM = 1.852 // km per nautical mile

  // Candela's own published battery and range figures, divided out. Using the
  // quoted range as if it were achievable makes these consumption figures
  // conservative: the real range is at most that, so the real kWh/km is at least this.
  val p12 = 336.0 / (40 * NM)          // 336 kWh usable, up to 40 nm at 25 kn
  val c8 = 69.0 / (57 * NM)            // 69 kWh, 57 nm at 22 kn
  val p12Seat = p12 / 30 * 100
  // The diesel vessels on the same Stockholm route, backed out of Candela's own
  // savings claim. The claim itself is quoted at 66%, 80% and 84% by different
  // sources, so this is a band rather than a number.
  val (dieselLo, dieselHi) = (p12Seat / (1 - 0.66), p12Seat / (1 - 0.84))

  // No commas in mode labels: this is a bare CSV and DuckDB sniffs the delimiter.
  val out = new StringBuilder
  out ++= "era,category,mode,speed_kmh,kwh,kwh_lo,kwh_hi,fill\n"
  def add(era: String, cat: String, mode: String, kmh: Double, kwh: Double,
          fill: String, lo: Double = 0, hi: Double = 0) =
    out ++= f"$era,$cat,$mode,$kmh%.1f,$kwh%.2f,$lo%.2f,$hi%.2f,$fill\n"

  // 2008: every value is stated in chapter 20 or chapter 5 of the original book.
  add("2008", "land", "Bicycle", 20, 1.6, "best")
  add("2008", "land", "Full 8-car train", 161, 1.6, "best")
  add("2008", "land", "Coach (full)", 105, 6.0, "best")
  add("2008", "land", "Croydon tram", 25, 9.0, "typical")
  add("2008", "land", "Underground", 33, 15.0, "typical")
  add("2008", "land", "Electric car (Roadster)", 50, 15.0, "best")
  add("2008", "land", "G-Wiz (real use)", 30, 21.0, "typical")
  add("2008", "land", "London bus", 13, 32.0, "typical")
  add("2008", "land", "Car (1 occupant)", 50, 80.0, "typical")
  add("2008", "land", "Honda FCX (hydrogen)", 50, 69.0, "best")
  add("2008", "land", "BMW Hydrogen 7", 50, 254.0, "best")
  add("2008", "air", "747 (full)", 900, 42.0, "best")
  add("2008", "water", "Liner (Rijndam)", 30.5, 121.0, "typical")

  // 2026.
  add("2026", "land", "E-bike", 25, 0.6, "best")
  add("2026", "land", "Efficient EV (Model 3)", 50, 14.7, "best")
  add("2026", "land", "EV (real-world average)", 50, 21.0, "typical")
  add("2026", "air", "787/A350 (full)", 900, 32.0, "best")
  add("2026", "water", "Candela P-12 (30 seats)", 46.3, p12Seat, "best")
  add("2026", "water", "Candela C-8 (6 aboard)", 40.7, c8 / 6 * 100, "best")
  add("2026", "water", "Candela C-8 (2 aboard)", 40.7, c8 / 2 * 100, "typical")
  add("2026", "water", "Diesel ferry it replaced", 30.0,
      math.sqrt(dieselLo * dieselHi), "typical", dieselLo, dieselHi)

  os.write.over(dir / "transport-energy.csv", out.toString)
  println("wrote data-refresh/transport-energy.csv")
  println(f"  Candela P-12   $p12%.2f kWh/km  ->  $p12Seat%.1f kWh/100 p-km at 30 seats")
  println(f"  Candela C-8    $c8%.3f kWh/km  ->  ${c8 / 6 * 100}%.1f (6 aboard), ${c8 / 2 * 100}%.1f (2 aboard)")
  println(f"  diesel ferry backed out of the savings claim: $dieselLo%.0f-$dieselHi%.0f kWh/100 p-km")
}

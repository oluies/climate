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

import java.sql.{Connection, DriverManager}
import scala.collection.mutable.ArrayBuffer
import scala.util.Using

/** Open a DuckDB connection, hand it to `body`, and close it whatever happens.
  *
  * Every step here opens a connection, and a query error or a validation failure
  * part-way through would otherwise leak it - the steps run in one JVM under
  * `mill Refresh.scala`, so a leak survives the failing step. Using this instead
  * of a bare close() means the release is stated once rather than at each call
  * site, where it was previously easy to omit and was in fact omitted sixteen
  * times. */
// Using.resource already has the semantics wanted here: the body's exception
// wins, a close-time failure is attached to it as suppressed rather than
// replacing it, and a close failure propagates if the body succeeded.
def withConn[T](body: Connection => T): T =
  Using.resource(DriverManager.getConnection("jdbc:duckdb:"))(body)

/** Our World in Data-grapher, cachad pa disk. Fyra steg hamtade tidigare samma
  * sak med var sin kopia av URL-mallen och timeouten. */
def fetch(slug: String, file: String): os.Path = {
  val dir = os.pwd / "data-refresh"
  os.makeDir.all(dir)
  val p = dir / file
  if (!os.exists(p)) os.write.over(p,
    requests.get(s"https://ourworldindata.org/grapher/$slug.csv?csvType=full", readTimeout = 60000).text())
  p
}


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
  withConn { conn =>
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
  }
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
  val modCsv = fetch("solar-pv-prices", "owid-solar-module.csv")
  val lcoeCsv = fetch("levelized-cost-of-energy", "owid-lcoe.csv")

  withConn { conn =>
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
    println("render the figure:")
    println("  uv run figures/cost_decline.py data-refresh/cost-decline.csv without-hot-air/Images/fig-cost-decline.svg")
  }
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
  withConn { conn =>
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
    os.write.over(dir / "uk-primary-energy.csv", out.toString)
    println(s"wrote data-refresh/uk-primary-energy.csv (1965-$last)")
    println("render:")
    println("  uv run figures/uk_primary_energy.py data-refresh/uk-primary-energy.csv without-hot-air/Images/fig-uk-primary-energy.svg")
  }
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
  withConn { conn =>
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
    os.write.over(dir / "uk-electricity-percapita.csv", out.toString)
    println(s"wrote data-refresh/uk-electricity-percapita.csv (1985-$last)")
    println("render:")
    println("  uv run figures/uk_electricity_percapita.py data-refresh/uk-electricity-percapita.csv without-hot-air/Images/fig-uk-electricity-percapita.svg")
  }
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
  println("render:")
  println("  uv run figures/gb_demand_week.py data-refresh/gb-demand-week.csv without-hot-air/Images/fig-gb-demand-week.svg")
}

// ---- Chapter J: world energy, from the EI Statistical Review workbook ----
// The Energy Institute's 2026 edition (published 30 June 2026, data for 2025).
// Run once a year, after the new edition appears at the end of June: the
// workbook is the only source here that moves, and everything this task writes
// moves with it.
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
  withConn { conn =>
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

    // -- the same series per person, in MacKay's kWh/d --
    // Population year by year from OWID's long-run series, which carries the UN
    // projection and so reaches 2025 without the extrapolation the plain
    // population grapher would need. Beware the region names: the Statistical
    // Review's "Europe" is not the UN's. It puts Turkey and Georgia in Europe and
    // Russia, Belarus and Moldova in the CIS, so OWID's Europe is reshaped to
    // match; left uncorrected the denominator is a tenth too large and Europe's
    // line sits a tenth too low. The other five regions coincide.
    val popCsv = fetch("population-long-run-with-projections", "owid-population-longrun.csv")
    val popNeeded = List("World", "China", "United States", "India", "Africa", "Europe",
                         "Russia", "Belarus", "Moldova", "Turkey", "Georgia")
    val elecYears = elecSeries.values.flatMap(_.keys)
    val (elecFirst, elecLast) = (elecYears.min, elecYears.max)
    val rs7 = st.executeQuery(
      s"""SELECT "Entity", TRY_CAST("Year" AS INTEGER) AS y,
             COALESCE(TRY_CAST("Population (projections) (Projected)" AS DOUBLE),
                      TRY_CAST("Population" AS DOUBLE)) AS v
          FROM read_csv_auto('${popCsv.toString.replace("'", "''")}', all_varchar=true)
          WHERE "Entity" IN (${popNeeded.map(e => s"'$e'").mkString(", ")})
            AND TRY_CAST("Year" AS INTEGER) BETWEEN $elecFirst AND $elecLast""")
    val popByEntity = collection.mutable.Map[String, collection.mutable.Map[Int, Double]]()
    while (rs7.next())
      popByEntity.getOrElseUpdate(rs7.getString(1), collection.mutable.Map())(rs7.getInt(2)) = rs7.getDouble(3)
    for (e <- popNeeded if !popByEntity.contains(e))
      sys.error(s"FEL: befolkning saknas for $e i owid-population-longrun.csv")
    def pop(entity: String, y: Int): Double = popByEntity(entity).getOrElse(y,
      sys.error(s"FEL: befolkning saknas for $entity $y i owid-population-longrun.csv"))
    def regionPop(region: String, y: Int): Double = region match {
      case "US"     => pop("United States", y)
      case "Europe" => pop("Europe", y) - pop("Russia", y) - pop("Belarus", y) - pop("Moldova", y) +
                       pop("Turkey", y) + pop("Georgia", y)
      case r        => pop(r, y)
    }
    // No year is skipped: a generation year with no population behind it fails
    // loudly in `pop` rather than shortening the series behind the reader's back.
    // That is the failure to expect next June, when the workbook gains a year and
    // the cached owid-population-longrun.csv has not - `fetch` never re-downloads
    // a file it already has, so delete it before the annual run.
    val epc = new StringBuilder; epc ++= "region,year,kwh_per_day\n"
    val elecPc = scala.collection.mutable.Map[String, Map[Int, Double]]()
    for (r <- elecRegions.map(_.replace("Total ", ""))) {
      val m = elecSeries(r).map { case (y, twh) => y -> twh * 1e9 / regionPop(r, y) / 365.0 }
      elecPc(r) = m
      for ((y, v) <- m.toSeq.sorted) epc ++= f"$r,$y,$v%.2f\n"
    }
    os.write.over(dir / "world-electricity-percapita.csv", epc.toString)

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
    val last = elecPc("World").keys.max
    val (usPeakY, usPeakV) = elecPc("US").maxBy(_._2)
    val afrNow = elecPc("Africa")(last)
    val cnCross = elecPc("China").keys.toSeq.sorted.find(y => elecPc("China")(y) >= elecPc("Europe")(y))
    val cnWhen = elecPc("China").keys.toSeq.sorted.find(y => elecPc("China")(y) >= afrNow)
    println(f"per person  US peaked $usPeakY at $usPeakV%.1f kWh/d, now ${elecPc("US")(last)}%.1f; " +
            f"China ${elecPc("China")(1985)}%.1f (1985) -> ${elecPc("China")(last)}%.1f, passed Europe in ${cnCross.getOrElse(0)}")
    println(f"            Africa ${elecPc("Africa")(1985)}%.1f -> $afrNow%.1f in forty years - where China was in ${cnWhen.getOrElse(0)}; " +
            f"India ${elecPc("India")(last)}%.1f, world ${elecPc("World")(last)}%.1f")
    println("render:")
    println("  uv run figures/world_energy_2025.py data-refresh/world-energy-2025.csv without-hot-air/Images/fig-world-energy-2025.svg")
    println("  uv run figures/world_electricity_2025.py data-refresh/world-electricity-2025.csv without-hot-air/Images/fig-world-electricity-2025.svg")
    println("  uv run figures/world_percapita.py data-refresh/world-tes-percapita.csv without-hot-air/Images/fig-world-percapita.svg")
    println("  uv run figures/world_electricity_history.py data-refresh/world-electricity-history.csv without-hot-air/Images/fig-world-electricity-history.svg")
    println("  uv run figures/world_electricity_percapita.py data-refresh/world-electricity-percapita.csv without-hot-air/Images/fig-world-electricity-percapita.svg")
  }
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
  withConn { conn =>
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
    os.write.over(dir / "ev-share.csv", out.toString)
    for (c <- want; (y, v) <- latest.get(c)) println(f"$c%-16s $y: $v%5.1f%% of new cars")
    println("render:")
    println("  uv run figures/ev_share.py data-refresh/ev-share.csv without-hot-air/Images/fig-ev-share.svg")
  }
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

  withConn { conn =>
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
    println("render:")
    println("  uv run figures/states_and_europe.py data-refresh/states-and-europe.csv without-hot-air/Images/fig-states-and-europe.svg")
  }
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
  withConn { conn =>
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
    os.write.over(dir / "de-net-trade.csv", out.toString)
    println(s"wrote data-refresh/de-net-trade.csv (1990-$last)")
    println(f"largest export surplus: $minY ${-minV}%.1f TWh  |  turned net importer: $flip")
    println("render:")
    println("  uv run figures/de_net_trade.py data-refresh/de-net-trade.csv without-hot-air/Images/fig-de-net-trade.svg")
  }
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
  val cache = os.pwd / "data-refresh" / "api-cache"

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

  withConn { conn =>
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
  withConn { conn =>
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
    println("wrote data-refresh/solar-capacity.csv, solar-percapita.csv, solar-power-density.csv")
    println("render:")
    println("  uv run figures/solar_capacity.py data-refresh/solar-capacity.csv without-hot-air/Images/fig-solar-capacity.svg")
  }
}

// ---- Chapter 1: figure 1.2, the North Sea carried forward ----
// MacKay's figure 1.2 ends in 2007. This rebuilds the same two series to the
// present from the Statistical Review: production for the three North Sea
// producers, and the crude price in constant dollars.
//
// Note on the workbook: the production sheet carries three columns labelled
// 2025 - the level, then a growth rate and a share. Reading them positionally
// from B, as `series` does, takes the levels and stops at the right column; a
// name-based read that keeps the last match picks up a percentage instead and
// reports the North Sea as having stopped producing.
@main
def chapter01(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  // Figur 1.12 ritas ur dessa tva. De hamtas ocksa av andra steg, men bada ar
  // gitignorerade, sa utan detta gar render-raden nedan inte att folja pa en
  // ren utcheckning.
  fetch("per-capita-ghg-emissions", "owid-per-capita-ghg-emissions.csv")
  fetch("population", "owid-population.csv")
  val xlsx = (dir / "ei-stats-review-all-data.xlsx").toString.replace("'", "''")
  withConn { conn =>
    val st = conn.createStatement()
    st.execute("LOAD excel")

    def row(sheet: String, region: String, firstYear: Int, lastYear: Int) = {
      val cols = (firstYear to lastYear).map(y => "\"" + colName(1 + (y - firstYear)) + "\"").mkString(",")
      val rs = st.executeQuery(
        s"""SELECT $cols FROM read_xlsx('$xlsx', sheet='$sheet', header=false,
            all_varchar=true, range='A4:${colName(1 + (lastYear - firstYear))}200')
            WHERE A='$region'""")
      val m = collection.mutable.Map[Int, Double]()
      if (rs.next()) for (i <- firstYear to lastYear) {
        val v = rs.getString(i - firstYear + 1)
        if (v != null && v.nonEmpty) m(i) = v.toDouble
      }
      m.toMap
    }

    val (y0, y1) = (1965, 2025)
    val uk = row("Oil Production - barrels", "United Kingdom", y0, y1)
    val no = row("Oil Production - barrels", "Norway", y0, y1)
    val dk = row("Oil Production - barrels", "Denmark", y0, y1)

    // Priser: aret i kolumn A, konstanta dollar i kolumn C.
    val price = collection.mutable.Map[Int, Double]()
    val pr = st.executeQuery(
      s"""SELECT A, C FROM read_xlsx('$xlsx', sheet='Oil crude prices since 1861',
          header=false, all_varchar=true, range='A5:C300')""")
    while (pr.next()) {
      val y = pr.getString(1); val v = pr.getString(2)
      if (y != null && v != null && y.forall(_.isDigit) && v.nonEmpty)
        price(y.toInt) = v.toDouble
    }

    val sb = new StringBuilder
    sb ++= "year,uk_kbd,no_kbd,dk_kbd,total_kbd,price_2025usd\n"
    for (y <- y0 to y1 if uk.contains(y) && no.contains(y)) {
      val u = uk.getOrElse(y, 0.0); val n = no.getOrElse(y, 0.0); val d = dk.getOrElse(y, 0.0)
      val p = price.get(y).map(v => f"$v%.2f").getOrElse("")
      sb ++= f"$y,$u%.1f,$n%.1f,$d%.1f,${u + n + d}%.1f,$p\n"
    }
    os.write.over(dir / "north-sea-oil.csv", sb.toString)

    // Figur 1.7: kolet, samma tva serier som MacKays egen version men hela vagen till nu.
    // Statistical Review borjar 1981, vilket inte racker for hans fonster, sa
    // langa serien kommer fran OWID (Our World in Data) i TWh.
    val coalCsv = dir / "owid-coal-production.csv"
    if (!os.exists(coalCsv))
      os.write.over(coalCsv, requests.get(
        "https://ourworldindata.org/grapher/coal-production-by-country.csv?csvType=full",
        readTimeout = 60000).text())
    val coal = collection.mutable.Map[Int, (Double, Double)]()
    val cr = st.executeQuery(
      s"""SELECT "Year", "Entity", "Coal" FROM read_csv_auto('${coalCsv.toString.replace("'", "''")}')
          WHERE "Entity" IN ('United Kingdom','World') AND "Coal" IS NOT NULL""")
    while (cr.next()) {
      val y = cr.getInt(1); val e = cr.getString(2); val v = cr.getDouble(3)
      val (u, w) = coal.getOrElse(y, (0.0, 0.0))
      coal(y) = if (e == "United Kingdom") (v, w) else (u, v)
    }
    // Behall ar dar NAGON av serierna finns: varldsserien borjar 1800 och den
    // brittiska 1700, och kravet att bada finns kastade bort 1700-talet - just
    // den period MacKays figur 1.5 handlar om. Saknat varde skrivs tomt, inte
    // noll, sa kurvan bryts i stallet for att falla till golvet.
    val cb = new StringBuilder; cb ++= "year,uk_twh,world_twh\n"
    for (y <- coal.keys.toSeq.sorted if coal(y)._1 > 0 || coal(y)._2 > 0) {
      val (u, w) = coal(y)
      val us = if (u > 0) f"$u%.1f" else ""
      val ws = if (w > 0) f"$w%.1f" else ""
      cb ++= s"$y,$us,$ws\n"
    }
    os.write.over(dir / "coal-long-run.csv", cb.toString)

    // Figur 1.4: koldioxidhalten. Iskarnor plus Mauna Loa i en serie.
    val co2Csv = dir / "owid-co2-concentration.csv"
    if (!os.exists(co2Csv))
      os.write.over(co2Csv, requests.get(
        "https://ourworldindata.org/grapher/co2-long-term-concentration.csv?csvType=full",
        readTimeout = 60000).text())
    val cc = new StringBuilder; cc ++= "year,ppm\n"
    val cr2 = st.executeQuery(
      s"""SELECT "Year", "Annual average" FROM read_csv_auto('${co2Csv.toString.replace("'", "''")}')
          WHERE "Entity" = 'World' AND "Year" >= 800 AND "Annual average" IS NOT NULL
          ORDER BY "Year"""")
    while (cr2.next()) cc ++= f"${cr2.getInt(1)},${cr2.getDouble(2)}%.2f\n"
    os.write.over(dir / "co2-concentration.csv", cc.toString)

    // Figur 1.16: varldens CO2 per person mot de banor MacKays egna scenarier kraver.
    val pcCsv = dir / "owid-co2-per-capita.csv"
    if (!os.exists(pcCsv))
      os.write.over(pcCsv, requests.get(
        "https://ourworldindata.org/grapher/co-emissions-per-capita.csv?csvType=full",
        readTimeout = 60000).text())
    val pc = new StringBuilder; pc ++= "year,t_per_person\n"
    val pr3 = st.executeQuery(
      s"""SELECT "Year", "CO₂ emissions per capita" FROM read_csv_auto('${pcCsv.toString.replace("'", "''")}')
          WHERE "Entity" = 'World' AND "Year" >= 1950 ORDER BY "Year"""")
    while (pr3.next()) pc ++= f"${pr3.getInt(1)},${pr3.getDouble(2)}%.3f\n"
    os.write.over(dir / "co2-per-capita-world.csv", pc.toString)

    // Figur 1.17: varldens vaxthusgaser per sektor.
    val secCsv = dir / "owid-ghg-by-sector.csv"
    if (!os.exists(secCsv))
      os.write.over(secCsv, requests.get(
        "https://ourworldindata.org/grapher/ghg-emissions-by-sector.csv?csvType=full",
        readTimeout = 60000).text())
    os.write.over(dir / "ghg-by-sector.csv",
      os.read(secCsv).linesIterator.filter(l => l.startsWith("Entity") || l.startsWith("World,")).mkString("\n") + "\n")

    // Figur 1.6: befolkning, samma fonster som figur 1.7 sa formerna gar att
    // lagga bredvid varandra. Langa serien med FN:s framskrivning, sa att den
    // nar samma slutar som ovriga.
    val popCsv = dir / "owid-population-longrun.csv"
    if (!os.exists(popCsv))
      os.write.over(popCsv, requests.get(
        "https://ourworldindata.org/grapher/population-long-run-with-projections.csv?csvType=full",
        readTimeout = 60000).text())
    val popq = collection.mutable.Map[Int, (Double, Double)]()
    val pq = st.executeQuery(
      s"""SELECT "Year", "Entity",
             COALESCE(TRY_CAST("Population (projections) (Projected)" AS DOUBLE), TRY_CAST("Population" AS DOUBLE)) AS v
          FROM read_csv_auto('${popCsv.toString.replace("'", "''")}', all_varchar=true)
          WHERE "Entity" IN ('United Kingdom','World') AND TRY_CAST("Year" AS INTEGER) BETWEEN 1700 AND 2025""")
    while (pq.next()) {
      val y = pq.getInt(1); val e = pq.getString(2); val v = pq.getDouble(3)
      val (u, w) = popq.getOrElse(y, (0.0, 0.0))
      popq(y) = if (e == "United Kingdom") (v, w) else (u, v)
    }
    val pb = new StringBuilder; pb ++= "year,uk,world\n"
    for (y <- popq.keys.toSeq.sorted if popq(y)._1 > 0 && popq(y)._2 > 0)
      pb ++= f"$y,${popq(y)._1}%.0f,${popq(y)._2}%.0f\n"
    os.write.over(dir / "population-longrun.csv", pb.toString)

    println("wrote data-refresh/north-sea-oil.csv, coal-long-run.csv, co2-concentration.csv, population-longrun.csv, " +
            "co2-per-capita-world.csv, ghg-by-sector.csv")
    println("render:")
    println("  uv run figures/north_sea_oil.py data-refresh/north-sea-oil.csv without-hot-air/Images/fig-north-sea-oil.svg")
    println("  uv run figures/coal_long_run.py data-refresh/coal-long-run.csv without-hot-air/Images/fig-coal-long-run.svg")
    println("  uv run figures/coal_early.py data-refresh/coal-long-run.csv without-hot-air/Images/fig-coal-early.svg")
    println("  uv run figures/co2_concentration.py data-refresh/co2-concentration.csv without-hot-air/Images/fig-co2-concentration.svg")
    println("  uv run figures/uk_gap.py data-refresh/uk-electricity-mix.csv without-hot-air/Images/fig-uk-gap.svg")
    println("  uv run figures/emission_paths.py data-refresh/co2-per-capita-world.csv without-hot-air/Images/fig-emission-paths.svg")
    println("  uv run figures/ghg_sectors.py data-refresh/ghg-by-sector.csv without-hot-air/Images/fig-ghg-sectors.svg")
    println("  uv run figures/population_longrun.py data-refresh/population-longrun.csv without-hot-air/Images/fig-population.svg")
    println("  uv run figures/ghg_rectangles.py data-refresh/owid-per-capita-ghg-emissions.csv " +
            "data-refresh/owid-population.csv without-hot-air/Images/fig-ghg-rectangles.svg")
  }
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
  withConn { conn =>
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
    println("wrote solar-seasonal.csv, solar-locations.csv, sunniness.csv")
  }
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
  withConn { conn =>
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
    os.write.over(dir / "peaks-by-country.csv", out.toString)
    println("wrote data-refresh/peaks-by-country.csv")
    println("render (once per fuel):")
    println("  uv run figures/peaks_by_country.py data-refresh/peaks-by-country.csv without-hot-air/Images/fig-peak-oil-by-country.svg Oil")
    println("  uv run figures/peaks_by_country.py data-refresh/peaks-by-country.csv without-hot-air/Images/fig-peak-gas-by-country.svg Gas")
  }
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

  withConn { conn =>
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
    os.write.over(dir / "heatpump-breakeven.csv", out.toString)
    println(s"wrote data-refresh/heatpump-breakeven.csv (${keep.size + 1} countries incl. UK)")
    println("render:")
    println("  uv run figures/heatpump_breakeven.py data-refresh/heatpump-breakeven.csv without-hot-air/Images/fig-heatpump-breakeven.svg")
  }
}

// ---- Figure 7.8: Cambridge daily temperature, MacKay's 2006 against now ----
// Open-Meteo's ERA5 reanalysis archive: free, no key, and consistent between
// the two years, which matters more here than station-exact values.
@main
def chapter07Temp(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  withConn { conn =>
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
    os.write.over(dir / "cambridge-temperature.csv", out.toString)
    println("wrote data-refresh/cambridge-temperature.csv")
  }
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
  println("render:")
  println("  uv run figures/lcoe_vs_capture.py data-refresh/lcoe-vs-capture.csv without-hot-air/Images/fig-lcoe-vs-capture.svg")
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
  // Mirrors data-refresh/uk-electricity-mix.csv term for term (OWID/Ember basis):
  // gas 91, nuclear 36, wind 86, solar 19, hydro 6, bioenergy 41. That table also
  // backs fig-uk-electricity-mix and the prose in chapters 19 and 28a, so this step
  // must not be moved onto the Statistical Review figures used by `stacks` without
  // moving those artefacts too.
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
  val p12Kmh = 25 * NM // 25-knot service speed, used for the P-12 and for the ferry it replaced
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
  add("2008", "land", "London bus", 18, 32.0, "typical")
  add("2008", "land", "Car (1 occupant)", 50, 80.0, "typical")
  add("2008", "land", "Honda FCX (hydrogen)", 50, 69.0, "best")
  add("2008", "land", "BMW Hydrogen 7", 50, 254.0, "best")
  add("2008", "air", "747 (full)", 900, 42.0, "best")
  add("2008", "water", "Liner (Rijndam)", 30.5, 121.0, "typical")

  // 2026.
  add("2026", "land", "E-bike", 25, 0.6, "best")
  // The best car on sale and an ordinary family one, both on the WLTP cycle and
  // both therefore optimistic in the way the chapter sets out. The Mercedes is
  // the most efficient of the recent Car of the Year winners in figure 20.22b;
  // the ID.7 is given as the range Volkswagen itself publishes across variants,
  // with the point at the geometric mean of the two ends.
  val id7Lo = 14.1; val id7Hi = 16.3
  add("2026", "land", "Most efficient EV (Mercedes CLA)", 50, 12.2, "best")
  add("2026", "land", "Family EV (VW ID.7)", 50, math.sqrt(id7Lo * id7Hi), "best", id7Lo, id7Hi)
  add("2026", "land", "EV (real-world average)", 50, 21.0, "typical")
  add("2026", "air", "787/A350 (full)", 900, 32.0, "best")
  add("2026", "water", "Candela P-12 (30 seats)", p12Kmh, p12Seat, "best")
  add("2026", "water", "Candela C-8 (6 aboard)", 40.7, c8 / 6 * 100, "best")
  add("2026", "water", "Candela C-8 (2 aboard)", 40.7, c8 / 2 * 100, "typical")
  // The diesel vessels' speed is not published. It is derived from the same
  // route taking 55 minutes against the P-12's 30, and rounded to whole km/h
  // because "about 30 minutes" does not support a decimal.
  add("2026", "water", "Diesel ferry it replaced", math.round(p12Kmh * 30 / 55).toDouble,
      math.sqrt(dieselLo * dieselHi), "typical", dieselLo, dieselHi)

  os.write.over(dir / "transport-energy.csv", out.toString)
  println("wrote data-refresh/transport-energy.csv")
  println(f"  Candela P-12   $p12%.2f kWh/km  ->  $p12Seat%.1f kWh/100 p-km at 30 seats")
  println(f"  Candela C-8    $c8%.3f kWh/km  ->  ${c8 / 6 * 100}%.1f (6 aboard), ${c8 / 2 * 100}%.1f (2 aboard)")
  println(f"  diesel ferry backed out of the savings claim: $dieselLo%.0f-$dieselHi%.0f kWh/100 p-km")
}

// ---- Nuclear and renewable cost ranges, chapter 24 ----
// Published levelised costs are ranges, not points, and for nuclear the range is
// mostly the discount rate rather than the reactor. Assembled from the sources
// listed in chapter 24's [^nucecon]; no commas in labels (bare CSV, sniffed delimiter).
@main
def nuclearCosts(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  // No capture prices here: the only series available is Elexon's GB figures in
  // pounds, and these cost rows are European and global in euros. Figure 28a.4
  // does that comparison properly, on one market and one currency.
  val out = new StringBuilder; out ++= "group,label,lo,mid,hi\n"
  def add(g: String, l: String, lo: Double, mid: Double, hi: Double, cap: Double = 0) =
    out ++= f"$g,$l,$lo%.1f,$mid%.1f,$hi%.1f\n"

  // Renewables and hydro: IRENA 2024 weighted averages and Danish Energy Agency data.
  add("Renewable", "Hydropower", 40, 50, 65, 0)
  add("Renewable", "Onshore wind", 44, 51, 62, 72)
  add("Renewable", "Solar PV", 48, 56, 70, 66)
  add("Renewable", "Offshore wind (fixed)", 105, 126, 150, 72)
  add("Renewable", "Offshore wind (floating)", 160, 190, 230, 72)
  // The same machine, financed and built four ways. This is the chapter's point.
  add("Nuclear", "Nuclear (China)", 55, 60, 70, 0)
  add("Nuclear", "Nuclear (Sweden at 4%)", 66, 72, 80, 80)
  add("Nuclear", "SMR (Rolls-Royce target)", 70, 75, 95, 0)
  add("Nuclear", "SMR (Korean estimate)", 78, 85, 105, 0)
  add("Nuclear", "Nuclear (commercial WACC)", 150, 175, 200, 80)

  os.write.over(dir / "nuclear-costs.csv", out.toString)
  println("wrote data-refresh/nuclear-costs.csv")
  val n = out.toString.linesIterator.drop(1).filter(_.startsWith("Nuclear"))
    .map(l => (l.split(",")(2).toDouble, l.split(",")(4).toDouble)).toList
  println(f"  nuclear spans ${n.map(_._1).min}%.0f-${n.map(_._2).max}%.0f EUR/MWh, a factor of ${n.map(_._2).max / n.map(_._1).min}%.1f")
}

// ---- Chapter 24: nuclear generation, China against Germany ----
// The clearest single picture of what happened to nuclear since MacKay wrote:
// one country's fleet went to zero while another's went past everyone.
@main
def nuclearHistory(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  val xlsx = (dir / "ei-stats-review-all-data.xlsx").toString
  withConn { conn =>
    val st = conn.createStatement(); st.execute("INSTALL excel; LOAD excel;")
    val y0 = 1965
    val cols = (y0 to 2025).map(y => "\"" + colName(1 + (y - y0)) + "\"").mkString(",")
    val want = Seq("China", "Germany", "US", "France", "Total World")
    val rs = st.executeQuery(
      s"""SELECT A, $cols FROM read_xlsx('$xlsx', sheet='Nuclear Generation - TWh',
          header=false, all_varchar=true, range='A3:${colName(1 + (2025 - y0))}120')
          WHERE A IS NOT NULL""")
    val out = new StringBuilder; out ++= "country,year,twh\n"
    val peak = scala.collection.mutable.Map[String, (Int, Double)]()
    while (rs.next()) {
      val name = rs.getString(1).trim
      if (want.contains(name)) {
        for (y <- y0 to 2025) {
          val s = rs.getString(y - y0 + 2)
          if (s != null && s.nonEmpty && s.toDoubleOption.isDefined) {
            val v = s.toDouble
            out ++= f"$name,$y,$v%.2f\n"
            if (v > peak.getOrElse(name, (0, 0.0))._2) peak(name) = (y, v)
          }
        }
      }
    }
    os.write.over(dir / "nuclear-history.csv", out.toString)
    println("wrote data-refresh/nuclear-history.csv")
    for (c <- want; (y, v) <- peak.get(c)) println(f"  $c%-12s peak $v%7.1f TWh in $y")
  }
}

// ---- Chapter 24: death rates by generation technology ----
// Replaces MacKay's figure 24.11, which used ExternE and Paul Scherrer figures
// from before 2008. Deaths per TWh; MacKay's own unit is deaths per GW-year,
// which is 8.76 times larger.
@main
def deathRates(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val out = new StringBuilder; out ++= "source,deaths_twh,deaths_gwy,basis\n"
  // Fossil and biomass include air pollution as well as accidents; the low-carbon
  // rows are accident deaths only, which is the comparison's main weakness.
  val rows = Seq(("Coal", 24.6, "accidents and air pollution"),
                 ("Oil", 18.4, "accidents and air pollution"),
                 ("Biomass", 4.6, "accidents and air pollution"),
                 ("Natural gas", 2.8, "accidents and air pollution"),
                 ("Hydropower", 1.3, "accidents"),
                 ("Wind", 0.04, "accidents"),
                 ("Nuclear", 0.03, "accidents"),
                 ("Solar", 0.02, "accidents"))
  for ((s, d, b) <- rows) out ++= f"$s,$d%.2f,${d * 8.76}%.3f,$b\n"
  os.write.over(dir / "death-rates.csv", out.toString)
  println("wrote data-refresh/death-rates.csv")
  println(f"  coal is ${24.6 / 0.03}%.0f times nuclear; coal ${24.6 * 8.76}%.0f deaths/GWy, nuclear ${0.03 * 8.76}%.2f")
}

// ---- Figure 20.21 remade, and MacKay's own folded into it ----
// His figure 20.21 is a scatter of 19 G-Wiz recharges with three guide lines
// through it, at 16, 21 and 33 kWh/100 km. Rather than print his chart and a
// second one beside it, the three numbers he draws are carried here: the 21 as
// a line, the 16 and the 33 as the edges of a band. All three are his own, from
// the text of this chapter - "the average transport cost of this G-Wiz is 21
// kWh per 100 km ... The best result was 16 ... and the worst was 33" - so
// nothing is read off his axes.
@main
def socketEnergy(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val out = new StringBuilder; out ++= "label,kwh_per_100km,era,kind,note\n"
  def add(l: String, v: Double, era: String, kind: String, n: String) =
    out ++= f"$l,$v%.2f,$era,$kind,$n\n"
  // No commas in any field: bare CSV, DuckDB sniffs the delimiter.
  add("E-bike (light assist)", 0.62, "2026", "line", "500 Wh over 80 km")
  add("Citroen Ami", 7.33, "2026", "line", "5.5 kWh over 75 km")
  add("Best in class (Kona Electric)", 13.4, "2026", "line", "WLTP")
  add("Electric fleet average", 21.0, "2026", "line", "real-world across 342 European cars")
  add("MacKay's G-Wiz", 21.0, "2008", "line", "mean of his 19 recharges")
  add("MacKay's best recharge", 16.0, "2008", "band", "best of the same 19")
  add("MacKay's worst recharge", 33.0, "2008", "band", "worst of the same 19")
  add("Large electric pickup", 30.0, "2026", "line", "indicative")
  os.write.over(dir / "socket-energy.csv", out.toString)
  println("wrote data-refresh/socket-energy.csv")
  println(f"  petrol car at 80 kWh/100km reaches 45 kWh after ${45.0 / 80 * 100}%.0f km")
  println("  MacKay's own spread, 16 to 33, is drawn as a band: a factor of 2.1 across one car")
}

// ---- Figure 20.22 remade: European Car of the Year, when it was electric ----
// MacKay's figure 20.22 is a Tesla Roadster at 15 kWh/100 km. The award's
// electric winners crossed below that figure around 2024-25.
@main
def cotyEfficiency(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  // No commas in any field: bare CSV, DuckDB sniffs the delimiter.
  val out = new StringBuilder; out ++= "year,model,lo,hi\n"
  def add(y: Int, m: String, lo: Double, hi: Double) = out ++= f"$y,$m,$lo%.1f,$hi%.1f\n"
  add(2019, "Jaguar I-Pace", 22.0, 25.2)
  add(2022, "Kia EV6", 15.9, 20.9)
  add(2024, "Renault Scenic E-Tech", 13.3, 17.8)
  add(2025, "Renault 5 E-Tech", 14.9, 14.9)
  add(2026, "Mercedes-Benz CLA", 12.2, 14.1)
  os.write.over(dir / "coty-efficiency.csv", out.toString)
  println("wrote data-refresh/coty-efficiency.csv")
  println("  MacKay's Tesla Roadster reference: 15.0 kWh/100 km")
}

// ---- Figure 18.9 remade: British renewables and nuclear, 2006 and 2025 ----
// MacKay's figure shows 2006, with the renewable breakdown scaled 100-fold
// vertically because it was too small to see. It no longer needs scaling.
@main
def ukLowCarbon(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  // Generation in TWh from the Statistical Review, over the population of each year.
  val pop2006 = 60.8e6; val pop2025 = 68.4e6
  def perDay(twh: Double, pop: Double) = twh * 1e9 / pop / 365.0
  // 2006 values are MacKay's era, from the same series; 2025 as used throughout.
  val rows = Seq(
    ("Wind",    4.2,  87.1),
    ("Solar",   0.0,  20.0),
    ("Hydro",   4.6,   5.1),
    ("Biomass", 8.5,  40.5),
    ("Nuclear", 75.5, 35.9))
  val out = new StringBuilder; out ++= "source,y2006,y2025\n"
  for ((n, a, b) <- rows)
    out ++= f"$n,${perDay(a, pop2006)}%.4f,${perDay(b, pop2025)}%.4f\n"
  os.write.over(dir / "uk-low-carbon.csv", out.toString)
  println("wrote data-refresh/uk-low-carbon.csv")
  val t06 = rows.map(r => perDay(r._2, pop2006)).sum
  val t25 = rows.map(r => perDay(r._3, pop2025)).sum
  println(f"  total low-carbon $t06%.2f -> $t25%.2f kWh/d per person")
  println(f"  renewables only  ${t06 - perDay(75.5, pop2006)}%.2f -> ${t25 - perDay(35.9, pop2025)}%.2f")
}

// ---- Figures 18.11 and 18.12 made native: energy against GDP, as trajectories ----
// The OWID iframes these replace show a scatter of all countries in one year. The
// chapter's argument is about the *path* each rich country has traced, so this
// extracts the time series for a handful of them and plots the path itself.
@main
def energyVsGdp(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val total = fetch("energy-use-per-person-vs-gdp-per-capita", "owid-energy-vs-gdp.csv")
  val fossil = fetch("per-capita-fossil-energy-vs-gdp", "owid-fossil-vs-gdp.csv")

  withConn { conn =>
    val st = conn.createStatement()
    // No commas in any field: bare CSV, DuckDB sniffs the delimiter downstream.
    val want = Seq("United Kingdom", "Germany", "France", "United States", "Japan", "China")
    val inList = want.map(c => s"'$c'").mkString(",")
    // The two graphers do not share a unit: the total-energy one serves kWh per
    // person per year, the fossil one MWh - despite both declaring "kilowatt-hours
    // per person" in their metadata, so trust the values rather than the label.
    // The check is the fossil share: 110 of 120 kWh/d for the UK in 1990, 54 of 69
    // in 2025. Hence the differing scale factors - both end as kWh per person/day.
    for ((src, col, scale, out) <- Seq(
          (total, "Per capita energy consumption", 1.0 / 365.0, "energy-vs-gdp.csv"),
          (fossil, "Per capita fossil energy consumption", 1000.0 / 365.0, "fossil-vs-gdp.csv"))) {
      val rs = st.executeQuery(
        s"""SELECT Entity, Year, "$col" * $scale AS kwh_d, "GDP per capita" AS gdp
            FROM read_csv_auto('${src.toString}')
            WHERE Entity IN ($inList) AND "$col" IS NOT NULL AND "GDP per capita" IS NOT NULL
              AND Year >= 1990 ORDER BY Entity, Year""")
      val sb = new StringBuilder; sb ++= "country,year,kwh_d,gdp\n"
      var n = 0
      while (rs.next()) {
        sb ++= f"${rs.getString(1)},${rs.getInt(2)},${rs.getDouble(3)}%.2f,${rs.getDouble(4)}%.0f\n"; n += 1
      }
      os.write.over(dir / out, sb.toString)
      println(s"wrote data-refresh/$out ($n rows)")
    }
  }
}

/** Figures I.11 and I.12: greenhouse-gas emissions per person against income
  * and against energy use, for every country that carries all four variables.
  * MacKay drew both from the UNDP Human Development Report 2007; this rebuilds
  * them from Our World in Data, keeping his squares-and-circles convention by
  * carrying the HDI through so the figure script can split on it. */
@main
def ghgScatter(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val ghg = fetch("per-capita-ghg-emissions", "owid-per-capita-ghg-emissions.csv")
  val hdi = fetch("human-development-index", "owid-human-development-index.csv")
  // Income and energy come from the grapher figure 18.11 already uses, so the
  // two chapters quote the same GDP series rather than two that nearly agree.
  val eng = fetch("energy-use-per-person-vs-gdp-per-capita", "owid-energy-vs-gdp.csv")

  // 2023 is the latest year all four variables exist: HDI stops there, and the
  // energy and GDP series run further but cannot be joined past it.
  val YEAR = 2023
  withConn { conn =>
    val st = conn.createStatement()
    val rs = st.executeQuery(
      s"""SELECT g.Entity,
                 g."Per capita greenhouse gas emissions including land use" AS ghg_t,
                 e."GDP per capita" AS gdp,
                 e."Per capita energy consumption" / 365.0 AS kwh_d,
                 h."Human Development Index" AS hdi
          FROM read_csv_auto('${ghg.toString}') g
          JOIN read_csv_auto('${eng.toString}') e ON e.Entity = g.Entity AND e.Year = g.Year
          JOIN read_csv_auto('${hdi.toString}') h ON h.Entity = g.Entity AND h.Year = g.Year
          WHERE g.Year = $YEAR AND g.Code IS NOT NULL
            -- OWID's aggregates carry codes too - OWID_WRL for the world, OWID_EU27,
            -- OWID_HIC and the income groups - and every one of them survives the
            -- joins and plots as though it were a country, double-counting its own
            -- members. Excluding the whole family is safe: no OWID_ entity in these
            -- files is a real country.
            AND g.Code NOT LIKE 'OWID\\_%' ESCAPE '\\'
            AND g."Per capita greenhouse gas emissions including land use" IS NOT NULL
            AND e."GDP per capita" IS NOT NULL
            -- The energy series encodes at least one missing value as a literal 0
            -- (Tuvalu, 2023), which would otherwise plot on the y-axis of I.12 as a
            -- country emitting two tonnes on no energy at all.
            AND e."Per capita energy consumption" > 0
            AND h."Human Development Index" IS NOT NULL
          ORDER BY g.Entity""")
    // No commas in any field: DuckDB sniffs the delimiter when the figure reads it.
    val sb = new StringBuilder; sb ++= "country,ghg_t,gdp,kwh_d,hdi\n"
    var n = 0
    while (rs.next()) {
      val c = rs.getString(1).replace(",", "")
      sb ++= f"$c,${rs.getDouble(2)}%.2f,${rs.getDouble(3)}%.0f,${rs.getDouble(4)}%.2f,${rs.getDouble(5)}%.3f\n"
      n += 1
    }
    os.write.over(dir / "ghg-scatter.csv", sb.toString)
    println(s"wrote data-refresh/ghg-scatter.csv ($n rows, year $YEAR)")
  }
}

/** Chapter L's import-dependence table: net energy imports as a share of energy
  * use, for the Asian economies plus a Western and exporter comparison set.
  * Negative means net exporter, which is the whole point of the table. */
@main
def energyImports(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val src = dir / "owid-energy-imports.csv"
  if (!os.exists(src)) os.write.over(src, requests.get(
    "https://ourworldindata.org/grapher/energy-imports-and-exports-energy-use.csv?csvType=full",
    readTimeout = 60000).text())

  // No commas in any field: DuckDB sniffs the delimiter when the table is read back.
  val want = Seq("Singapore", "Japan", "South Korea", "Sri Lanka", "Cambodia", "Thailand",
    "Philippines", "Bangladesh", "Pakistan", "India", "Vietnam", "Nepal", "China",
    "Malaysia", "Myanmar", "Laos", "Indonesia", "Brunei", "Mongolia",
    "Germany", "France", "United Kingdom", "United States", "Australia", "Russia", "Kazakhstan")
  val inList = want.map(c => s"'$c'").mkString(",")
  val col = "\"Energy imports, net (% of energy use)\""
  withConn { conn =>
    val st = conn.createStatement()
    // Countries stop reporting in different years, so take each one's own latest.
    val rs = st.executeQuery(
      s"""WITH d AS (SELECT Entity, Year, $col AS pct FROM read_csv_auto('${src.toString}')
                     WHERE Entity IN ($inList) AND $col IS NOT NULL AND Code IS NOT NULL)
          SELECT Entity, Year, pct FROM d
          WHERE (Entity, Year) IN (SELECT Entity, max(Year) FROM d GROUP BY Entity)
          ORDER BY pct DESC""")
    val sb = new StringBuilder; sb ++= "country,year,pct\n"
    var n = 0
    val got = scala.collection.mutable.Set.empty[String]
    while (rs.next()) {
      sb ++= f"${rs.getString(1)},${rs.getInt(2)},${rs.getDouble(3)}%.0f\n"; n += 1
      got += rs.getString(1)
    }
    // Entities are matched by OWID display name, and those get renamed. Validate
    // before writing: a rename would otherwise drop a country silently and leave
    // the truncated CSV on disk for whoever is transcribing the table.
    val missing = want.toSet -- got
    if (missing.nonEmpty)
      sys.error(s"energyImports: no rows for ${missing.toSeq.sorted.mkString(", ")} - " +
        "check the entity names against the OWID series before using the output")
    os.write.over(dir / "energy-imports.csv", sb.toString)
    println(s"wrote data-refresh/energy-imports.csv ($n rows)")
  }
}

/** Figure 26.5 remade: half-hourly GB market-index prices on three days of
  * 2026, replacing MacKay's three days of 2006-07. The spread within a day is
  * what pays for storage, so the days are chosen to show its range: the widest
  * in the sample, a spring day that goes negative, and a median one. */
@main
def gbPrices(): Unit = {
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val days = Seq(
    ("2026-01-08", "widest spread in sample"),
    ("2026-01-14", "a median day"),
    // No commas in any label: DuckDB sniffs the delimiter when the figure reads it.
    ("2026-04-07", "spring - prices go negative"))
  val sb = new StringBuilder; sb ++= "day,label,period,price\n"
  var n = 0
  for ((d, label) <- days) {
    require(!label.contains(","), s"gbPrices: label for $d contains a comma")
    val date = java.time.LocalDate.parse(d)
    // A settlement day is 48 half-hours. The two clock-change days are 46 and 50,
    // and the figure maps period to hour as (period - 1)/2, which is wrong on
    // both - so refuse them here, before the network call, rather than fetching
    // a day that cannot be plotted.
    val zone = java.time.ZoneId.of("Europe/London")
    val hours = java.time.Duration.between(
      date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone)).toHours
    require(hours == 24,
      s"gbPrices: $d is a clock-change day ($hours hours) - the figure's hour mapping " +
        "assumes 48 periods, so pick another day")
    // Query from the previous day: the endpoint filters on startTime, not on
    // settlementDate, and under BST periods 1 and 2 of a day start at 23:00Z
    // and 23:30Z the day before. A window beginning at 00:00Z silently drops
    // them - which is exactly the overnight trough this figure is about.
    val url = s"https://data.elexon.co.uk/bmrs/api/v1/balancing/pricing/market-index" +
      s"?from=${date.minusDays(1)}T12:00Z&to=${date.plusDays(1)}T12:00Z&format=json"
    val js = ujson.read(requests.get(url, readTimeout = 60000).text())
    // APXMIDP is the APX index; the other provider reports zeros for most periods.
    val rows = js("data").arr
      .filter(r => r("dataProvider").str == "APXMIDP" && r("settlementDate").str == d)
      .sortBy(_("settlementPeriod").num)
    val got = rows.map(_("settlementPeriod").num.toInt).toSet
    // 48, restating the invariant the require above guarantees.
    val expected = (1 to 48).toSet
    val missing = expected -- got
    require(missing.isEmpty,
      s"gbPrices: $d should have ${expected.size} settlement periods but is missing " +
        s"${missing.toSeq.sorted.mkString(", ")} - if these are periods 1-2, widen the query window")
    val prices = rows.map(_("price").num)
    for (r <- rows) {
      val price = String.format(java.util.Locale.US, "%.2f", Double.box(r("price").num))
      sb ++= s"$d,$label,${r("settlementPeriod").num.toInt},$price\n"; n += 1
    }
    // Printed so a re-run surfaces any drift against the figures quoted in
    // chapter 26 rather than leaving it to be noticed later.
    println(String.format(java.util.Locale.US, "  %s  min %8.2f  max %8.2f  spread %7.2f",
      d, Double.box(prices.min), Double.box(prices.max), Double.box(prices.max - prices.min)))
  }
  os.write.over(dir / "gb-prices.csv", sb.toString)
  println(s"wrote data-refresh/gb-prices.csv ($n rows)")
}

// ---- Chapter 26: what a battery is paid, in Sweden and in Britain ----
// Figure 26.16a used to be drawn from numbers typed into a CSV by hand. Two of
// them turned out not to be reproducible from the source the note named, so the
// price side is computed here instead, from one method applied to every zone:
// the mean over the year of each day's cheapest and dearest four hours.
//
// Capacity prices are the other half. Britain's come from NESO's auction
// results, which are an open dataset; Sweden's are still hand-entered from
// Svenska kraftnat's monthly PDFs, which are not, and stay in
// data-refresh/se-battery-revenue.csv with the capital costs.
@main
def chapter26Batteries(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)

  /** Dygnets billigaste och dyraste fyra timmar, i snitt over aret. Antalet
    * observationer per dygn skiljer sig mellan zoner och over aret, sa fyra
    * timmar raknas som en sjattedel av dygnets punkter i stallet for ett fast
    * antal - annars blir en kvartstimmesdag fyra gangeromfattande fel. */
  def lowHigh(byDay: Map[String, Seq[Double]]): (Double, Double) = {
    val pairs = byDay.values.filter(_.size >= 20).map { v =>
      val k = math.max(1, math.round(v.size / 6.0).toInt)
      val s = v.sorted
      (s.take(k).sum / k, s.takeRight(k).sum / k)
    }.toSeq
    require(pairs.size >= 360, s"chapter26Batteries: only ${pairs.size} usable days")
    (pairs.map(_._1).sum / pairs.size, pairs.map(_._2).sum / pairs.size)
  }

  // Energy-Charts, som kapitlets not redan namner. Tidszonen ar zonens egen:
  // dygnet maste brytas dar marknaden bryter det.
  val sthlm = java.time.ZoneId.of("Europe/Stockholm")
  def energyCharts(bzn: String): Map[String, Seq[Double]] = {
    val cache = dir / "api-cache" / s"price-$bzn-2025.json"
    os.makeDir.all(cache / os.up)
    if (!os.exists(cache)) os.write.over(cache, requests.get(
      s"https://api.energy-charts.info/price?bzn=$bzn&start=2025-01-01&end=2025-12-31",
      readTimeout = 180000).text())
    val js = ujson.read(os.read(cache))
    val ts = js("unix_seconds").arr.map(_.num.toLong)
    val px = js("price").arr
    val byDay = ts.zip(px).flatMap { case (t, p) =>
      if (p.isNull) None else {
        val d = java.time.Instant.ofEpochSecond(t).atZone(sthlm).toLocalDate
        if (d.getYear == 2025) Some(d.toString -> p.num) else None
      }
    }.groupBy(_._1).view.mapValues(_.map(_._2).toSeq).toMap
    byDay
  }

  /** Elexon, samma marknadsindex som figur 26.5b. Veckorna hamtas med samma
    * indelning och samma cachenamn som capturePrices, sa de tva stegen delar
    * nedladdning i stallet for att hamta samma bytes tva ganger.
    *
    * Bada andpunkterna ar inklusive, sa varje veckoskarv ger en dubblerad
    * avrakningsperiod - 52 av dygnen kom ut med 49 halvtimmar i stallet for 48.
    * Nyckeln (dygn, period) tar bort dubbletterna. */
  def elexon(): Map[String, Seq[Double]] = {
    val cache = dir / "api-cache"
    val seen = collection.mutable.LinkedHashMap[(String, Int), Double]()
    var d = java.time.LocalDate.of(2025, 1, 1)
    while (d.getYear == 2025) {
      val e = d.plusDays(7)
      val url = s"https://data.elexon.co.uk/bmrs/api/v1/balancing/pricing/market-index" +
        s"?from=${d}T00:00Z&to=${e}T00:00Z&format=json"
      for (r <- ujson.read(cachedGet(url, cache / s"price-$d.json"))("data").arr
           if r("dataProvider").str == "APXMIDP" && r("settlementDate").str.startsWith("2025"))
        seen.getOrElseUpdate((r("settlementDate").str, r("settlementPeriod").num.toInt),
                             r("price").num)
      d = e
    }
    seen.toSeq.groupBy(_._1._1).view.mapValues(_.map(_._2)).toMap
  }

  /** NESO:s auktionsresultat. Kalenderaret 2025 ligger over tva brittiska
    * budgetar, sa bada arkiven fragas och volymvagas ihop. */
  val NESO_FY = Seq("ab130833-3ce4-4361-90fb-69fa3cf30f15",  // FY2024: jan-mars 2025
                    "be55ee51-b79e-47da-b71e-a0f8865d9d66")  // FY2025: april-dec 2025
  def nesoPrices(): Map[String, (Double, Double)] = {
    val acc = collection.mutable.Map[String, (Double, Double, Int)]()
    for (rid <- NESO_FY) {
      val sql = s"""SELECT "auctionProduct" p, sum("clearedVolume") vol,
                    sum("clearingPrice"*"clearedVolume") pv, count(*) n FROM "$rid"
                    WHERE "deliveryStart" >= '2025-01-01' AND "deliveryStart" < '2026-01-01'
                    AND "auctionProduct" IN ('DCL','DRL','DML','PQR','PBR') GROUP BY 1"""
      val js = ujson.read(requests.get("https://api.neso.energy/api/3/action/datastore_search_sql",
        params = Map("sql" -> sql), readTimeout = 180000).text())
      require(js("success").bool, s"chapter26Batteries: NESO query failed for $rid")
      for (r <- js("result")("records").arr) {
        val p = r("p").str
        val (v0, pv0, n0) = acc.getOrElse(p, (0.0, 0.0, 0))
        acc(p) = (v0 + r("vol").str.toDouble, pv0 + r("pv").str.toDouble, n0 + r("n").str.toInt)
      }
    }
    acc.view.mapValues { case (v, pv, n) => (pv / v, v / n) }.toMap
  }

  // ECB:s referenskurser, arsmedel 2025. Serien ar noterad som valuta per euro.
  def ecb(cur: String): Double = {
    val txt = cachedGet(
      s"https://data-api.ecb.europa.eu/service/data/EXR/D.$cur.EUR.SP00.A" +
        "?startPeriod=2025-01-01&endPeriod=2025-12-31&format=csvdata",
      dir / "api-cache" / s"ecb-$cur-2025.csv")
    val lines = txt.linesIterator.toSeq
    val col = lines.head.split(",").indexOf("OBS_VALUE")
    require(col >= 0, s"chapter26Batteries: no OBS_VALUE column in the ECB $cur series")
    val v = lines.tail.flatMap(l => l.split(",").lift(col).flatMap(_.toDoubleOption))
    require(v.size >= 200, s"chapter26Batteries: only ${v.size} ECB observations for $cur")
    v.sum / v.size
  }

  val se3days = energyCharts("SE3"); val se4days = energyCharts("SE4")
  val gbdays = elexon()
  val (se3lo, se3hi) = lowHigh(se3days); val (se4lo, se4hi) = lowHigh(se4days)
  val (gblo, gbhi) = lowHigh(gbdays)
  val neso = nesoPrices()
  val gbpPerEur = ecb("GBP"); val usdPerEur = ecb("USD")

  /** Andelen av arets pristimmar under ett tak. Kapitlet stallde tidigare
    * brittiska timmar under 10 pund mot svenska under 10 euro, vilket inte ar
    * samma snitt; tacket ar i euro for bada nu och de brittiska priserna raknas
    * om med arets referenskurs. */
  def share(days: Map[String, Seq[Double]], eurCap: Double, toEur: Double = 1.0): Double = {
    val v = days.values.flatten.toSeq
    require(v.size >= 8000, s"chapter26Batteries: only ${v.size} price points for a share")
    100.0 * v.count(_ * toEur < eurCap) / v.size
  }

  val sb = new StringBuilder; sb ++= "post,varde,enhet,kalla\n"
  // No commas in any kalla: the row would gain a field the header does not have,
  // and DuckDB sniffs the delimiter when the figure reads it. Same rule as gbPrices.
  def add(k: String, v: Double, unit: String, src: String) = {
    require(!src.contains(","), s"chapter26Batteries: kalla for $k contains a comma")
    sb ++= f"$k,$v%.3f,$unit,$src\n"
  }
  val ec = "Energy-Charts 2025 - snitt av dygnets fyra billigaste respektive dyraste timmar"
  add("se3_lag", se3lo, "EUR/MWh", ec); add("se3_hog", se3hi, "EUR/MWh", ec)
  add("se4_lag", se4lo, "EUR/MWh", ec); add("se4_hog", se4hi, "EUR/MWh", ec)
  val el = "Elexon marknadsindex APXMIDP 2025 - samma rakning"
  add("gb_lag", gblo, "GBP/MWh", el); add("gb_hog", gbhi, "GBP/MWh", el)
  for (p <- Seq("DCL", "DRL", "DML", "PQR", "PBR")) {
    require(neso.contains(p), s"chapter26Batteries: NESO returned no rows for $p")
    val (pris, mw) = neso(p)
    add(s"gb_${p.toLowerCase}", pris, "GBP/MW/h",
        "NESO EAC auktionsresultat 2025 - volymvagt")
    add(s"gb_${p.toLowerCase}_mw", mw, "MW", "NESO EAC - snittvolym per block 2025")
  }
  val ea = "Energy-Charts 2025 - andel av arets pristimmar"
  add("se3_andel_under_10", share(se3days, 10), "%", ea)
  add("se3_andel_negativ", share(se3days, 0), "%", ea)
  add("gb_andel_under_10", share(gbdays, 10, 1 / gbpPerEur), "%",
      "Elexon APXMIDP 2025 - andel av arets halvtimmar omraknade till euro")
  add("gbp_per_eur", gbpPerEur, "GBP/EUR", "ECB referenskurs - arsmedel 2025")
  add("usd_per_eur", usdPerEur, "USD/EUR", "ECB referenskurs - arsmedel 2025")
  os.write.over(dir / "battery-prices.csv", sb.toString)

  println("wrote data-refresh/battery-prices.csv")
  println(f"  SE3 $se3lo%6.2f / $se3hi%6.2f EUR   SE4 $se4lo%6.2f / $se4hi%6.2f EUR   " +
          f"GB $gblo%6.2f / $gbhi%6.2f GBP")
  for ((p, (pris, mw)) <- neso.toSeq.sortBy(-_._2._1))
    println(f"  $p%-4s $pris%6.2f GBP/MW/h   snitt $mw%6.0f MW")
  println(f"  GBP/EUR $gbpPerEur%.4f   USD/EUR $usdPerEur%.4f")
  println("render:")
  println("  uv run figures/battery_revenue.py data-refresh/battery-prices.csv " +
          "data-refresh/se-battery-revenue.csv without-hot-air/Images/fig-battery-revenue.svg")
}

// ---- Chapter 11a: data-centre IT capacity against national peak load ----
// Figure 11a.1. Hand-entered, like lcoeVsCapture and for the same reason: the
// numbers exist only as a published chart. They are read off a dumbbell chart
// circulated by ARdS in 2026, "How material could data centres become for
// Europe's power systems?", which states its sources as the EUDCA European
// Data Centre Market Monitor 2025 (capacity) and the ENTSO-E Statistical
// Factsheet 2025 (peak load). This edition has not seen the Market Monitor
// itself, so the chapter's note says so.
//
// The quantity is total data-centre IT capacity - colocation plus
// hyperscale-owned - as a percentage of the country's 2025 national peak
// electricity load. Both years are expressed against the 2025 peak, so the
// 2031 column is not a forecast of the 2031 ratio: it is 2031 capacity over
// today's peak. It is nameplate IT capacity, not consumption and not spare
// capacity. The chapter's text does the conversion to an energy share and
// states the assumptions it needs.
//
// The United Kingdom is absent from the source chart and is therefore absent
// here rather than being filled in from a different basis.
@main
def chapter11aPeakShare(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  // (country, 2025, 2031 forecast), both as % of 2025 national peak load
  val rows = Seq(
    ("Ireland",     29.1, 46.4),
    ("Portugal",     0.6, 16.4),
    ("Denmark",      7.2, 14.6),
    ("Netherlands",  6.8, 12.8),
    ("Finland",      2.7,  9.0),
    ("Norway",       2.0,  8.6),
    ("Sweden",       2.5,  7.0),
    ("Spain",        1.0,  6.9),
    ("Germany",      2.3,  5.9),
    ("Belgium",      2.0,  5.2),
    ("Switzerland",  1.7,  4.5),
    ("Italy",        0.8,  4.0),
    ("Austria",      0.8,  3.5),
    ("Greece",       0.7,  2.4),
    ("Poland",       0.9,  2.3),
    ("France",       0.9,  2.1))
  for ((c, a, b) <- rows) require(b >= a && a > 0, s"chapter11aPeakShare: bad row for $c")
  val out = new StringBuilder; out ++= "country,share_2025,share_2031,multiple\n"
  for ((c, a, b) <- rows.sortBy(-_._3)) out ++= f"$c,$a%.1f,$b%.1f,${b / a}%.1f\n"
  os.write.over(dir / "dc-peak-share.csv", out.toString)
  println("wrote data-refresh/dc-peak-share.csv")
  for ((c, a, b) <- rows.sortBy(-_._3)) println(f"  $c%-13s $a%5.1f%% -> $b%5.1f%%  x${b / a}%.1f")
  // The two the chapter argues from, and the one line of arithmetic it needs.
  // A fleet running flat out draws roughly its IT capacity once the facility's
  // own overhead is added, so a capacity share divided by the grid's load
  // factor is about the energy share. Sweden's load factor is near 0.60.
  val (se25, se31) = rows.find(_._1 == "Sweden").map(r => (r._2, r._3)).get
  println(f"  Sweden at a 0.60 load factor: ${se25 / 0.60}%.1f%% of energy now, " +
          f"${se31 / 0.60}%.1f%% by 2031")
  println("render:")
  println("  uv run figures/dc_peak_share.py data-refresh/dc-peak-share.csv " +
          "without-hot-air/Images/fig-dc-peak-share.svg")
}

// ---- Chapter 4: the Cambridge rooftop, twenty years on ----
// Figures 4.1a and 4.6a. MacKay's figures 4.1 and 4.6 are the Computer
// Laboratory's rooftop weather station in 2006, and the station is still
// there and still publishing: one half-hourly CSV from 30 June 1995 to now.
// So this is the rare case where a figure can be carried forward on the same
// instrument at the same address rather than on a substitute.
//
// Three things about the source, all of which the chapter's note repeats.
//
// The wind column is tenths of a knot, like the temperature column is tenths
// of a degree. That is not documented; it is inferred, and the inference is
// checked below against MacKay's own published result - he says the daily
// mean reached 6 m/s on about 30 days of 2006, and on this reading it is 26.
// At any other scaling his sentence is nonsense, so the scaling is right.
//
// The station moved to the Computer Laboratory roof in 2004 from a lower,
// more sheltered roof, and the annual means step up by about a metre a second
// at that point. Only years from 2004 on are comparable with 2006, which is
// why the second panel is not from the 1990s.
//
// And the anemometer died. 2024 is 97% zeros and 2025 and 2026 are 100%, so
// the last usable year is 2023 - the station's own front page says only that
// the wind sensor has been wrong "since a power outage on 18 Jan", which
// understates it by about two years. Even before that the share of zero
// readings climbs from 8% in 2006 to 18% in 2023, so the later year's mean is
// depressed by an instrument recording calms that may not be calms. That bias
// runs downwards, and MacKay's claim is an upper bound, so it cannot rescue
// the 6 m/s figure - but it does mean the fall from 2.7 to 1.9 m/s is not
// evidence that Cambridge got less windy, and the chapter says so.
@main
def chapter4CambridgeWind(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val raw = dir / "cambridge-weather-raw.csv"      // 26 MB, gitignored, regenerable
  if (!os.exists(raw)) {
    println("fetching the station's whole half-hourly record (26 MB)...")
    os.write.over(raw, requests.get(
      "https://www.cl.cam.ac.uk/research/dtg/weather/weather-raw.csv",
      readTimeout = 300000).text())
  }
  val knot = 0.514444 / 10                          // tenths of a knot -> m/s
  val years = Seq("2006", "2023")

  // (year, timestamp, speed) for the two years, and a mean per year for all of
  // them, which is what shows the 2004 move and the sensor's death.
  val kept = scala.collection.mutable.ArrayBuffer[(String, String, Double)]()
  val byYear = scala.collection.mutable.Map[String, (Double, Int, Int)]()
  for (line <- os.read.lines.stream(raw)) {
    val f = line.split(",", -1)
    if (f.length >= 7) {
      val year = f(0).take(4)
      f(5).toIntOption.foreach { tenths =>
        val speed = tenths * knot
        val (sum, n, zeros) = byYear.getOrElse(year, (0.0, 0, 0))
        byYear(year) = (sum + speed, n + 1, zeros + (if (speed == 0) 1 else 0))
        if (years.contains(year)) kept += ((year, f(0), speed))
      }
    }
  }
  println("year   readings   mean m/s   zero readings")
  for (y <- byYear.keys.toSeq.sorted) {
    val (sum, n, zeros) = byYear(y)
    println(f"$y     $n%6d     ${sum / n}%5.2f     ${zeros * 100.0 / n}%5.1f%%")
  }

  val out = new StringBuilder; out ++= "year,timestamp,speed_ms\n"
  for ((y, t, s) <- kept) out ++= f"$y,$t,$s%.2f\n"
  os.write.over(dir / "cambridge-wind.csv", out.toString)
  println(f"wrote data-refresh/cambridge-wind.csv (${kept.length}%d half-hourly readings)")

  // Daily means, and MacKay's own test: how many days average 6 m/s or more.
  for (y <- years) {
    val days = kept.filter(_._1 == y).groupBy(_._2.take(10)).values
      .filter(_.length >= 36).map(v => v.map(_._3).sum / v.length).toSeq
    val half = kept.filter(_._1 == y).map(_._3)
    val over = days.count(_ >= 6)
    println(f"$y: ${days.length}%3d days, mean ${days.sum / days.length}%4.2f m/s, " +
            f"$over%3d days at or above 6 m/s, " +
            f"${half.count(_ >= 6) * 100.0 / half.length}%4.1f%% of half-hours above 6, " +
            f"mean of the cube ${half.map(v => v * v * v).sum / half.length}%5.1f m3/s3")
    if (y == "2006") require(over >= 20 && over <= 35,
      s"chapter4CambridgeWind: 2006 gives $over days at or above 6 m/s, " +
      "MacKay's text says about 30 - the wind column's scaling must be wrong")
  }
  println("render:")
  println("  uv run figures/cambridge_wind.py data-refresh/cambridge-wind.csv " +
          "without-hot-air/Images/fig-cambridge-wind.svg")
  println("  uv run figures/cambridge_wind_hist.py data-refresh/cambridge-wind.csv " +
          "without-hot-air/Images/fig-cambridge-wind-hist.svg")
}

// ---- Chapter 4: Cairngorm summit, MacKay's other wind figure ----
// Figure 4.2a. His figure 4.2 is "six months of 2006" from the Heriot-Watt
// automatic weather station on the summit of Cairn Gorm, 1245 m up. The
// station is still running and its archive goes back to 1990.
//
// The six months turn out not to have been a choice. The 2006 file has means
// of 0.0 and 0.1 mph for July, August and December: the anemometer failed in
// the summer and again at the end of November, which the station's own log
// records ("1/12/06 Suspected anemometer fault"). January to June is what
// 2006 has, and it is what he plotted.
//
// The archive's file names are not consistent - YEARDATA2006.txt, YearData.txt,
// "2010 Data.txt" - so each year's page is read for its own link. The 2006
// file is tab-separated and starts with a date string; the current year's is
// comma-separated and starts with the day number. Both carry mean wind in mph
// from 1996 on, and that is the only column used here.
@main
def chapter4Cairngorm(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val site = "https://cairngormweather.eps.hw.ac.uk"
  val mph = 0.44704
  val plotted = Seq(2006 -> "2006", 2026 -> "CurrentYear")

  /** The archive page for a year, scraped for whatever it calls its data file. */
  def yearFile(year: Int): os.Path = {
    val cache = dir / s"cairngorm-$year.txt"              // gitignored, regenerable
    if (!os.exists(cache)) {
      val page = requests.get(s"$site/$year", readTimeout = 60000).text()
      val href = "href=\"([^\"]+\\.txt)\"".r.findFirstMatchIn(page)
        .map(_.group(1)).getOrElse(sys.error(s"chapter4Cairngorm: no data link on $site/$year"))
      val url = if (href.startsWith("http")) href else s"$site/$year/${href.replace(" ", "%20")}"
      os.write.over(cache, requests.get(url, readTimeout = 300000).text())
    }
    cache
  }
  def currentFile(): os.Path = {
    val cache = dir / "cairngorm-current.txt"
    if (!os.exists(cache))
      os.write.over(cache, requests.get(s"$site/CurrentYear.txt", readTimeout = 300000).text())
    cache
  }

  /** (day of year, time, speed in m/s), from either of the archive's layouts. */
  def read(file: os.Path): Seq[(Int, Int, Double)] =
    os.read.lines(file).flatMap { line =>
      val f = if (line.contains("\t")) line.split("\t", -1) else line.split(",", -1)
      val off = if (line.contains("\t")) 1 else 0        // the tab layout leads with a date
      for {
        day <- f.lift(off).flatMap(_.trim.toDoubleOption).map(_.toInt)
        time <- f.lift(off + 1).flatMap(_.trim.toDoubleOption).map(_.toInt)
        v <- f.lift(off + 2).flatMap(_.trim.toDoubleOption)
        if v >= 0 && v <= 120                            // 6999 and friends are sensor errors
      } yield (day, time, v * mph)
    }

  val out = new StringBuilder; out ++= "year,day,time,speed_ms\n"
  for ((year, _) <- plotted) {
    val rows = read(if (year == 2026) currentFile() else yearFile(year))
    for ((d, t, s) <- rows) out ++= f"$year,$d,$t,$s%.2f\n"
    val halves = rows.filter(_._1 <= 182)
    val days = halves.groupBy(_._1).values.filter(_.length >= 36).map(v => v.map(_._3).sum / v.length)
    println(f"$year January-June: ${halves.length}%5d readings, ${days.size}%3d full days, " +
            f"daily mean ${days.sum / days.size}%5.2f m/s, " +
            f"${days.count(_ >= 6)}%3d days at or above 6 m/s")
    if (year == 2006) require(days.count(_ >= 6) > days.size / 2,
      "chapter4Cairngorm: 2006 should be windy - more than half its days above 6 m/s")
  }
  os.write.over(dir / "cairngorm-wind.csv", out.toString)
  println("wrote data-refresh/cairngorm-wind.csv")

  // A context scan over the other archive years was tried and dropped: several
  // years (2012 reads 1.4 m/s for a Cairngorm winter, 2016 3.9) are anemometer
  // failures rather than calm weather, and this edition has no way to tell
  // which are which. The chapter therefore claims no trend, only the contrast
  // with Cambridge, which is a factor of four and survives any of it.
  println("render:")
  println("  uv run figures/cairngorm_wind.py data-refresh/cairngorm-wind.csv " +
          "without-hot-air/Images/fig-cairngorm-wind.svg")
}

// ---- Chapter 11a: what Finland has already committed ----
// Figure 11a.2. Hand-entered like chapter11aPeakShare, but with a better
// provenance than that one: four of the seven bars are in documents this
// edition has read, two are second hand, and the seventh is the sum of the
// first three - so it is only as sound as the softest of them. The note in
// the chapter says which is which.
//
// The quantity is data-centre *electricity* capacity in megawatts - the grid
// connection the facility is built around, not the IT load inside it, which is
// smaller by the cooling and the losses. That is a different basis from figure
// 11a.1, which counts IT capacity, and the chapter states the difference where
// it compares the two.
//
// The left-hand bars are cumulative and the right-hand ones are separate
// scenarios:
//   285 MW   33 data centres operating in September 2025, from the government
//            rapporteur's report (Ramboll's census: facilities over 1 MW,
//            crypto-mining excluded).
//   +1300    projects with an investment decision or already starting up at
//            the end of August 2025, for completion by 2027, from the
//            Confederation of Finnish Industries' investment dashboard as
//            quoted in the same report. A further 2500 MW sat in planning or
//            feasibility and is deliberately NOT in the total.
//   +1300    Google's four Finnish sites, announced 9 September 2026. Google
//            does not publish their capacity; this is Helsingin Sanomat's
//            estimate from the EUR 13 billion, and is the softest bar here.
//   2500     AFRY's strong-development case for 2030, run for the rapporteur's
//            report. Its baseline is 1200 MW.
//   1900     KEITO's assumption for 2050 - about 10 TWh a year in VTT
//            Technology 442 - converted to power at 60% utilisation.
//   7000     Luke's electrification pathway for 2055, converted the same way.
//            Read off the source chart; this edition has not seen the Luke
//            publication itself.
//
// Assembled as a comparison by Ilkka Hannula (Carbon Economics, 2026, CC BY
// 4.0); this is our own redraw, with the numbers checked against the sources
// above where this edition could reach them.
@main
def chapter11aFinlandPipeline(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  // (label, sub-label, MW, kind) - kind is what the figure does with the bar.
  val rows = Seq(
    ("In operation",                  "Sept 2025",  285.0, "base"),
    ("Decided or under construction", "Aug 2025",  1300.0, "add"),
    // Its own kind, so the figure colours the estimate on what it is rather
    // than on it happening to be the last bar added.
    ("Google decision",               "Sept 2026", 1300.0, "add-estimate"),
    ("Committed capacity",            "",          2885.0, "total"),
    ("AFRY strong growth",            "2030",      2500.0, "scenario"),
    ("KEITO assumption",              "2050",      1900.0, "scenario"),
    ("Luke electrification pathway",  "2055",      7000.0, "scenario"))

  val committed = rows.filter(r => Seq("base", "add", "add-estimate").contains(r._4)).map(_._3).sum
  val stated = rows.find(_._4 == "total").map(_._3).get
  require(committed == stated,
    s"chapter11aFinlandPipeline: bars sum to $committed, total says $stated")
  // One of each of the three singular kinds. The estimate is in here because
  // the figure gives that kind its own warning colour and the arithmetic below
  // takes Google's capacity from it, so a second one would quietly break both.
  require(rows.count(_._4 == "base") == 1 && rows.count(_._4 == "total") == 1 &&
          rows.count(_._4 == "add-estimate") == 1,
    "chapter11aFinlandPipeline: expected exactly one base bar, one total and one estimate")

  val out = new StringBuilder; out ++= "label,sublabel,mw,kind\n"
  for ((l, s, mw, k) <- rows) out ++= f"$l,$s,$mw%.0f,$k\n"
  os.write.over(dir / "fi-dc-pipeline.csv", out.toString)
  println("wrote data-refresh/fi-dc-pipeline.csv")
  for ((l, s, mw, k) <- rows) println(f"  $l%-30s $s%-10s $mw%6.0f MW  $k")

  // The arithmetic the chapter does with these bars, printed so that the text
  // and the figure cannot drift apart.
  val planning = 2500.0            // EK dashboard, planning and feasibility, Aug 2025
  val meteredTWh = 1.3             // Finnish tax records, data centres, 2024
  val peakMW = 15553.0             // Fingrid, 8 January 2026, a Finnish record
  val fiTWh = 82.0; val pop = 5.6e6 // as in the Loviisa arithmetic above
  val contractedTWh = 6.0
  // Both of these are bars in the table above. Read them from it rather than
  // retyping them: the 285 MW baseline is the one the note calls low, and if
  // a later census revises it the bars, the total and this arithmetic have to
  // move together or the chapter silently keeps dividing by the old number.
  val baseMW = rows.find(_._4 == "base").map(_._3).get
  val googleMW = rows.find(_._4 == "add-estimate").map(_._3)
    .getOrElse(sys.error("chapter11aFinlandPipeline: no estimated bar to take the Google capacity from"))

  val useNow = meteredTWh * 1e6 / (baseMW * 8760)  // what the built fleet runs at
  println(f"  metered 2024 $meteredTWh%.1f TWh over $baseMW%.0f MW nameplate: " +
          f"${useNow * 100}%.0f%% utilisation")
  for (u <- Seq(useNow, 0.60, 0.85)) {
    val twh = committed * 8760 * u / 1e6
    println(f"  committed $committed%.0f MW at ${u * 100}%.0f%%: $twh%5.1f TWh, " +
            f"${twh / fiTWh * 100}%4.1f%% of Finnish electricity, " +
            f"${twh * 1e9 / pop / 365}%5.1f kWh/d per Finn")
  }
  for ((l, _, mw, k) <- rows if k == "scenario")
    println(f"  committed / $l%-30s ${committed / mw}%.2f")
  println(f"  committed against the record peak $peakMW%.0f MW: " +
          f"${committed / peakMW * 100}%.0f%% (figure 11a.1 forecasts 9.0%% of peak for 2031)")
  println(f"  with the 2500 MW still in planning: ${committed + planning}%.0f MW, " +
          f"${(committed + planning) / peakMW * 100}%.0f%% of the peak")
  for (u <- Seq(useNow, 0.60, 0.85)) {
    val twh = googleMW * 8760 * u / 1e6
    println(f"  Google $googleMW%.0f MW at ${u * 100}%.0f%%: $twh%.1f TWh, " +
            f"contract covers ${contractedTWh / twh * 100}%.0f%%")
  }
  println("render:")
  println("  uv run figures/fi_dc_pipeline.py data-refresh/fi-dc-pipeline.csv " +
          "without-hot-air/Images/fig-fi-dc-pipeline.svg")
}

// ---- Appendix A: electric-car range against transport cost ----
// Figure A.14a. This is MacKay's own model, not a new one: the figure recomputes
// his figure A.14 from the assumptions his text states - 740 kg of car and
// occupants without batteries, 50 km/h, a drag-area of 0.8 m2, rolling
// resistance 0.01, 500 m between stops, regenerative braking recovering half
// the kinetic energy, a drive efficiency of 85% and charging at 85% - and then
// adds the two things twenty years changed.
//
// The first is the battery. His curves are 40 Wh/kg (lead-acid) and 120 Wh/kg
// (the lithium cells of 2008). A 2025 pack is about 160 Wh/kg.
//
// The second is the car. 740 kg for car and occupants is a 2008 abstraction;
// a 2025 electric car without its pack is more like 1500 kg with two people in
// it, and the mass term in his own model taxes that. The fourth curve is the
// modern pack in the heavier car, and it is the one that lands on what real
// cars are measured to use.
//
// The task prints his five stated results first. They come out as he printed
// them, which is the check that the recompute is his model and not a new one.
@main
def chapterAElectricRange(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val rho = 1.3; val v = 50 / 3.6; val cdA = 0.8; val crr = 0.01
  val g = 9.81; val stops = 500.0; val regen = 0.5; val drive = 0.85; val charge = 0.85

  // Newtons per metre of travel: air, rolling, and the half of each stop's
  // kinetic energy that braking does not give back.
  def force(mass: Double) =
    0.5 * rho * cdA * v * v + crr * mass * g + (1 - regen) * 0.5 * mass * v * v / stops
  // kWh per 100 km, at the battery terminals or at the wall socket.
  def perHundred(mass: Double, atWall: Boolean) = {
    val battery = force(mass) * 1e5 / 3.6e6 / drive
    if (atWall) battery / charge else battery
  }
  def range(battKg: Double, whPerKg: Double, glider: Double) =
    (battKg * whPerKg / 1000) / perHundred(glider + battKg, false) * 100
  /** Battery mass for a wanted range, by bisection: the mass is on both sides
    * of the equation, because carrying it costs range. */
  def battFor(km: Double, whPerKg: Double, glider: Double) = {
    var (lo, hi) = (0.1, 1e4)
    for (_ <- 1 to 200) {
      val mid = (lo + hi) / 2
      if (range(mid, whPerKg, glider) < km) lo = mid else hi = mid
    }
    (lo + hi) / 2
  }

  // MacKay's own results, as printed in his text and caption.
  val his = Seq((500.0, 40.0, 180.0), (500.0, 120.0, 538.0), (2000.0, 40.0, 400.0),
                (250.0, 120.0, 300.0), (100.0, 120.0, 140.0))
  println("MacKay's figure A.14, recomputed from his assumptions:")
  for ((mb, dens, stated) <- his) {
    val r = range(mb, dens, 740)
    println(f"  $mb%6.0f kg at $dens%5.0f Wh/kg: $r%5.0f km (his text says $stated%.0f), " +
            f"${perHundred(740 + mb, true)}%5.1f kWh/100 km at the wall")
    require(math.abs(r - stated) / stated < 0.10,
      s"chapterAElectricRange: $mb kg at $dens Wh/kg gives $r, his text says $stated")
  }

  // (label, pack energy density, mass of car and occupants without the pack)
  val series = Seq(
    ("Lead-acid 40 Wh/kg",        40.0,  740.0),   // no commas: the label is a CSV field
    ("Lithium 2008 120 Wh/kg",   120.0,  740.0),
    ("Pack 2025 160 Wh/kg",      160.0,  740.0),
    ("Pack 2025 in a 2025 car",  160.0, 1500.0))
  val out = new StringBuilder; out ++= "series,wh_per_kg,glider_kg,batt_kg,range_km,kwh_per_100km\n"
  for ((label, dens, glider) <- series; mb <- 25 to 2000 by 25)
    out ++= f"$label,$dens%.0f,$glider%.0f,$mb,${range(mb, dens, glider)}%.1f," +
            f"${perHundred(glider + mb, true)}%.2f\n"
  os.write.over(dir / "ev-range.csv", out.toString)
  println("wrote data-refresh/ev-range.csv")

  println("what the two changes do:")
  for ((label, dens, glider) <- series)
    println(f"  $label%-26s 500 kg pack: ${range(500, dens, glider)}%4.0f km at " +
            f"${perHundred(glider + 500, true)}%4.1f kWh/100 km; " +
            f"300 km wants ${battFor(300, dens, glider)}%4.0f kg of pack")
}

// ---- Chapter 25: what one degree buys, in gigawatts ----
// Thermosensitivity: the slope of daily electricity demand against daily mean
// temperature, fitted separately on the cold arm and the hot arm.
//
// Method, stated here because the chapter's note states it too. Daily mean load
// against a population-weighted daily mean temperature, weekdays only, August
// excluded because industrial holidays move the base in Italy and France and
// would be read as a temperature effect. Two straight lines with fixed
// thresholds rather than fitted knots: the heating arm is every day below
// 15 C and the cooling arm every day above 20 C. Fixed thresholds are cruder
// than a fitted breakpoint and are used because they are the same for every
// country, so the six numbers can be compared.
//
// Load is ENTSO-E, taken from the energy-charts API as the price series in
// chapter 26 is. Great Britain is the exception: its ENTSO-E feed has been
// incomplete since Brexit - no nuclear, no solar, no load - so GB comes from
// NESO's own historic demand data. That also lets GB be put on the same basis
// as the others, because NESO publishes its estimate of the distribution-
// connected solar and wind that national demand nets off. Underlying demand
// here is ND + EMBEDDED_SOLAR_GENERATION + EMBEDDED_WIND_GENERATION; without
// that correction a sunny hot day looks like a fall in demand.
@main
def chapter25Thermosensitivity(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  import java.time.{LocalDate, Instant, ZoneId, DayOfWeek, Month}
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val cache = dir / "api-cache"
  val FROM = LocalDate.of(2021, 1, 1)
  val TO   = LocalDate.of(2026, 8, 31)
  val T_HEAT = 15.0   // below this, the heating arm
  val T_COOL = 20.0   // above this, the cooling arm

  // Weights are approximate metropolitan populations in millions. They only
  // weight the temperature average, so a rounded figure is enough.
  case class City(slug: String, lat: Double, lon: Double, w: Double)
  case class Land(key: String, label: String, tz: String, cities: Seq[City])
  val LANDS = Seq(
    Land("uk", "Great Britain", "Europe/London", Seq(
      City("london", 51.5072, -0.1276, 9.6), City("birmingham", 52.4862, -1.8904, 2.6),
      City("manchester", 53.4808, -2.2426, 2.7), City("leeds", 53.8008, -1.5491, 1.9),
      City("glasgow", 55.8642, -4.2518, 1.2))),
    Land("fr", "France", "Europe/Paris", Seq(
      City("paris", 48.8566, 2.3522, 11.1), City("lyon", 45.7640, 4.8357, 1.7),
      City("marseille", 43.2965, 5.3698, 1.6), City("toulouse", 43.6047, 1.4442, 1.0),
      City("lille", 50.6292, 3.0573, 1.0))),
    Land("de", "Germany", "Europe/Berlin", Seq(
      City("berlin", 52.5200, 13.4050, 3.6), City("hamburg", 53.5511, 9.9937, 1.9),
      City("munich", 48.1351, 11.5820, 1.5), City("cologne", 50.9375, 6.9603, 1.1),
      City("frankfurt", 50.1109, 8.6821, 0.8))),
    Land("it", "Italy", "Europe/Rome", Seq(
      City("rome", 41.9028, 12.4964, 2.8), City("milan", 45.4642, 9.1900, 1.4),
      City("naples", 40.8518, 14.2681, 0.9), City("turin", 45.0703, 7.6869, 0.85),
      City("palermo", 38.1157, 13.3615, 0.63))),
    Land("es", "Spain", "Europe/Madrid", Seq(
      City("madrid", 40.4168, -3.7038, 3.3), City("barcelona", 41.3874, 2.1686, 1.6),
      City("valencia", 39.4699, -0.3763, 0.8), City("seville", 37.3891, -5.9845, 0.69),
      City("zaragoza", 41.6488, -0.8891, 0.68))),
    Land("se", "Sweden", "Europe/Stockholm", Seq(
      City("stockholm", 59.3293, 18.0686, 1.0), City("gothenburg", 57.7089, 11.9746, 0.6),
      City("malmo", 55.6050, 13.0038, 0.35), City("uppsala", 59.8586, 17.6389, 0.24),
      City("vasteras", 59.6099, 16.5448, 0.13))))

  /** Population-weighted daily mean temperature, from the Open-Meteo archive
    * (ERA5), the same source chapter 7's Cambridge figure uses. */
  def dailyTemp(l: Land): Map[LocalDate, Double] = {
    val acc = collection.mutable.Map[LocalDate, (Double, Double)]()
    for (c <- l.cities) {
      val js = ujson.read(cachedGet(
        "https://archive-api.open-meteo.com/v1/archive" +
          s"?latitude=${c.lat}&longitude=${c.lon}&start_date=$FROM&end_date=$TO" +
          s"&daily=temperature_2m_mean&timezone=${l.tz.replace("/", "%2F")}",
        cache / s"meteo-${l.key}-${c.slug}.json"))
      val days = js("daily")("time").arr.map(_.str)
      val temp = js("daily")("temperature_2m_mean").arr
      require(days.size == temp.size, s"thermo: ${l.key}/${c.slug} ragged arrays")
      for ((d, v) <- days.zip(temp) if !v.isNull) {
        val k = LocalDate.parse(d)
        val (s, w) = acc.getOrElse(k, (0.0, 0.0))
        acc(k) = (s + v.num * c.w, w + c.w)
      }
    }
    acc.view.mapValues { case (s, w) => s / w }.toMap
  }

  /** energy-charts is free and rate-limits, so a year that comes back 429 is
    * waited out rather than failing the step. Only uncached years are fetched. */
  def politeGet(url: String, f: os.Path): String =
    if (os.exists(f)) os.read(f)
    else {
      var attempt = 0; var out: Option[String] = None
      while (out.isEmpty) {
        attempt += 1
        try out = Some(cachedGet(url, f))
        catch {
          // NonFatal only: an OutOfMemoryError is not something to sleep on.
          case scala.util.control.NonFatal(e) if attempt < 6 =>
            println(s"  ${f.last}: ${e.getClass.getSimpleName}, retrying in ${20 * attempt}s")
            Thread.sleep(20000L * attempt)
        }
      }
      Thread.sleep(3000)
      out.get
    }

  /** ENTSO-E load through energy-charts, averaged over each local day. The
    * resolution differs by country and over time, so days with fewer than 20
    * observations are dropped rather than averaged. */
  def entsoeLoad(l: Land): Map[LocalDate, Double] = {
    val zone = ZoneId.of(l.tz)
    val acc = collection.mutable.Map[LocalDate, (Double, Int)]()
    for (y <- FROM.getYear to TO.getYear) {
      val a = if (y == FROM.getYear) FROM else LocalDate.of(y, 1, 1)
      val b = if (y == TO.getYear) TO else LocalDate.of(y, 12, 31)
      val js = ujson.read(politeGet(
        s"https://api.energy-charts.info/public_power?country=${l.key}&start=$a&end=$b",
        cache / s"ecload-${l.key}-$y.json"))
      val ts = js("unix_seconds").arr.map(_.num.toLong)
      val series = js("production_types").arr.find(_("name").str == "Load")
        .getOrElse(sys.error(s"thermo: no Load series for ${l.key} in $y"))("data").arr
      for ((t, v) <- ts.zip(series) if !v.isNull) {
        val d = Instant.ofEpochSecond(t).atZone(zone).toLocalDate
        val (s, n) = acc.getOrElse(d, (0.0, 0)); acc(d) = (s + v.num, n + 1)
      }
    }
    acc.collect { case (d, (s, n)) if n >= 20 => d -> s / n / 1000.0 }.toMap
  }

  val NESO = Seq(
    2021 -> "18c69c42-f20d-46f0-84e9-e279045befc6/download/demanddata_2021.csv",
    2022 -> "bb44a1b5-75b1-4db2-8491-257f23385006/download/demanddata_2022.csv",
    2023 -> "bf5ab335-9b40-4ea4-b93a-ab4af7bce003/download/demanddata_2023.csv",
    2024 -> "f6d02c0f-957b-48cb-82ee-09003f2ba759/download/demanddata_2024.csv",
    2025 -> "b2bde559-3455-4021-b179-dfe60c0337b0/download/demanddata_2025.csv",
    2026 -> "8a4a771c-3929-4e56-93ad-cdf13219dea5/download/demanddataupdate_2026.csv")
  val MONTHS = Vector("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC")

  /** NESO writes the date three ways across the years - 01-JAN-2021, 01-Jan-23
    * and 2026-08-19 - so the parser works from the parts rather than the
    * length, and a two-digit year is read as this century. */
  def nesoDate(s: String): LocalDate = {
    val p = s.trim.take(11).split("-")
    require(p.length == 3, s"thermo: cannot read NESO date '$s'")
    if (p(0).length == 4) LocalDate.of(p(0).toInt, p(1).toInt, p(2).toInt)
    else {
      val m = MONTHS.indexOf(p(1).toUpperCase) + 1
      require(m > 0, s"thermo: cannot read NESO month in '$s'")
      val y = p(2).toInt
      LocalDate.of(if (y < 100) 2000 + y else y, m, p(0).toInt)
    }
  }

  def gbLoad(): Map[LocalDate, Double] = {
    val acc = collection.mutable.Map[LocalDate, (Double, Int)]()
    for ((y, path) <- NESO) {
      val txt = cachedGet(
        "https://api.neso.energy/dataset/8f2fe0af-871c-488d-8bad-960426f24601/resource/" + path,
        cache / s"neso-demand-$y.csv")
      val lines = txt.linesIterator.filter(_.trim.nonEmpty).toArray
      // 2025 quotes every field, the other years quote none.
      def cell(x: String) = x.trim.stripPrefix("\"").stripSuffix("\"")
      val hdr = lines.head.stripPrefix("﻿").split(",").map(cell)
      val Seq(iD, iN, iW, iS) = Seq("SETTLEMENT_DATE", "ND",
        "EMBEDDED_WIND_GENERATION", "EMBEDDED_SOLAR_GENERATION").map(c => hdr.indexOf(c))
      require(Seq(iD, iN, iW, iS).forall(_ >= 0), s"thermo: NESO $y is missing a column")
      // The in-year file carries forecast rows alongside actuals; every row is
      // "A" today, but this file is refetched, so the filter is stated rather
      // than assumed. The older files have no such column and keep every row.
      val iF = hdr.indexOf("FORECAST_ACTUAL_INDICATOR")
      for (line <- lines.tail) {
        val a = line.split(",", -1)
        if (a.length > Seq(iD, iN, iW, iS, iF).max && (iF < 0 || cell(a(iF)) == "A")) {
          val d = nesoDate(cell(a(iD)))
          val mw = cell(a(iN)).toDouble + cell(a(iW)).toDouble + cell(a(iS)).toDouble
          val (s, n) = acc.getOrElse(d, (0.0, 0)); acc(d) = (s + mw, n + 1)
        }
      }
    }
    acc.collect { case (d, (s, n)) if n >= 40 => d -> s / n / 1000.0 }.toMap
  }

  /** Ordinary least squares of load on temperature, with the standard error of
    * the slope, so the text can say whether an arm is real. */
  def slope(pts: Seq[(Double, Double)]): (Double, Double) = {
    val n = pts.size
    val mx = pts.map(_._1).sum / n; val my = pts.map(_._2).sum / n
    val sxx = pts.map(p => (p._1 - mx) * (p._1 - mx)).sum
    val b = pts.map(p => (p._1 - mx) * (p._2 - my)).sum / sxx
    val a = my - b * mx
    val sse = pts.map { case (x, y) => val e = y - (a + b * x); e * e }.sum
    (b, math.sqrt(sse / (n - 2) / sxx))
  }

  val scatter = new StringBuilder; scatter ++= "country,date,temp_c,load_gw\n"
  val fits = new StringBuilder
  fits ++= "country,mean_load_gw,n_days,t_heat,t_cool," +
           "heat_gw_per_c,heat_se,heat_n,heat_pct,cool_gw_per_c,cool_se,cool_n,cool_pct\n"

  for (l <- LANDS) {
    val temp = dailyTemp(l)
    val load = if (l.key == "uk") gbLoad() else entsoeLoad(l)
    // Weekdays only, August dropped, and only days where both series exist.
    val days = temp.keySet.intersect(load.keySet).toSeq.sorted.filter { d =>
      !d.isBefore(FROM) && !d.isAfter(TO) &&
        d.getDayOfWeek.getValue <= DayOfWeek.FRIDAY.getValue && d.getMonth != Month.AUGUST
    }
    require(days.size >= 800, s"thermo: only ${days.size} usable days for ${l.key}")
    for (d <- days) scatter ++= f"${l.label},$d,${temp(d)}%.2f,${load(d)}%.3f\n"

    val pts = days.map(d => (temp(d), load(d)))
    val mean = pts.map(_._2).sum / pts.size
    val cold = pts.filter(_._1 < T_HEAT)
    val hot  = pts.filter(_._1 > T_COOL)
    require(cold.size >= 30, s"thermo: ${l.key} has only ${cold.size} days below $T_HEAT C")
    val (bh, sh) = slope(cold)
    val (bc, sc) = if (hot.size >= 30) slope(hot) else (Double.NaN, Double.NaN)
    // The heating arm is reported per degree COLDER, so its sign is flipped.
    fits ++= f"${l.label},$mean%.2f,${days.size},$T_HEAT%.0f,$T_COOL%.0f," +
             f"${-bh}%.4f,$sh%.4f,${cold.size},${-100 * bh / mean}%.3f," +
             f"$bc%.4f,$sc%.4f,${hot.size},${100 * bc / mean}%.3f\n"
    println(f"  ${l.label}%-14s mean ${mean}%5.1f GW over ${days.size}%4d days   " +
            f"colder ${-bh}%6.3f +-$sh%.3f GW/C (${-100 * bh / mean}%4.2f%%, n=${cold.size}%4d)   " +
            f"hotter $bc%6.3f +-$sc%.3f GW/C (${100 * bc / mean}%5.2f%%, n=${hot.size}%4d)")
  }
  os.write.over(dir / "thermosensitivity.csv", scatter.toString)
  os.write.over(dir / "thermosensitivity-fit.csv", fits.toString)
  println("wrote data-refresh/thermosensitivity.csv and thermosensitivity-fit.csv")
  println("render:")
  println("  uv run figures/thermosensitivity.py data-refresh/thermosensitivity.csv " +
          "data-refresh/thermosensitivity-fit.csv without-hot-air/Images/fig-thermosensitivity.svg")
}

// ---- Appendix A: the steady-speed curves, with 2025 vehicles in them ----
// Figure A.9a, which updates his figures A.9, A.10 and A.11 in one panel each.
// The model is the one those figures are drawn from and the caption states:
// at a steady speed there is no stop-start term, so the energy per unit
// distance is the air resistance plus the rolling resistance, divided by the
// efficiency of whatever is driving the wheels.
//
// His vehicles, from his own captions:
//   car    efficiency 0.25, cdA 1.00 m2, 1000 kg, Crr 0.01
//   bike   efficiency 0.25, cdA 0.75 m2,   90 kg, Crr 0.005
//   train  efficiency 0.90, cdA 11.0 m2, 400 t,   Crr 0.002, 584 passengers
//
// The vehicles added here, and where their numbers come from:
//   an electric car - efficiency 0.85 from battery to wheels rather than 0.25,
//     cdA 0.60 m2 for a modern saloon, 1800 kg with its pack;
//   an electric sport-utility - the same drivetrain in cdA 0.90 and 2400 kg,
//     which is the size effect this appendix's 2026 section is about;
//   an electric bicycle - the rider's 0.25 replaced by a motor at 0.75, and
//     25 kg of motor and battery added;
//   his own train at a realistic load rather than full, because a train's
//     energy per passenger is mostly a question of how many are aboard.
//
// Two checks are printed. His own curves must come back where he drew them,
// and the modern ones must land near what modern vehicles are measured to use.
@main
def chapterASteadySpeed(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val rho = 1.3; val g = 9.81

  // (panel, label, drag-area, mass, rolling resistance, efficiency, occupants)
  val vehicles = Seq(
    ("car",   "Petrol car (MacKay's)",      1.00,   1000.0, 0.01,  0.25,   1),
    ("car",   "Electric car 2025",        0.60,   1800.0, 0.01,  0.85,   1),
    ("car",   "Electric SUV 2025",        0.90,   2400.0, 0.01,  0.85,   1),
    ("bike",  "Bicycle (MacKay's)",         0.75,     90.0, 0.005, 0.25,   1),
    ("bike",  "Electric bicycle",          0.75,    115.0, 0.005, 0.75,   1),
    ("train", "Train full (584 seats)",  11.00, 400000.0, 0.002, 0.90, 584),
    ("train", "Train at 40% of seats",          11.00, 400000.0, 0.002, 0.90, 234))

  /** kWh per 100 km, per occupant, at a steady speed in km/h. */
  def cost(cdA: Double, mass: Double, crr: Double, eff: Double, people: Int, kmh: Double) = {
    val v = kmh / 3.6
    val newtons = 0.5 * rho * cdA * v * v + crr * mass * g
    newtons / eff * 1e5 / 3.6e6 / people
  }

  val out = new StringBuilder; out ++= "panel,label,kmh,kwh_per_100km\n"
  for ((panel, label, cdA, mass, crr, eff, people) <- vehicles) {
    val top = if (panel == "bike") 45 else if (panel == "train") 300 else 160
    for (kmh <- 5 to top by 5)
      out ++= f"$panel,$label,$kmh,${cost(cdA, mass, crr, eff, people, kmh)}%.3f\n"
  }
  os.write.over(dir / "steady-speed.csv", out.toString)
  println("wrote data-refresh/steady-speed.csv")

  println("at a steady speed, kWh per 100 km per occupant:")
  for ((panel, label, cdA, mass, crr, eff, people) <- vehicles) {
    val at = if (panel == "bike") Seq(15.0, 25.0) else if (panel == "train") Seq(160.0, 200.0)
             else Seq(50.0, 110.0)
    println(f"  $panel%-6s $label%-26s " +
            at.map(s => f"${s.toInt}%3d km/h: ${cost(cdA, mass, crr, eff, people, s)}%6.2f").mkString("   "))
  }
  // MacKay's petrol car at 110 km/h is the 80 kWh/100 km this book is built on.
  val his = cost(1.00, 1000.0, 0.01, 0.25, 1, 110)
  require(his > 60 && his < 90, s"chapterASteadySpeed: his car gives $his kWh/100 km at 110 km/h")
  println(f"  check: his car at 110 km/h is $his%.0f kWh/100 km, against the book's 80")
  println("render:")
  println("  uv run figures/steady_speed.py data-refresh/steady-speed.csv " +
          "without-hot-air/Images/fig-steady-speed.svg")
}

// ---- Appendix A: measured fuel economy against steady speed ----
// Figure A.12a. MacKay's figure A.12 plots one Prius and one BMW against a
// speed-squared curve to show that real consumption does not follow v^2. His
// two sources have decayed - the Prius page is gone and the BMW page publishes
// pictures rather than numbers - but a better source exists and is free.
//
// Oak Ridge National Laboratory's Transportation Energy Data Book, edition 40
// (2022), tables 4.34 and 4.33, collect dynamometer measurements of fuel
// economy at steady speeds from four studies spanning forty years, plus
// Argonne's Autonomie model results for model year 2016. The values here are
// hand-entered from those two tables, and the note in the chapter says so.
//
// Everything is converted to this book's units. A US gallon of petrol is
// 33.7 kWh on the EPA's own convention, and 100 km is 62.137 miles, so
// kWh per 100 km = 33.7 * 62.137 / mpg = 2094 / mpg.
@main
def chapterAFuelVsSpeed(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val kwhPerGallon = 33.7; val milesPer100km = 100 / 1.609344
  def kwh(mpg: Double) = kwhPerGallon * milesPer100km / mpg

  // TEDB table 4.34: fuel economy in mpg at speeds in mph, by study.
  val studies = Seq(
    ("1973 study (13 cars)", Seq(30 -> 21.1, 35 -> 21.1, 40 -> 21.1, 45 -> 20.3, 50 -> 19.5,
                                 55 -> 18.5, 60 -> 17.5, 65 -> 16.2, 70 -> 14.9)),
    ("1984 study (15 cars)", Seq(15 -> 21.1, 20 -> 25.5, 25 -> 30.0, 30 -> 31.8, 35 -> 33.6,
                                 40 -> 33.6, 45 -> 33.5, 50 -> 31.9, 55 -> 30.3, 60 -> 27.6,
                                 65 -> 24.9, 70 -> 22.5, 75 -> 20.0)),
    ("1997 study (9 cars)",  Seq(15 -> 24.4, 20 -> 27.9, 25 -> 30.5, 30 -> 31.7, 35 -> 31.2,
                                 40 -> 31.0, 45 -> 31.6, 50 -> 32.4, 55 -> 32.4, 60 -> 31.4,
                                 65 -> 29.2, 70 -> 26.8, 75 -> 24.8)),
    ("2012 study (74 cars)", Seq(40 -> 33.2, 50 -> 31.9, 60 -> 27.9, 70 -> 24.1, 80 -> 20.5)),
    // TEDB table 4.33, Argonne's Autonomie model for model year 2016.
    ("2016 model: midsize car", Seq(45 -> 43.0, 55 -> 45.0, 65 -> 38.0, 75 -> 32.0)),
    ("2016 model: large SUV",   Seq(45 -> 35.0, 55 -> 31.0, 65 -> 29.0, 75 -> 25.0)),
    ("2016 model: hybrid car",  Seq(45 -> 55.0, 55 -> 46.0, 65 -> 38.0, 75 -> 33.0)))

  val out = new StringBuilder; out ++= "series,kmh,mpg,kwh_per_100km\n"
  for ((label, points) <- studies; (mph, mpg) <- points)
    out ++= f"$label,${mph * 1.609344}%.1f,$mpg%.1f,${kwh(mpg)}%.1f\n"
  os.write.over(dir / "fuel-vs-speed.csv", out.toString)
  println("wrote data-refresh/fuel-vs-speed.csv")

  // The book's own quantity, and MacKay's claim: is this a square law?
  for ((label, points) <- studies) {
    val at = points.toMap
    val best = points.minBy(p => kwh(p._2))
    println(f"$label%-26s best ${best._1 * 1.609344}%5.0f km/h at ${kwh(best._2)}%5.1f kWh/100 km; " +
            points.filter(p => Seq(50, 70).contains(p._1))
                  .map(p => f"${p._1 * 1.609344}%.0f km/h ${kwh(p._2)}%.0f").mkString(", "))
    if (at.contains(50) && at.contains(70))
      println(f"    50 to 70 mph: consumption up ${(kwh(at(70)) / kwh(at(50)) - 1) * 100}%4.1f%%, " +
              f"a square law would give ${(math.pow(70.0 / 50, 2) - 1) * 100}%.0f%%")
  }
  // TEDB prints the 2012 study's own 50-70 mph loss as 24.5%; reproduce it.
  val s2012 = studies.find(_._1.startsWith("2012")).get._2.toMap
  val loss = 1 - s2012(70) / s2012(50)
  require(math.abs(loss - 0.245) < 0.01,
    f"chapterAFuelVsSpeed: 50-70 mph loss is $loss%.3f, TEDB table 4.34 says 0.245")
  println(f"  check: the 2012 study's 50-70 mph fuel-economy loss is ${loss * 100}%.1f%%, " +
          "against the 24.5% printed in TEDB table 4.34")
  println("render:")
  println("  uv run figures/fuel_vs_speed.py data-refresh/fuel-vs-speed.csv " +
          "without-hot-air/Images/fig-a12-fuel-speed.svg")
}

// ---- Appendix A: power against top speed, on 2020s cars ----
// Figure A.13a. MacKay's figure A.13 is Tennekes' scatter of engine power
// against top speed, and its caption states the law: power goes as the cube of
// speed. The scatter itself is from a 1997 book and cannot be redrawn, but the
// law can be tested on today's cars, because one country publishes the numbers.
//
// The Dutch vehicle authority RDW puts its whole register online. Two open
// datasets are used here: the vehicle register (m9d7-ebf2), which carries the
// type-approval maximum design speed in km/h, and the fuel register
// (8ys7-d773), which carries maximum net power in kW - a different column for
// combustion (nettomaximumvermogen) and for electric drive
// (netto_max_vermogen_elektrisch).
//
// One car is taken per make and model, from models with at least a hundred
// registrations first admitted since 2023, and only ordinary body types, so
// the sample is cars people actually bought rather than one-off imports.
@main
def chapterAPowerVsTopSpeed(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val cache = dir / "rdw"; os.makeDir.all(cache)
  def enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

  val where = "voertuigsoort='Personenauto' AND maximale_constructiesnelheid > 80 " +
    "AND datum_eerste_toelating > 20230000 " +
    "AND inrichting in ('hatchback','sedan','stationwagen','MPV','coupe','cabriolet')"
  val modelsUrl = "https://opendata.rdw.nl/resource/m9d7-ebf2.json?" +
    s"$$select=${enc("merk,handelsbenaming,min(kenteken) as kenteken," +
      "avg(maximale_constructiesnelheid) as vmax,count(*) as n")}" +
    s"&$$group=${enc("merk,handelsbenaming")}&$$where=${enc(where)}&$$limit=6000"
  val models = ujson.read(cachedGet(modelsUrl, cache / "models.json")).arr
    .filter(m => m.obj.contains("vmax") && m("n").str.toInt >= 100)
  println(s"${models.length} models with a hundred registrations or more since 2023")

  // Power comes from the fuel register, a hundred registrations at a time.
  val power = scala.collection.mutable.Map[String, (Set[String], Double)]()
  val fields = "kenteken,brandstof_omschrijving,nettomaximumvermogen," +
    "netto_max_vermogen_elektrisch,nominaal_continu_maximumvermogen"
  for ((batch, i) <- models.map(_("kenteken").str).grouped(100).zipWithIndex) {
    val url = "https://opendata.rdw.nl/resource/8ys7-d773.json?" +
      s"$$select=${enc(fields)}&$$where=${enc(batch.map(k => s"'$k'").mkString("kenteken in (", ",", ")"))}" +
      "&$limit=500"
    for (r <- ujson.read(cachedGet(url, cache / f"fuel-$i%02d.json")).arr) {
      val kw = Seq("nettomaximumvermogen", "netto_max_vermogen_elektrisch",
                   "nominaal_continu_maximumvermogen")
        .flatMap(f => r.obj.get(f).flatMap(_.str.toDoubleOption))
      val (fuels, best) = power.getOrElse(r("kenteken").str, (Set.empty[String], 0.0))
      power(r("kenteken").str) =
        (fuels + r.obj.get("brandstof_omschrijving").map(_.str).getOrElse(""),
         (best +: kw).max)
    }
  }

  val out = new StringBuilder; out ++= "make,model,kind,vmax_kmh,power_kw,registrations\n"
  var rows = 0
  for (m <- models; (fuels, kw) <- power.get(m("kenteken").str) if kw > 0) {
    val kind = if (fuels == Set("Elektriciteit")) "Electric"
               else if (fuels.contains("Elektriciteit")) "Hybrid" else "Combustion"
    // A few model names carry commas, which would shift the CSV's columns.
    val clean = (s: String) => s.replace(",", " ").trim
    out ++= f"${clean(m("merk").str)},${clean(m("handelsbenaming").str)},$kind," +
            f"${m("vmax").str.toDouble}%.0f,$kw%.0f,${m("n").str}\n"
    rows += 1
  }
  os.write.over(dir / "power-vs-topspeed.csv", out.toString)
  println(s"wrote data-refresh/power-vs-topspeed.csv ($rows models)")

  // The law, fitted where Tennekes drew it: log power against log top speed.
  case class Car(kind: String, v: Double, kw: Double)
  val cars = os.read.lines(dir / "power-vs-topspeed.csv").drop(1).map(_.split(","))
    .map(f => Car(f(2), f(3).toDouble, f(4).toDouble))
  def exponent(sub: Seq[Car]) = {
    val xs = sub.map(c => math.log(c.v)); val ys = sub.map(c => math.log(c.kw))
    val mx = xs.sum / xs.length; val my = ys.sum / ys.length
    xs.zip(ys).map { case (x, y) => (x - mx) * (y - my) }.sum / xs.map(x => math.pow(x - mx, 2)).sum
  }
  for (kind <- Seq("Combustion", "Hybrid", "Electric")) {
    val sub = cars.filter(_.kind == kind)
    println(f"  $kind%-11s n=${sub.length}%3d  power goes as speed to the ${exponent(sub)}%4.2f")
  }
  println(f"  all together n=${cars.length}%3d  exponent ${exponent(cars)}%4.2f")
  println(f"  ${cars.count(_.v == 250)}%d models declare exactly 250 km/h, the German limiter")
  val burn = cars.filter(_.kind == "Combustion")
  require(exponent(burn) > 2.4 && exponent(burn) < 3.6,
    s"chapterAPowerVsTopSpeed: combustion exponent ${exponent(burn)}, Tennekes says about 3")
  println("render:")
  println("  uv run figures/power_vs_topspeed.py data-refresh/power-vs-topspeed.csv " +
          "without-hot-air/Images/fig-a13-power-speed.svg")
}

// ---- Appendices B and C: two more of MacKay's model curves ----
// Figures B.7a and C.5a. Both originals are small bitmaps from the 2008 EPUB,
// and both are curves of formulas printed in their own chapters, so both can
// be recomputed rather than squinted at.
//
// B.7 is wind speed against height under two standard shear formulas, with the
// speed pinned to 6 m/s at 10 m, and the power density that follows from each.
// The Danish Wind Industry Association's is logarithmic with a roughness
// length z0 of 0.1 m; the National Renewable Energy Laboratory's is a power
// law, conventionally with an exponent of a seventh.
//
// C.5 is the thrust a plane needs against its speed: ordinary drag, which
// grows as the square of speed, plus the drag that comes with making lift,
// which falls as the square of it. Every number it needs is printed in the
// book, and in the figure's own caption rather than in table C.6: a 747 of
// 319 tonnes, wingspan 64.4 m, drag coefficient 0.03 and frontal area 180 m2,
// in air of 0.41 kg/m3, which is the density at ten kilometres. The area of
// the sausage of air the wings throw down is the square of the wingspan,
// which figure C.7's caption states.
//
// Table C.6 gives the same plane a fully-laden 363 tonnes and air of 0.4, and
// the chapter's text uses those to get a minimum thrust of 130 kN. The figure
// is drawn from the figure's own numbers, so it comes out at 113 kN, and the
// chapter's note records that the book disagrees with itself about the mass.
@main
def appendixBCModelCurves(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val rho = 1.3

  // --- B.7: wind against height ---
  val (vRef, zRef, z0, shear) = (6.0, 10.0, 0.1, 1.0 / 7)
  def dwia(z: Double) = vRef * math.log(z / z0) / math.log(zRef / z0)
  def nrel(z: Double) = vRef * math.pow(z / zRef, shear)
  val wind = new StringBuilder; wind ++= "model,height_m,speed_ms,power_density_wm2\n"
  for (model <- Seq("DWIA", "NREL"); z <- Seq.range(10, 401, 2).map(_.toDouble)) {
    val v = if (model == "DWIA") dwia(z) else nrel(z)
    wind ++= f"$model,$z%.0f,$v%.3f,${0.5 * rho * v * v * v}%.1f\n"
  }
  // Check before writing: a failed invariant should leave no file behind for
  // a figure script to render happily from.
  require(math.abs(dwia(zRef) - vRef) < 1e-9 && math.abs(nrel(zRef) - vRef) < 1e-9,
    "appendixBCModelCurves: both formulas must give 6 m/s at 10 m")
  os.write.over(dir / "wind-height.csv", wind.toString)
  println("wrote data-refresh/wind-height.csv")
  for (z <- Seq(10.0, 50.0, 100.0, 200.0))
    println(f"  at $z%3.0f m: DWIA ${dwia(z)}%5.2f m/s (${0.5 * rho * math.pow(dwia(z), 3)}%5.0f W/m2), " +
            f"NREL ${nrel(z)}%5.2f m/s (${0.5 * rho * math.pow(nrel(z), 3)}%5.0f W/m2)")

  // --- C.5: the thrust a jumbo jet needs ---
  val mass = 319000.0; val g = 9.81; val rhoAir = 0.41; val ap = 180.0; val cd = 0.03
  val wingspan = 64.4; val as = wingspan * wingspan   // figure C.7: a square of the wingspan
  def drag(v: Double) = 0.5 * cd * rhoAir * ap * v * v
  def lift(v: Double) = 0.5 * math.pow(mass * g, 2) / (rhoAir * v * v * as)
  // One grid for the file and for the optimum, so the number asserted here is
  // the number the figure and the captions publish.
  val speeds = Seq.range(100, 401, 2).map(_.toDouble)
  val plane = new StringBuilder; plane ++= "speed_ms,drag_kn,lift_kn,total_kn\n"
  for (v <- speeds)
    plane ++= f"$v%.0f,${drag(v) / 1000}%.2f,${lift(v) / 1000}%.2f,${(drag(v) + lift(v)) / 1000}%.2f\n"
  val best = speeds.minBy(v => drag(v) + lift(v))
  val closed = math.sqrt(cd * ap / as) * mass * g / 1000     // his own (cd fA)^1/2 mg
  println(f"  sausage area $as%.0f m2, the square of a $wingspan%.1f m wingspan")
  println(f"  optimum $best%.0f m/s at ${(drag(best) + lift(best)) / 1000}%.0f kN, " +
          f"against the 220 m/s his caption states")
  println(f"  his closed form (cd Ap / As)^1/2 mg gives $closed%.0f kN at this mass; " +
          f"the chapter's 130 kN uses table C.6's fully-laden 363 t")
  // Two checks with content: the curve's minimum must match his own algebra,
  // and the speed must be the one his caption prints.
  require(math.abs((drag(best) + lift(best)) / 1000 - closed) < 1.0,
    s"appendixBCModelCurves: minimum ${(drag(best) + lift(best)) / 1000} kN against his formula's $closed")
  require(math.abs(best - 220) <= 10,
    s"appendixBCModelCurves: optimum at $best m/s, his caption says 220")
  os.write.over(dir / "plane-thrust.csv", plane.toString)
  println("wrote data-refresh/plane-thrust.csv")
  println("render:")
  println("  uv run figures/wind_height.py data-refresh/wind-height.csv " +
          "without-hot-air/Images/fig-b7-wind-height.svg")
  println("  uv run figures/plane_thrust.py data-refresh/plane-thrust.csv " +
          "without-hot-air/Images/fig-c5-thrust.svg")
}

// ---- Chapter P: what waste heat alone does to a growing civilisation ----
// Figure P.1. Thomas Murphy's chapter 1 adds a growing human power source to
// the sunlight the Earth already absorbs and solves the same equilibrium
// equation this book uses in chapter 1: in goes 0.707 x 1360 W/m2 over the
// planet's projected disk, out goes sigma T^4 over its whole surface.
//
// Today's 18 TW is 0.14 W/m2 over that disk. At 2.3% a year - a factor of ten
// a century, which is what the last few centuries have actually done - that
// term grows until it dwarfs the Sun's.
//
// His table 1.4 is the check. It prints 288.1 K at a hundred years, 288.9 at
// two hundred, 296.9 at three, 344 at four, and 373 - water boiling - at 417.
// The recompute reproduces all of those but the 400-year row, where the same
// equation gives 352 K rather than his 344; the chapter's note says so.
@main
def chapterPWasteHeat(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val solar = 0.707 * 1360.0      // absorbed, per square metre of projected disk
  val sigma = 5.67e-8
  val greenhouse = 33.0           // the observed 288 K less the bare 255 K
  val today = 0.14                // 18 TW over the projected disk, in W/m2

  // Murphy works in one-century chunks at "a factor of ten each century",
  // which is what he calls 2.3% a year; compounding 1.023 exactly would give
  // 9.73 per century and would not reproduce his table.
  def waste(years: Double) = today * math.pow(10, years / 100)
  def temperature(years: Double) =
    math.pow((solar + waste(years)) / (4 * sigma), 0.25) + greenhouse

  val out = new StringBuilder; out ++= "years,waste_wm2,temperature_k\n"
  for (y <- 0 to 500 by 5)
    out ++= f"$y,${waste(y)}%.4f,${temperature(y)}%.2f\n"
  os.write.over(dir / "waste-heat.csv", out.toString)
  println("wrote data-refresh/waste-heat.csv")

  println("against Murphy's table 1.4:")
  val his = Seq((100.0, 288.1), (200.0, 288.9), (300.0, 296.9), (400.0, 344.0), (417.0, 373.0))
  for ((y, k) <- his)
    println(f"  $y%5.0f years: ${waste(y)}%9.1f W/m2, ${temperature(y)}%6.1f K " +
            f"(his table says $k%.1f)")
  // Four of his five rows, including the boiling one, must come back.
  for ((y, k) <- his if y != 400)
    require(math.abs(temperature(y) - k) < 1.5,
      f"chapterPWasteHeat: at $y years the equation gives ${temperature(y)}%.1f K, his table $k%.1f")
  println(f"  water boils at ${(1 to 600).find(y => temperature(y) >= 373).get}%d years")
  println("render:")
  println("  uv run figures/waste_heat.py data-refresh/waste-heat.csv " +
          "without-hot-air/Images/fig-p1-waste-heat.svg")
}

// ---- Charts: the marks on the two translation charts ----
// MacKay's printed charts carry marks for 2004 and 1990. The interactive
// versions carry those and today's as well, and today's are computed here
// rather than typed in: energy per person from the same Energy Institute
// series that chapter L's explorer uses, and CO2 per person from the Our
// World in Data file the emissions figures are drawn from.
//
// The output is a small JSON the charts page fetches, so that a refresh of
// the data moves the marks without anyone editing the page.
@main
def chartsMarks(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val out = os.pwd / "book" / "assets" / "chart-marks.json"
  val energyFile = os.pwd / "book" / "assets" / "tes-percapita.parquet"
  val co2File = os.pwd / "data-refresh" / "owid-co2-per-capita.csv"
  require(os.exists(energyFile) && os.exists(co2File),
    "chartsMarks: needs tes-percapita.parquet and owid-co2-per-capita.csv")

  val wanted = Seq("US" -> "United States", "Sweden" -> "Sweden", "China" -> "China",
                   "United Kingdom" -> "United Kingdom", "World" -> "World", "India" -> "India")
  val energy = withConn { c =>
    val year = {
      val rs = c.createStatement().executeQuery(
        s"select max(year) from read_parquet('${energyFile}')")
      rs.next(); rs.getInt(1)
    }
    val rows = scala.collection.mutable.LinkedHashMap[String, Double]()
    for ((key, label) <- wanted) {
      val st = c.prepareStatement(
        s"select kwh_per_day from read_parquet('${energyFile}') where region = ? and year = ?")
      st.setString(1, key); st.setInt(2, year)
      val rs = st.executeQuery()
      if (rs.next()) rows(label) = rs.getDouble(1)
    }
    (year, rows)
  }
  println(s"energy per person, ${energy._1}, kWh/d:")
  for ((k, v) <- energy._2) println(f"  $k%-16s $v%6.1f")

  // CO2 per person: the latest year the file has for every country wanted.
  val co2rows = os.read.lines(co2File).drop(1).map(_.split(",", -1))
    .filter(f => f.length >= 4 && wanted.map(_._2).contains(f(0)))
  val co2Year = wanted.map(_._2).map(c => co2rows.filter(_(0) == c).map(_(2).toInt).max).min
  val co2 = wanted.map(_._2).map(c =>
    c -> co2rows.find(f => f(0) == c && f(2).toInt == co2Year).map(_(3).toDouble).getOrElse(0.0))
  println(s"CO2 per person, $co2Year, tonnes:")
  for ((k, v) <- co2) println(f"  $k%-16s $v%6.2f")
  require(co2.forall(_._2 > 0), "chartsMarks: a country is missing a CO2 value")

  val q = (s: String) => "\"" + s + "\""
  val json = new StringBuilder
  json ++= "{\n"
  json ++= s"  ${q("energyYear")}: ${energy._1},\n  ${q("co2Year")}: $co2Year,\n"
  json ++= s"  ${q("energy")}: {" +
    energy._2.map { case (k, v) => f"${q(k)}: $v%.1f" }.mkString(", ") + "},\n"
  json ++= s"  ${q("co2")}: {" +
    co2.map { case (k, v) => f"${q(k)}: $v%.2f" }.mkString(", ") + "}\n"
  json ++= "}\n"
  os.write.over(out, json.toString)
  println(s"wrote book/assets/chart-marks.json")
  println(f"  scales must reach ${energy._2.values.max}%.0f kWh/d/p and ${co2.map(_._2).max}%.1f tCO2/y/p")
}

// ---- Chapter Q: the IPCC's scenarios in this book's units ----
// Figure Q.1. The Sixth Assessment Report's working group III gives its
// pathways as global totals of greenhouse gas, in gigatonnes of CO2-equivalent
// a year. This book works in per person quantities, so they are converted.
//
// The pathways are not read off the printed Table SPM.2 but computed from the
// ensemble underneath it: the AR6 Scenarios Database's metadata indicators,
// version 1.1, vetted set, 1202 scenarios, cut to the columns used here. That
// the medians come back as the table prints them is the check.
//
// Population is the United Nations' medium projection as published by Our
// World in Data, cut to the world row.
@main
def chapterQPathways(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  val pop = os.read.lines(dir / "world-population-projected.csv").drop(1)
    .map(_.split(",")).map(f => f(0).toInt -> f(1).toDouble).toMap

  case class Row(category: String, cuts: Map[Int, Double], netZero: Option[Double],
                 peak: Option[Double])
  val lines = os.read.lines(dir / "ar6-scenarios-meta.csv")
  val head = lines.head.split(",").zipWithIndex.toMap
  def num(f: Array[String], k: String) = f.lift(head(k)).flatMap(_.trim.toDoubleOption)
  val all = lines.drop(1).map { l =>
    val f = l.split(",", -1)
    // A comma inside a model or scenario name would shift every column, which
    // is how a category of 4182 C once appeared in this table.
    require(f.length == head.size,
      s"ar6-scenarios-meta.csv: a row has ${f.length} fields against ${head.size} in the header: $l")
    require(f(head("category")).matches("C[1-8]"),
      s"ar6-scenarios-meta.csv: '${f(head("category"))}' is not a category")
    Row(f(head("category")),
        Seq(2030, 2040, 2050).flatMap(y => num(f, s"cut_$y").map(y -> _)).toMap,
        num(f, "netzero_co2"), num(f, "peak_warming"))
  }
  println(s"${all.length} vetted scenarios: " +
    all.groupBy(_.category).toSeq.sortBy(_._1).map { case (c, rs) => s"$c ${rs.length}" }.mkString(", "))

  def pct(xs: Seq[Double], p: Double) = {
    require(xs.nonEmpty, "chapterQPathways: asked for a percentile of nothing")
    val s = xs.sorted; s(math.min(s.length - 1, (p * s.length).toInt))
  }
  def median(xs: Seq[Double]) = pct(xs, 0.5)
  val base = 55.0; val baseYear = 2019     // the report's own modelled 2019 total
  val gramsPerKwh = 250.0                  // the book's chemical exchange rate
  def perPerson(gt: Double, year: Int) = gt * 1e9 / pop(year)
  def asFuel(t: Double) = t * 1e6 / gramsPerKwh / 365

  // What Table SPM.2 prints, to check the ensemble against.
  val printed = Map(("C1", 2030) -> 43.0, ("C1", 2040) -> 69.0, ("C1", 2050) -> 84.0,
                    ("C3", 2030) -> 21.0, ("C3", 2040) -> 46.0, ("C3", 2050) -> 64.0)

  val out = new StringBuilder
  out ++= "category,year,gt_total,gt_p5,gt_p95,people,t_per_person,t_p5,t_p95,kwh_per_day\n"
  out ++= f"history,$baseYear,$base%.1f,53.0,58.0,${pop(baseYear)}%.0f," +
          f"${perPerson(base, baseYear)}%.3f,${perPerson(53, baseYear)}%.3f," +
          f"${perPerson(58, baseYear)}%.3f,${asFuel(perPerson(base, baseYear))}%.1f\n"
  for (cat <- Seq("C1", "C3"); year <- Seq(2030, 2040, 2050)) {
    val cuts = all.filter(_.category == cat).flatMap(_.cuts.get(year))
    val level = (p: Double) => base * (1 - pct(cuts, p) / 100)
    val (med, lo, hi) = (level(0.5), level(0.95), level(0.05))   // a deeper cut is a lower level
    out ++= f"$cat,$year,$med%.1f,$lo%.1f,$hi%.1f,${pop(year)}%.0f,${perPerson(med, year)}%.3f," +
            f"${perPerson(lo, year)}%.3f,${perPerson(hi, year)}%.3f,${asFuel(perPerson(med, year))}%.1f\n"
    val cut = median(cuts)
    println(f"  $cat $year: cut ${cut}%4.1f%% (table prints ${printed((cat, year))}%.0f), " +
            f"${med}%4.1f Gt, ${perPerson(med, year)}%5.2f t per person, " +
            f"${asFuel(perPerson(med, year))}%5.1f kWh/d as fuel; " +
            f"per person the cut is ${(1 - perPerson(med, year) / perPerson(base, baseYear)) * 100}%4.0f%%")
    require(math.abs(cut - printed((cat, year))) <= 1.5,
      f"chapterQPathways: $cat $year gives ${cut}%.1f%% from the ensemble, Table SPM.2 prints ${printed((cat, year))}%.0f%%")
  }
  os.write.over(dir / "ar6-pathways.csv", out.toString)
  println("wrote data-refresh/ar6-pathways.csv")
  println("  every median above is within 1.5 points of the report's own printed table")

  // The whole ladder, which the chapter prints as a table.
  val ladder = new StringBuilder; ladder ++= "category,pathways,cut_2050,t_per_person_2050,kwh_per_day,net_zero_co2,peak_warming\n"
  println("category  n     2050 cut  t/person  kWh/d  net zero CO2  peak warming")
  val perCategory = scala.collection.mutable.LinkedHashMap[String, Double]()
  for ((cat, rs) <- all.groupBy(_.category).toSeq.sortBy(_._1)) {
    val cuts = rs.flatMap(_.cuts.get(2050))
    val level = base * (1 - median(cuts) / 100)
    val t = perPerson(level, 2050)
    perCategory(cat) = t
    val nz = rs.flatMap(_.netZero)
    val nzStr = if (nz.isEmpty || nz.length * 2 < rs.length) "" else f"${median(nz)}%.0f"
    ladder ++= f"$cat,${rs.length},${median(cuts)}%.1f,$t%.2f,${asFuel(t)}%.1f,$nzStr,${median(rs.flatMap(_.peak))}%.2f\n"
    println(f"  $cat%-3s ${rs.length}%4d  ${median(cuts)}%8.0f%%  $t%7.2f t  ${asFuel(t)}%5.1f  " +
            f"${if (nzStr.isEmpty) "   none" else nzStr}%8s  ${median(rs.flatMap(_.peak))}%8.2f C")
  }
  // The chapter transcribes this table, so the rows it prints are checked
  // here against the very figures just written into it: a refresh that moves
  // them fails rather than leaving the chapter quietly wrong.
  val printedLadder = Map("C1" -> 0.90, "C2" -> 1.41, "C3" -> 2.02, "C4" -> 2.91,
                          "C5" -> 4.05, "C6" -> 5.42, "C7" -> 7.06, "C8" -> 8.31)
  require(perCategory.keySet == printedLadder.keySet,
    s"chapterQPathways: the ensemble has categories ${perCategory.keys.mkString(" ")}, " +
    s"chapter Q prints ${printedLadder.keys.toSeq.sorted.mkString(" ")}")
  for ((cat, t) <- perCategory)
    require(math.abs(t - printedLadder(cat)) < 0.02,
      f"chapterQPathways: $cat is now $t%.2f t per person in 2050; chapter Q prints ${printedLadder(cat)}%.2f")
  os.write.over(dir / "ar6-ladder.csv", ladder.toString)
  println("wrote data-refresh/ar6-ladder.csv")
  println("  the ladder matches the table printed in chapter Q")
  println("render:")
  println("  uv run figures/ar6_pathways.py data-refresh/ar6-pathways.csv " +
          "without-hot-air/Images/fig-q1-ar6-pathways.svg")
}

// ---- Chapter Q: the scenario database, one region at a time ----
// Figures Q.2, Q.3 and Q.4 all ask the same question of the AR6 Scenarios
// Database - where does a carrier start, where does the median pathway put it,
// and how far apart are the pathways about it - so they share one row type and
// one query, parameterised by which timeseries view and which region.
//
// A pathway is counted for a variable only if it reports that variable in all
// three printed years, so the pathway count beside a row describes every number
// in that row rather than the last column of it. Variables still differ from
// each other - not every pathway splits out solar and wind - which is why the
// chapter reads the spread rather than adding the medians up.
case class MixRow(category: String, variable: String, pathways: Int,
                  y2020: Double, y2030: Double, y2050: Double,
                  p5: Double, p95: Double) {
  def multiple = if (y2020 > 0) y2050 / y2020 else 0.0
  def fallPercent = (1 - multiple) * 100
}

def gatherMix(st: java.sql.Statement, view: String, region: String,
              variables: Seq[String]): Seq[MixRow] = {
  val inList = variables.map(v => s"'${v.replace("'", "''")}'").mkString(",")
  val rs = st.executeQuery(s"""
    select m.category, d.Variable, count(*) as n,
           median(d."2020") as y2020, median(d."2030") as y2030, median(d."2050") as y2050,
           quantile_cont(d."2050", 0.05) as p5, quantile_cont(d."2050", 0.95) as p95
    from $view d join meta m
      on m.model = replace(d.Model, ',', ' ') and m.scenario = replace(d.Scenario, ',', ' ')
    where d.Region = '$region' and d.Variable in ($inList) and m.category in ('C1', 'C3')
      and d."2020" is not null and d."2030" is not null and d."2050" is not null
    group by 1, 2 order by 1, 2""")
  val rows = scala.collection.mutable.ArrayBuffer[MixRow]()
  while (rs.next())
    rows += MixRow(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getDouble(4),
                   rs.getDouble(5), rs.getDouble(6), rs.getDouble(7), rs.getDouble(8))
  rows.toSeq
}

def mixRow(rows: Seq[MixRow], cat: String, variable: String, where: String): MixRow =
  rows.find(r => r.category == cat && r.variable == variable)
    .getOrElse(sys.error(s"$where: no rows for $cat $variable"))

// Every number chapter Q transcribes out of these tables is pinned here, in
// the style of chapterQPathways' printedLadder: a refresh that moves one fails
// the task rather than leaving the chapter and a committed SVG quietly wrong.
def requireClose(what: String, got: Double, printed: Double, tolerance: Double): Unit =
  require(math.abs(got - printed) <= tolerance,
    f"$what is now $got%.2f; chapter Q prints $printed%.2f, and the tolerance is $tolerance%.2f")

val PRIMARY_ENERGY = Seq("Primary Energy", "Primary Energy|Solar", "Primary Energy|Wind",
  "Primary Energy|Nuclear", "Primary Energy|Hydro", "Primary Energy|Biomass",
  "Primary Energy|Coal", "Primary Energy|Oil", "Primary Energy|Gas",
  "Primary Energy|Non-Biomass Renewables")

// ---- Chapter Q: what the pathways actually build ----
// Figure Q.2. The report says its categories contain "different shares of
// nuclear, biomass, non-biomass renewables, and fossil CCS across pathways"
// and prints no number for any of them. The numbers exist one level down, in
// the AR6 Scenarios Database's timeseries file, and this task reads them.
//
// That file is 345 MB and is not in this repository: it has to be downloaded
// by hand from the IIASA Scenario Explorer, which requires a free account, and
// put at data-refresh/ar6-world-v1.1.csv. The derived table this writes is
// small and is committed, so the chapter builds without it.
//
// The join to a scenario's category is by model and scenario name against the
// metadata extract, whose names have had commas replaced by spaces, so the
// same replacement is made here.
//
// The check is the report's own published fossil declines: C1's medians for
// coal, oil and gas in 2050 against 2019 are about 95%, 60% and 45%, and C3's
// about 85%, 30% and 15%. If the join or the aggregation were wrong, those
// would not come back.
@main
def chapterQEnergyMix(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  val big = dir / "ar6-world-v1.1.csv"
  require(os.exists(big),
    "chapterQEnergyMix: put AR6_Scenarios_Database_World_v1.1.csv at " +
    "data-refresh/ar6-world-v1.1.csv (345 MB, from the IIASA Scenario Explorer)")
  val meta = dir / "ar6-scenarios-meta.csv"

  // Primary energy is in exajoules a year; the book wants kWh per day per
  // person, and the 2050 population is the same UN projection figure Q.1 uses.
  def population(year: String) = os.read.lines(dir / "world-population-projected.csv").drop(1)
    .map(_.split(",")).find(_(0) == year).map(_(1).toDouble).get
  val (pop2020, pop2050) = (population("2020"), population("2050"))
  def perPerson(ej: Double, people: Double) = ej * 1e18 / 3.6e6 / people / 365

  val out = new StringBuilder
  out ++= "category,carrier,pathways,ej_2020,ej_2030,ej_2050,ej_2050_p5,ej_2050_p95," +
          "kwh_per_day_2020,kwh_per_day_2050\n"
  withConn { c =>
    val st = c.createStatement()
    st.execute(s"create view db as select * from read_csv_auto('${big}', header=true, sample_size=-1)")
    st.execute(s"create view meta as select * from read_csv_auto('${meta}', header=true)")
    val rows = gatherMix(st, "db", "World", PRIMARY_ENERGY)
    println(f"${"cat"}%-4s ${"carrier"}%-26s ${"n"}%4s ${"2020"}%8s ${"2030"}%8s ${"2050"}%8s " +
            f"${"x2020"}%7s ${"kWh/d"}%7s   2050 spread")
    for (r <- rows) {
      val name = r.variable.stripPrefix("Primary Energy|").replace("Primary Energy", "all sources")
      out ++= f"${r.category},$name,${r.pathways},${r.y2020}%.1f,${r.y2030}%.1f,${r.y2050}%.1f," +
              f"${r.p5}%.1f,${r.p95}%.1f," +
              f"${perPerson(r.y2020, pop2020)}%.2f,${perPerson(r.y2050, pop2050)}%.2f\n"
      println(f"${r.category}%-4s $name%-26s ${r.pathways}%4d ${r.y2020}%8.1f ${r.y2030}%8.1f " +
              f"${r.y2050}%8.1f ${r.multiple}%7.1f ${perPerson(r.y2050, pop2050)}%7.1f   " +
              f"${r.p5}%.0f to ${r.p95}%.0f EJ")
    }
    // The report's own numbers, as the check on the join, run before the
    // derived table is written: a bad join must not leave a bad file behind
    // for the figure script to render from.
    val printed = Map(("C1", "Coal") -> 95.0, ("C1", "Oil") -> 60.0, ("C1", "Gas") -> 45.0,
                      ("C3", "Coal") -> 85.0, ("C3", "Oil") -> 30.0, ("C3", "Gas") -> 15.0)
    require(rows.nonEmpty, "chapterQEnergyMix: the join matched nothing - " +
      "do the model and scenario names in the timeseries file match the metadata extract?")
    for (((cat, fuel), expected) <- printed.toSeq.sortBy(_._1)) {
      val r = mixRow(rows, cat, s"Primary Energy|$fuel", "chapterQEnergyMix")
      require(r.pathways > 50 && r.y2020 > 0,
        f"chapterQEnergyMix: $cat $fuel has ${r.pathways}%d pathways and ${r.y2020}%.1f EJ in 2020, which cannot be right")
      println(f"  check $cat $fuel%-5s falls ${r.fallPercent}%4.0f%% by 2050; the report prints $expected%.0f%%")
      require(math.abs(r.fallPercent - expected) <= 8,
        f"chapterQEnergyMix: $cat $fuel falls ${r.fallPercent}%.0f%%, the report prints $expected%.0f%%")
    }
    // And the figures chapter Q prints in the book's own units, which the
    // report does not publish and so nothing else would catch.
    val world = mixRow(rows, "C1", "Primary Energy", "chapterQEnergyMix")
    requireClose("chapterQEnergyMix: the C1 world total in 2050",
                 perPerson(world.y2050, pop2050), 42.7, 1.0)
    requireClose("chapterQEnergyMix: the C1 world total in 2020",
                 perPerson(world.y2020, pop2020), 55.4, 1.0)
    for ((carrier, printedMultiple) <- Seq("Solar" -> 32.0, "Wind" -> 16.0, "Nuclear" -> 2.1))
      requireClose(s"chapterQEnergyMix: C1 $carrier in 2050 against 2020",
                   mixRow(rows, "C1", s"Primary Energy|$carrier", "chapterQEnergyMix").multiple,
                   printedMultiple, printedMultiple * 0.1)
    os.write.over(dir / "ar6-energy-mix.csv", out.toString)
    println("wrote data-refresh/ar6-energy-mix.csv")
  }
  println("render:")
  println("  uv run figures/ar6_energy_mix.py data-refresh/ar6-energy-mix.csv " +
          "without-hot-air/Images/fig-q2-ar6-energy-mix.svg")
}

// ---- Chapter Q: the scenario metadata, cut down from the workbook ----
// The AR6 Scenarios Database publishes a metadata workbook alongside its
// timeseries: one row per scenario, with the category it was sorted into and
// the emissions and warming indicators chapter 3 of the report is built on.
// The workbook is in this repository; this task cuts it to the columns
// chapterQPathways uses and writes them as a CSV, so that extraction is a
// step anyone can rerun rather than something done by hand once.
//
// Two model names contain a comma, which would shift every column of a plain
// CSV, so commas in names are replaced by spaces here - and the reader in
// chapterQPathways checks each row's field count anyway.
@main
def chapterQMeta(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  val xlsx = dir / "ar6-metadata-indicators-v1.1.xlsx"
  require(os.exists(xlsx), s"chapterQMeta: $xlsx is missing")
  // (column in the CSV, column in the workbook)
  val cols = Seq(
    "model" -> "Model", "scenario" -> "Scenario", "category" -> "Category", "imp" -> "IMP_marker",
    "ghg_2030" -> "GHG emissions 2030 Gt CO2-equiv/yr (Harmonized-Infilled)",
    "ghg_2050" -> "GHG emissions 2050 Gt CO2-equiv/yr (Harmonized-Infilled)",
    "cut_2030" -> "GHG emissions reductions 2019-2030 % modelled Harmonized-Infilled",
    "cut_2040" -> "GHG emissions reductions 2019-2040 % modelled Harmonized-Infilled",
    "cut_2050" -> "GHG emissions reductions 2019-2050 % modelled Harmonized-Infilled",
    "netzero_co2" -> "Year of netzero CO2 emissions (Harm-Infilled) Table SPM2",
    "cumulative_co2" -> "Cumulative net CO2 (2020 to netzero  Gt CO2) (Harm-Infilled)",
    "peak_warming" -> "Median peak warming (MAGICCv7.5.3)",
    "warming_2100" -> "Median warming in 2100 (MAGICCv7.5.3)")
  withConn { c =>
    val st = c.createStatement()
    st.execute("INSTALL excel"); st.execute("LOAD excel")
    val src = s"read_xlsx('${xlsx.toString.replace("'", "''")}', " +
              "sheet='meta_Ch3vetted_withclimate', header=true, all_varchar=true)"
    val header = {
      val rs = st.executeQuery(s"select * from $src limit 0")
      (1 to rs.getMetaData.getColumnCount).map(rs.getMetaData.getColumnName)
    }
    // The workbook's own header text is matched loosely, because a stray
    // double space in it would otherwise silently drop a column.
    def find(want: String) = {
      val squash = (s: String) => s.replaceAll("[\\s,]+", " ").trim.toLowerCase
      header.find(h => squash(h) == squash(want))
        .getOrElse(sys.error(s"chapterQMeta: no column like '$want' in the workbook"))
    }
    val select = cols.map { case (name, want) =>
      val col = "\"" + find(want).replace("\"", "\"\"") + "\""
      if (name == "model" || name == "scenario") s"replace($col, ',', ' ') as $name"
      else s"$col as $name"
    }.mkString(", ")
    val rs = st.executeQuery(s"select $select from $src where Category is not null")
    val out = new StringBuilder; out ++= cols.map(_._1).mkString(",") + "\n"
    var n = 0
    while (rs.next()) {
      out ++= (1 to cols.length).map { i =>
        val v = rs.getString(i)
        if (v == null || v == "NA") "" else v.trim
      }.mkString(",") + "\n"
      n += 1
    }
    require(n == 1202, s"chapterQMeta: $n scenarios, the vetted set has 1202")
    os.write.over(dir / "ar6-scenarios-meta.csv", out.toString)
    println(s"wrote data-refresh/ar6-scenarios-meta.csv ($n scenarios)")
  }
}

// ---- Chapter Q: what the pathways build in Europe, and ask of Europeans ----
// Figures Q.3 and Q.4. The same questions as chapterQEnergyMix, asked of one
// region rather than the world, and they come back with different answers:
// world nuclear roughly doubles by 2050 in the median 1.5 C pathway and
// European nuclear falls by more than half; and what separates C1 from C3 in
// Europe is mostly how much energy is used, not what builds it.
//
// Two files are read, and neither is in this repository. The R10 regional
// timeseries (1.2 GB) goes at data-refresh/ar6-r10-v1.1.csv and is the source
// of every number the chapter prints; the ISO3 country timeseries (1.1 GB)
// goes at data-refresh/ar6-iso3-v1.1.csv and is read only to cross-check the
// nuclear direction against a tighter definition of Europe, and to establish
// the negative result the chapter reports about Britain and Sweden. Both come
// from the IIASA Scenario Explorer, which wants a free account.
//
// The population is the pathway's own, not the UN projection used for the
// world: a downscaled scenario carries its own regional population, and using
// it keeps the per-person figure internally consistent with the energy beside
// it.
//
// The checks are that the region is the Europe it claims to be - about 547
// million people in 2020, which is Europe without the former Soviet Union -
// that its share of world primary energy in 2020 is about a ninth, and then
// the figures chapter Q transcribes out of the two tables this writes - the
// per-person totals and sectors, the per-carrier changes, the nuclear falls
// and spread, the conversion chain, the build rates, and the count of British
// and Swedish scenarios, which is zero in the vetted set.
@main
def chapterQEurope(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"
  val r10 = dir / "ar6-r10-v1.1.csv"
  val iso3 = dir / "ar6-iso3-v1.1.csv"
  val worldMix = dir / "ar6-energy-mix.csv"
  require(os.exists(r10),
    "chapterQEurope: put AR6_Scenarios_Database_R10_regions_v1.1.csv at " +
    "data-refresh/ar6-r10-v1.1.csv (1.2 GB, from the IIASA Scenario Explorer)")
  require(os.exists(iso3),
    "chapterQEurope: put AR6_Scenarios_Database_ISO3_v1.1.csv at " +
    "data-refresh/ar6-iso3-v1.1.csv (1.1 GB, from the IIASA Scenario Explorer)")
  require(os.exists(worldMix),
    "chapterQEurope: data-refresh/ar6-energy-mix.csv is missing - run chapterQEnergyMix first, " +
    "since Europe's share of world primary energy is checked against it")
  val meta = dir / "ar6-scenarios-meta.csv"
  // Final energy by sector and the generating plant behind it: figure Q.4's
  // side of the question, which is what the pathways ask of a European rather
  // than what they build for one.
  val demand = Seq("Final Energy", "Final Energy|Electricity", "Final Energy|Transportation",
    "Final Energy|Residential and Commercial", "Final Energy|Industry",
    "Capacity|Electricity|Wind", "Capacity|Electricity|Solar", "Capacity|Electricity|Nuclear")
  // The conversion chain, for the passage on why primary energy falls faster
  // than anyone uses less: what is generated, what is made out of it, and what
  // transport swaps for what. Only the R10 gather asks for these; the ISO3 cut
  // is a second opinion on primary energy alone and would scan 1.1 GB for rows
  // it then discards.
  val chain = Seq("Secondary Energy|Electricity", "Secondary Energy|Hydrogen",
    "Secondary Energy|Liquids|Biomass",
    "Final Energy|Transportation|Electricity", "Final Energy|Transportation|Liquids")
  def perPerson(ej: Double, millions: Double) = ej * 1e18 / 3.6e6 / (millions * 1e6) / 365

  val out = new StringBuilder
  out ++= "region,category,carrier,pathways,ej_2020,ej_2030,ej_2050," +
          "ej_2050_p5,ej_2050_p95,kwh_per_day_2020,kwh_per_day_2050\n"
  val change = new StringBuilder
  change ++= "category,variable,unit,pathways,v_2020,v_2030,v_2050," +
             "per_person_2020,per_person_2030,per_person_2050\n"
  withConn { c =>
    val st = c.createStatement()
    st.execute(s"create view r10 as select * from read_csv_auto('${r10}', header=true, sample_size=-1)")
    st.execute(s"create view iso as select * from read_csv_auto('${iso3}', header=true, sample_size=-1)")
    st.execute(s"create view meta as select * from read_csv_auto('${meta}', header=true)")
    val europe = gatherMix(st, "r10", "R10EUROPE", PRIMARY_ENERGY :+ "Population")
    require(europe.nonEmpty, "chapterQEurope: the join to R10EUROPE matched nothing - " +
      "do the model and scenario names in the regional file match the metadata extract?")
    def eu1(cat: String, v: String) = mixRow(europe, cat, v, "chapterQEurope")

    // The region has to be the Europe it claims to be before anything else in
    // here means much: the United Nations put Europe without the former
    // Soviet republics at about 547 million people in 2020.
    val pop = Map("C1" -> eu1("C1", "Population"), "C3" -> eu1("C3", "Population"))
    for ((cat, p) <- pop.toSeq.sortBy(_._1)) {
      println(f"$cat population: ${p.y2020}%.0f million in 2020, ${p.y2050}%.0f million in 2050 (${p.pathways} pathways)")
      require(p.y2020 > 500 && p.y2020 < 600,
        f"chapterQEurope: R10EUROPE has ${p.y2020}%.0f million people in 2020; Europe without the " +
        "former Soviet Union is about 547 million, so this is not the region it should be")
    }

    println(f"${"cat"}%-4s ${"carrier"}%-26s ${"n"}%4s ${"2020"}%8s ${"2030"}%8s ${"2050"}%8s " +
            f"${"x2020"}%7s ${"kWh/d"}%7s   2050 spread")
    for (r <- europe if r.variable != "Population") {
      val name = r.variable.stripPrefix("Primary Energy|").replace("Primary Energy", "all sources")
      val (d20, d50) = (perPerson(r.y2020, pop(r.category).y2020), perPerson(r.y2050, pop(r.category).y2050))
      out ++= f"Europe,${r.category},$name,${r.pathways},${r.y2020}%.2f,${r.y2030}%.2f,${r.y2050}%.2f," +
              f"${r.p5}%.2f,${r.p95}%.2f,$d20%.2f,$d50%.2f\n"
      println(f"${r.category}%-4s $name%-26s ${r.pathways}%4d ${r.y2020}%8.2f ${r.y2030}%8.2f " +
              f"${r.y2050}%8.2f ${r.multiple}%7.1f $d50%7.1f   ${r.p5}%.1f to ${r.p95}%.1f EJ")
    }

    // About a ninth of the world's primary energy, which is what Europe
    // without the former Soviet Union actually uses. If the join had picked up
    // the wrong scenarios this would not land near it. The world table is read
    // by column name, not position, so a column added to it cannot silently
    // turn this into a comparison against something else.
    val worldLines = os.read.lines(worldMix)
    val head = worldLines.head.split(",").zipWithIndex.toMap
    val worldC1 = worldLines.tail.map(_.split(","))
      .find(f => f(head("category")) == "C1" && f(head("carrier")) == "all sources")
      .map(f => f(head("ej_2020")).toDouble)
      .getOrElse(sys.error("chapterQEurope: no C1 'all sources' row in ar6-energy-mix.csv"))
    val share = eu1("C1", "Primary Energy").y2020 / worldC1 * 100
    println(f"  check Europe is ${share}%.1f%% of world primary energy in 2020")
    require(share > 10 && share < 13,
      f"chapterQEurope: R10EUROPE is ${share}%.1f%% of world primary energy in 2020, which is not about a ninth")

    // The chapter's headline: nuclear is the one carrier that goes the other
    // way from the world's. Both cuts of Europe have to agree about that.
    val eu = gatherMix(st, "iso", "EU", PRIMARY_ENERGY :+ "Population")
    require(eu.nonEmpty, "chapterQEurope: the join to the ISO3 file's EU aggregate matched nothing")
    for ((label, rows, printedFall) <- Seq(("R10EUROPE", europe, 58.0), ("EU", eu, 91.0))) {
      val nuc = mixRow(rows, "C1", "Primary Energy|Nuclear", "chapterQEurope")
      println(f"  check $label%-9s C1 nuclear ${nuc.y2020}%.2f EJ in 2020 to ${nuc.y2050}%.2f in 2050, down ${nuc.fallPercent}%.0f%%")
      requireClose(s"chapterQEurope: $label C1 nuclear's fall by 2050, in per cent",
                   nuc.fallPercent, printedFall, 8.0)
    }

    // The second opinion is kept in the same file, on the same footing, so a
    // reader can see how much of Europe's answer depends on where its edge is
    // drawn. Its population comes from the ISO3 file's own EU rows.
    val euPop = Map("C1" -> mixRow(eu, "C1", "Population", "chapterQEurope"),
                    "C3" -> mixRow(eu, "C3", "Population", "chapterQEurope"))
    for (r <- eu if r.variable.startsWith("Primary Energy")) {
      val name = r.variable.stripPrefix("Primary Energy|").replace("Primary Energy", "all sources")
      out ++= f"EU,${r.category},$name,${r.pathways},${r.y2020}%.2f,${r.y2030}%.2f,${r.y2050}%.2f," +
              f"${r.p5}%.2f,${r.p95}%.2f,${perPerson(r.y2020, euPop(r.category).y2020)}%.2f," +
              f"${perPerson(r.y2050, euPop(r.category).y2050)}%.2f\n"
    }

    // Figure Q.3's numbers, as chapter Q prints them.
    for (cat <- Seq("C1", "C3")) {
      val all = eu1(cat, "Primary Energy")
      val printedTotals = Map("C1" -> (89.7, 69.2), "C3" -> (89.5, 73.7))
      requireClose(s"chapterQEurope: Europe's $cat primary energy per person in 2020, kWh/d",
                   perPerson(all.y2020, pop(cat).y2020), printedTotals(cat)._1, 2.0)
      requireClose(s"chapterQEurope: Europe's $cat primary energy per person in 2050, kWh/d",
                   perPerson(all.y2050, pop(cat).y2050), printedTotals(cat)._2, 2.0)
    }
    val printedC1 = Map("Coal" -> -98.0, "Oil" -> -81.0, "Gas" -> -68.0,
                        "Solar" -> 9.3, "Wind" -> 7.0, "Biomass" -> 1.6, "Hydro" -> 1.1)
    for ((carrier, printed) <- printedC1.toSeq.sortBy(_._1)) {
      val r = eu1("C1", s"Primary Energy|$carrier")
      // Growth is printed as a multiple and decline as a percentage, so each
      // is checked in the unit the chapter and the figure actually use.
      if (printed < 0) requireClose(s"chapterQEurope: Europe's C1 $carrier fall by 2050, in per cent",
                                    -r.fallPercent, printed, 6.0)
      else requireClose(s"chapterQEurope: Europe's C1 $carrier in 2050 against 2020",
                        r.multiple, printed, printed * 0.12)
    }
    val nucSpread = eu1("C1", "Primary Energy|Nuclear")
    requireClose("chapterQEurope: the low end of Europe's C1 nuclear spread in 2050, EJ",
                 nucSpread.p5, 0.1, 0.3)
    requireClose("chapterQEurope: the high end of Europe's C1 nuclear spread in 2050, EJ",
                 nucSpread.p95, 13.8, 1.5)

    // Figure Q.4: what the pathways ask of a European, and the plant behind it.
    val ch = gatherMix(st, "r10", "R10EUROPE", demand ++ chain)
    require(ch.nonEmpty, "chapterQEurope: R10EUROPE has no final-energy or capacity rows")
    println(f"\n${"cat"}%-4s ${"variable"}%-40s ${"n"}%4s ${"2020"}%9s ${"2030"}%9s ${"2050"}%9s   per person")
    for (r <- ch) {
      val unit = if (r.variable.startsWith("Capacity")) "GW" else "EJ/yr"
      val pp = (y: Double, p: Double) =>
        if (unit == "GW") y * 1000 / p          // watts per person
        else perPerson(y, p)                     // kWh per day per person
      val (a, b, d) = (pp(r.y2020, pop(r.category).y2020), pp(r.y2030, pop(r.category).y2030),
                       pp(r.y2050, pop(r.category).y2050))
      change ++= f"${r.category},${r.variable},$unit,${r.pathways},${r.y2020}%.2f,${r.y2030}%.2f," +
                 f"${r.y2050}%.2f,$a%.2f,$b%.2f,$d%.2f\n"
      println(f"${r.category}%-4s ${r.variable}%-40s ${r.pathways}%4d ${r.y2020}%9.2f ${r.y2030}%9.2f " +
              f"${r.y2050}%9.2f   $a%6.1f to $d%6.1f ${if (unit == "GW") "W" else "kWh/d"}")
    }
    def chRow(cat: String, v: String) = mixRow(ch, cat, v, "chapterQEurope")
    // The chapter's demand-side sentences, pinned the same way.
    requireClose("chapterQEurope: Europe's C1 final energy per person in 2020, kWh/d",
                 perPerson(chRow("C1", "Final Energy").y2020, pop("C1").y2020), 71.4, 2.0)
    requireClose("chapterQEurope: Europe's C1 final energy per person in 2050, kWh/d",
                 perPerson(chRow("C1", "Final Energy").y2050, pop("C1").y2050), 49.7, 2.0)
    val elecShare = (cat: String, year: String) => {
      val (fe, el) = (chRow(cat, "Final Energy"), chRow(cat, "Final Energy|Electricity"))
      val pick = (r: MixRow) => if (year == "2020") r.y2020 else r.y2050
      pick(el) / pick(fe) * 100
    }
    requireClose("chapterQEurope: electricity's share of Europe's C1 final energy in 2020, per cent",
                 elecShare("C1", "2020"), 21.0, 2.0)
    requireClose("chapterQEurope: electricity's share of Europe's C1 final energy in 2050, per cent",
                 elecShare("C1", "2050"), 59.0, 3.0)
    // The chapter states these falls per person, and Europe's population grows
    // inside the pathways, so they are computed and pinned per person. The
    // aggregate figures are three points smaller and are deliberately not the
    // ones in print.
    val feCut = (cat: String) => {
      val r = chRow(cat, "Final Energy")
      (1 - perPerson(r.y2050, pop(cat).y2050) / perPerson(r.y2020, pop(cat).y2020)) * 100
    }
    println(f"  check final energy per person falls ${feCut("C1")}%.0f%% in C1 and ${feCut("C3")}%.0f%% in C3 by 2050")
    requireClose("chapterQEurope: the fall in Europe's C1 final energy per person by 2050, per cent",
                 feCut("C1"), 30.4, 3.0)
    requireClose("chapterQEurope: the fall in Europe's C3 final energy per person by 2050, per cent",
                 feCut("C3"), 14.7, 3.0)
    // The section on why primary energy falls faster than anyone uses less
    // quotes the Europe-wide totals rather than the per-person figures, so
    // those are pinned too: rebasing feCut onto per person left them unguarded.
    val primaryFall = eu1("C1", "Primary Energy").fallPercent
    val finalFall = chRow("C1", "Final Energy").fallPercent
    println(f"  check Europe-wide primary energy falls ${primaryFall}%.0f%% and final energy ${finalFall}%.0f%%")
    requireClose("chapterQEurope: the fall in Europe's C1 primary energy by 2050, Europe-wide, per cent",
                 primaryFall, 19.0, 3.0)
    requireClose("chapterQEurope: the fall in Europe's C1 final energy by 2050, Europe-wide, per cent",
                 finalFall, 27.0, 3.0)
    // And the like-for-like comparison the section now makes: how far apart the
    // two categories are in 2050 itself, per person, in demand and in plant.
    // These are commensurable with each other; a fall from 2020 is not.
    val fe50 = (cat: String) => perPerson(chRow(cat, "Final Energy").y2050, pop(cat).y2050)
    val demandApart = (1 - fe50("C1") / fe50("C3")) * 100
    println(f"  check in 2050 C1 uses ${demandApart}%.0f%% less energy per person than C3")
    requireClose("chapterQEurope: how much less energy per person C1 uses than C3 in 2050, per cent",
                 demandApart, 17.0, 3.0)
    for ((plant, printed) <- Seq("Wind" -> 25.0, "Solar" -> 36.0)) {
      val perHead = (cat: String) =>
        chRow(cat, s"Capacity|Electricity|$plant").y2050 * 1000 / pop(cat).y2050
      val apart = (perHead("C1") / perHead("C3") - 1) * 100
      println(f"  check in 2050 C1 has ${apart}%.0f%% more $plant capacity per person than C3")
      requireClose(s"chapterQEurope: how much more $plant capacity per person C1 has than C3 in 2050, per cent",
                   apart, printed, 3.0)
    }
    // The sectors and the nuclear capacity the section prints, which nothing
    // else would catch.
    for ((cat, variable, a, b) <- Seq(
        ("C1", "Final Energy|Transportation", 22.3, 14.0),
        ("C1", "Final Energy|Residential and Commercial", 26.1, 19.3),
        ("C1", "Final Energy|Industry", 21.7, 16.6),
        ("C3", "Final Energy|Residential and Commercial", 27.0, 25.7))) {
      val r = chRow(cat, variable)
      requireClose(s"chapterQEurope: $cat ${variable.stripPrefix("Final Energy|")} per person in 2020, kWh/d",
                   perPerson(r.y2020, pop(cat).y2020), a, 1.5)
      requireClose(s"chapterQEurope: $cat ${variable.stripPrefix("Final Energy|")} per person in 2050, kWh/d",
                   perPerson(r.y2050, pop(cat).y2050), b, 1.5)
    }
    val nuclearPlant = chRow("C1", "Capacity|Electricity|Nuclear")
    requireClose("chapterQEurope: Europe's C1 nuclear capacity in 2020, GW", nuclearPlant.y2020, 96.0, 6.0)
    requireClose("chapterQEurope: Europe's C1 nuclear capacity in 2050, GW", nuclearPlant.y2050, 43.0, 6.0)
    // Figure Q.4's right-hand panel is the section's scoreboard, and the rates
    // exist nowhere but the figure script, so they are pinned from the same
    // arithmetic here.
    for ((plant, firstLeg, secondLeg) <- Seq(("Wind", 31.0, 35.0), ("Solar", 61.0, 38.0))) {
      val r = chRow("C1", s"Capacity|Electricity|$plant")
      requireClose(s"chapterQEurope: the gigawatts of $plant a year the 2020s ask of Europe",
                   (r.y2030 - r.y2020) / 10, firstLeg, 4.0)
      requireClose(s"chapterQEurope: the gigawatts of $plant a year the 2030s and 2040s ask of Europe",
                   (r.y2050 - r.y2030) / 20, secondLeg, 4.0)
    }

    // Why primary energy falls faster than anyone uses less. Three of these
    // pull the same way and the fourth pulls back, which is the passage's
    // point, so all four are pinned.
    val primary = eu1("C1", "Primary Energy")
    val finalEnergy = chRow("C1", "Final Energy")
    println(f"  check primary over final energy is ${primary.y2020 / finalEnergy.y2020}%.2f in 2020 " +
            f"and ${primary.y2050 / finalEnergy.y2050}%.2f in 2050")
    requireClose("chapterQEurope: Europe's C1 primary-to-final ratio in 2020",
                 primary.y2020 / finalEnergy.y2020, 1.26, 0.05)
    requireClose("chapterQEurope: Europe's C1 primary-to-final ratio in 2050",
                 primary.y2050 / finalEnergy.y2050, 1.39, 0.06)
    // The chapter prints both legs of the transport swap as well as the ratio,
    // so both legs are pinned: a refresh that halved them would leave the ratio
    // at 3.2 and the sentence wrong.
    val liquidsOut = chRow("C1", "Final Energy|Transportation|Liquids")
    val electricIn = chRow("C1", "Final Energy|Transportation|Electricity")
    val (liquidsDrop, electricRise) = (liquidsOut.y2020 - liquidsOut.y2050, electricIn.y2050 - electricIn.y2020)
    println(f"  check transport drops $liquidsDrop%.1f EJ of liquids for $electricRise%.1f EJ of " +
            f"electricity, a ratio of ${liquidsDrop / electricRise}%.1f")
    requireClose("chapterQEurope: the liquid fuel European transport gives up by 2050, EJ", liquidsDrop, 11.1, 1.2)
    requireClose("chapterQEurope: the electricity European transport takes on by 2050, EJ", electricRise, 3.5, 0.5)
    requireClose("chapterQEurope: what a joule of transport electricity replaces, in joules of liquid fuel",
                 liquidsDrop / electricRise, 3.2, 0.4)
    // Generation and the two fuels made out of it, all four of which the
    // section transcribes.
    val generated = chRow("C1", "Secondary Energy|Electricity")
    requireClose("chapterQEurope: Europe's C1 electricity generated in 2020, EJ", generated.y2020, 13.1, 1.0)
    requireClose("chapterQEurope: Europe's C1 electricity generated in 2050, EJ", generated.y2050, 27.3, 2.0)
    val bioLiquids = chRow("C1", "Secondary Energy|Liquids|Biomass")
    requireClose("chapterQEurope: Europe's C1 biomass liquids in 2020, EJ", bioLiquids.y2020, 0.56, 0.2)
    requireClose("chapterQEurope: Europe's C1 biomass liquids in 2050, EJ", bioLiquids.y2050, 3.57, 0.6)
    // The paragraph's two system-wide totals, which are sums over rows already
    // gathered and were the only figures in it without a guard.
    val fossilFall = Seq("Coal", "Oil", "Gas")
      .map(f => eu1("C1", s"Primary Energy|$f")).map(r => r.y2020 - r.y2050).sum
    val cleanRise = Seq("Non-Biomass Renewables", "Biomass")
      .map(f => eu1("C1", s"Primary Energy|$f")).map(r => r.y2050 - r.y2020).sum
    println(f"  check fossil primary energy falls $fossilFall%.0f EJ and renewables and biomass rise $cleanRise%.0f EJ")
    requireClose("chapterQEurope: the fall in Europe's C1 fossil primary energy by 2050, EJ", fossilFall, 40.0, 4.0)
    requireClose("chapterQEurope: the rise in Europe's C1 renewables and biomass by 2050, EJ", cleanRise, 26.0, 4.0)

    val h2 = chRow("C1", "Secondary Energy|Hydrogen")
    println(f"  check hydrogen goes from ${h2.y2020}%.2f EJ to ${h2.y2050}%.2f")
    requireClose("chapterQEurope: Europe's C1 secondary hydrogen in 2020, EJ", h2.y2020, 0.05, 0.15)
    requireClose("chapterQEurope: Europe's C1 secondary hydrogen in 2050, EJ", h2.y2050, 3.34, 0.5)
    // Stated on the levels rather than through MixRow.multiple, which reports
    // zero growth from a zero baseline - and C3's 2020 median is already 0.00,
    // so the 0.05 here is one rounding step from asserting the opposite of
    // what happened.
    require(h2.y2050 > 30 * h2.y2020,
      f"chapterQEurope: hydrogen now goes from ${h2.y2020}%.2f EJ to ${h2.y2050}%.2f, less than " +
      "thirty-fold; chapter Q says the pathways assume Europe builds a hydrogen industry it does not have")

    // The negative result the chapter reports: the ISO3 file has Britain and
    // Sweden in it, but none of those scenarios were vetted and sorted into a
    // temperature category, so there is no C1 or C3 answer for either.
    val rs = st.executeQuery("""
      select count(*) as n
      from iso d join meta m
        on m.model = replace(d.Model, ',', ' ') and m.scenario = replace(d.Scenario, ',', ' ')
      where d.Region in ('GBR', 'SWE')""")
    rs.next()
    println(s"  check Britain and Sweden have ${rs.getInt(1)} rows in the vetted set")
    require(rs.getInt(1) == 0,
      s"chapterQEurope: Britain and Sweden now have ${rs.getInt(1)} categorised rows; " +
      "chapter Q says the database has none and would have to be rewritten")
    val rs2 = st.executeQuery("""
      select count(distinct Model || '|' || Scenario) as n from iso
      where Region in ('GBR', 'SWE') and Variable = 'Primary Energy'""")
    rs2.next()
    println(s"  check the file holds ${rs2.getInt(1)} uncategorised British and Swedish scenarios")
    require(rs2.getInt(1) == 48,
      s"chapterQEurope: the file now holds ${rs2.getInt(1)} British and Swedish scenarios; " +
      "chapter Q and book/index.qmd both print 48")

    os.write.over(dir / "ar6-europe-mix.csv", out.toString)
    println("wrote data-refresh/ar6-europe-mix.csv")
    os.write.over(dir / "ar6-europe-change.csv", change.toString)
    println("wrote data-refresh/ar6-europe-change.csv")
  }
  println("render:")
  println("  uv run figures/ar6_europe_mix.py data-refresh/ar6-europe-mix.csv " +
          "data-refresh/ar6-energy-mix.csv without-hot-air/Images/fig-q3-ar6-europe.svg")
  println("  uv run figures/ar6_europe_change.py data-refresh/ar6-europe-change.csv " +
          "data-refresh/europe-build-rates.csv without-hot-air/Images/fig-q4-ar6-europe-change.svg")
}

// ---- Chapter M: what a barrel costs on the way to the wheel ----
// Figure M.1. Hall, Balogh and Murphy's oil cascade: of 100 units of crude in
// the ground, how much is still doing work by the time a car moves. It is the
// clearest illustration in the literature of the point chapter M is about -
// that where you draw the boundary is most of the answer - and it is also the
// clearest illustration of chapter Q's passage on why primary energy falls
// faster than anyone uses less, because these losses are inside primary energy
// and outside final energy.
//
// Everything here is hand-entered from one open-access paper: Charles Hall,
// Stephen Balogh and David Murphy, "What is the Minimum EROI that a
// Sustainable Society Must Have?", Energies 2(1):25-47, 2009, sections 5.1 and
// 5.2. There is no series to refresh; the task exists so that the arithmetic
// is checked against the paper's own published conclusions rather than
// transcribed once and trusted.
//
// This edition draws the cascade from the paper's own percentages rather than
// from the well-known chart of it in Hall, Lambert and Balogh, "EROI of
// different fuels and the implications for society", Energy Policy 64:141-152,
// 2014, figure 1. That chart's first three steps are the paper's, but its
// fourth charges 37.5 units to transport infrastructure and leaves 20.5 - and
// the 2009 paper it cites says 24 and leaves 36. 37.5 is 64.7% of the 58 left
// after the first three steps, which is the 2009 paper's 64% TOTAL applied a
// second time to a flow already net of the first 42%. The check below is the
// paper's own headline: roughly three units of crude for one unit of service,
// which its own numbers give and the chart's do not.
@main
def chapterMOilCascade(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)

  // (stage, what it costs, per cent of the original 100 units, which boundary
  // it falls inside). The boundary names are the paper's own.
  val steps = Seq(
    ("Extraction",       "getting the oil out of the ground",        10.0, "mine mouth"),
    ("Refining",         "energy to run the refinery",               10.0, "point of use"),
    ("Byproducts",       "the barrel that does not become fuel",     17.0, "point of use"),
    ("Delivery",         "moving the fuel to where it is burned",     3.0, "point of use"),
    ("Roads",            "building and maintaining what it drives on", 24.0, "extended"))

  // The boundary column drives the figure's brackets, and the figure keys a
  // lookup table off it, so it is checked here to be the three expected names
  // in nesting order rather than free text that a typo could widen.
  val boundaries = Seq("mine mouth", "point of use", "extended")
  require(steps.map(_._4).distinct == boundaries.filter(steps.map(_._4).contains),
    s"chapterMOilCascade: the boundaries are ${steps.map(_._4).distinct.mkString(", ")}, " +
    s"which is not ${boundaries.mkString(", ")} in nesting order")
  require(steps.forall(s => boundaries.contains(s._4)),
    s"chapterMOilCascade: unknown boundary in ${steps.map(_._4).distinct.mkString(", ")}")

  val pou = steps.filter(s => s._4 != "extended").map(_._3).sum
  val all = steps.map(_._3).sum
  val left = 100.0 - all
  // The paper states both of these in words, so they are the check: "our
  // EROI_pou is about 40 percent ... less than the EROI_mm", and "adding in
  // the energy costs of getting the oil in the ground to the consumer in a
  // usable from (40 percent) plus the pro-rated energy cost of the
  // infrastructure necessary to use the fuel (24 percent) is 64 percent of the
  // initial oil in the ground".
  require(math.abs(pou - 40.0) < 0.05,
    s"chapterMOilCascade: the point-of-use costs sum to $pou, the paper says 40")
  require(math.abs(all - 64.0) < 0.05,
    s"chapterMOilCascade: all the costs sum to $all, the paper says 64")
  // And its headline, which is the whole reason the paper is cited: "the
  // energy necessary to provide the services of 1 unit of crude oil ... is
  // roughly 3 units of crude oil", so "the minimum EROI is 3:1".
  val perUnitOfService = 100.0 / left
  require(math.abs(perUnitOfService - 3.0) < 0.3,
    f"chapterMOilCascade: the paper's numbers now give $perUnitOfService%.1f units of crude per unit " +
    "of service; the paper says roughly 3, which is the sentence chapter M quotes")

  // The three ratios the chapter prints at its three brackets. They are not
  // read off the cascade by subtraction: the paper scales its wellhead ratio
  // by the fraction of the barrel that survives, which is a different
  // operation and gives a different number, so the chapter says which is being
  // done and both are derived here rather than in prose.
  val eroiWellhead = 10.0            // the paper's EROI_mm for American oil
  val eroiPointOfUse = eroiWellhead * (1 - pou / 100)
  val eroiExtended = eroiWellhead * (1 - all / 100)
  println(f"  EROI $eroiWellhead%.1f at the wellhead, $eroiPointOfUse%.1f at the petrol tank, " +
          f"$eroiExtended%.1f where the work happens")
  require(math.abs(eroiPointOfUse - 6.0) < 0.3,
    f"chapterMOilCascade: the point-of-use ratio is now $eroiPointOfUse%.1f; chapter M prints six")
  require(math.abs(eroiExtended - 3.6) < 0.3,
    f"chapterMOilCascade: the extended ratio is now $eroiExtended%.1f; chapter M prints about three and a half")

  val out = new StringBuilder
  out ++= "stage,what,cost_pct,boundary,remaining_pct\n"
  var remaining = 100.0
  println("stage        cost  remaining  boundary")
  for ((stage, what, cost, boundary) <- steps) {
    remaining -= cost
    out ++= f"$stage,$what,$cost%.1f,$boundary,$remaining%.1f\n"
    println(f"  $stage%-12s $cost%4.1f  $remaining%8.1f  $boundary")
  }
  os.write.over(dir / "eroi-oil-cascade.csv", out.toString)
  println("wrote data-refresh/eroi-oil-cascade.csv")
  println(f"  point-of-use costs $pou%.0f%% of the barrel, all costs $all%.0f%%, " +
          f"$left%.0f%% still doing work")
  println(f"  check: $perUnitOfService%.1f units of crude per unit of service; the paper says about 3")

  // The discrepancy with the 2014 chart, printed so the note in the chapter
  // can be checked rather than believed. If a future reading of either paper
  // changes these, the chapter's paragraph about it has to change too.
  val chartInfra = 37.5
  val chartLeft = 100.0 - 10.0 - 27.0 - 5.0 - chartInfra
  require(math.abs(chartLeft - 20.5) < 0.1,
    s"chapterMOilCascade: the 2014 chart is meant to leave 20.5 units, this gives $chartLeft")
  val doubleCounted = chartInfra / (100.0 - 10.0 - 27.0 - 5.0) * 100
  println(f"  the 2014 chart charges $chartInfra%.1f to roads and leaves $chartLeft%.1f, " +
          f"against the 2009 paper's 24.0 and $left%.1f")
  require(math.abs(doubleCounted - all) < 1.5,
    f"chapterMOilCascade: the chart's road charge is $doubleCounted%.1f%% of the flow before it; " +
    f"chapter M says that is the paper's own $all%.0f%% total applied a second time, and that " +
    "sentence only holds while the two match")
  println(f"  that charge is $doubleCounted%.1f%% of the 58 before it - the paper's own $all%.0f%% total, applied twice")
  println("render:")
  println("  uv run figures/eroi_oil_cascade.py data-refresh/eroi-oil-cascade.csv " +
          "without-hot-air/Images/fig-m1-eroi-cascade.svg")
}

/** One country-year of a fuel's trade balance: what it produced, what it burned
  * and how many people lived there. Figures 1.2a and 1.3a are the same
  * arithmetic asked of oil and of gas, so they share this and the query below
  * rather than keeping two copies that can drift apart. */
case class TradeRow(country: String, year: Int, prod: Double, cons: Double, people: Double) {
  def net = prod - cons
  def netPerDay = net * 1e9 / people / 365          // kWh per day per person
}

/** Production minus inland consumption per person, from Our World in Data's
  * republication of the Energy Institute's energy series, joined to the United
  * Nations' population estimates and projections.
  *
  * `fuel` is both the OWID column name and the grapher slug's prefix, so "Oil"
  * reads oil-production-by-country and oil-consumption-by-country. The caller
  * gets every year both series cover, sorted by country and year. */
def netTrade(fuel: String, countries: Seq[String], who: String): Seq[TradeRow] = {
  val lower = fuel.toLowerCase
  val prodCsv = fetch(s"$lower-production-by-country", s"owid-$lower-production.csv")
  val consCsv = fetch(s"$lower-consumption-by-country", s"owid-$lower-consumption.csv")
  val popCsv = fetch("population-with-un-projections", "owid-population-projections.csv")
  withConn { c =>
    val st = c.createStatement()
    def view(name: String, p: os.Path) =
      st.execute(s"create view $name as select * from " +
                 s"read_csv_auto('${p.toString.replace("'", "''")}', header=true)")
    view("prod", prodCsv); view("cons", consCsv); view("pop", popCsv)
    val inList = countries.map(n => s"'${n.replace("'", "''")}'").mkString(",")
    val rs = st.executeQuery(s"""
      select p.Entity, p.Year, p."$fuel", c."$fuel",
             coalesce(pop."Population", pop."Population (Projected)")
      from prod p
        join cons c on c.Entity = p.Entity and c.Year = p.Year
        join pop on pop.Entity = p.Entity and pop.Year = p.Year
      where p.Entity in ($inList) and p."$fuel" is not null and c."$fuel" is not null
      order by p.Entity, p.Year""")
    val rows = scala.collection.mutable.ArrayBuffer[TradeRow]()
    while (rs.next())
      rows += TradeRow(rs.getString(1), rs.getInt(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5))
    require(rows.nonEmpty, s"$who: the join matched nothing for $fuel")
    require(countries.forall(n => rows.exists(_.country == n)),
      s"$who: expected all of ${countries.mkString(", ")}, got " +
      rows.map(_.country).distinct.mkString(", "))
    rows.toSeq
  }
}

/** The CSV both figures are drawn from. */
def tradeCsv(rows: Seq[TradeRow]): String = {
  val out = new StringBuilder
  out ++= "country,year,prod_twh,cons_twh,net_twh,people,net_kwh_per_day\n"
  for (r <- rows)
    out ++= f"${r.country},${r.year},${r.prod}%.1f,${r.cons}%.1f,${r.net}%.1f," +
            f"${r.people}%.0f,${r.netPerDay}%.2f\n"
  out.toString
}

// ---- Chapter 1: figure 1.2a, the same three countries as exporters ----
// Figure 1.2 asks MacKay's question with production, which is what his own
// figure plotted. But production is not what a country has to sell: what it
// has to sell is production minus what it burns itself, and on that measure
// the three North Sea producers stop being one story at three scales and
// become three different stories.
//
// Britain and Denmark both crossed from exporter to importer within a decade
// of each other and kept falling. Norway did not, because it consumes almost
// nothing of what it pumps. Same sea, same decade, opposite outcomes - and the
// difference is domestic demand, which is the half of the equation this book
// spends most of its pages on.
//
// Everything is in energy rather than barrels, and from one publisher's own
// energy series, because net exports is a difference of two quantities and
// differencing two series converted at different factors would be arithmetic
// on sand. That does mean the levels here cannot be read off figure 1.2: the
// barrel counts there include natural-gas liquids, which carry less energy per
// barrel than crude, so the two do not convert at a single factor. The shapes
// agree, and the check below is that they do - both put the peak in 2000.
//
// The quantity is production minus inland consumption, which is a proxy for
// net trade and not customs data. Britain in particular exports its own light
// crude and imports heavier grades for its refineries, so its gross flows are
// much larger than the net figure drawn here.
@main
def chapter01NetExports(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val countries = Seq("United Kingdom", "Norway", "Denmark")
  val rows = netTrade("Oil", countries, "chapter01NetExports")

  // Each country's own story, printed and then checked.
  println("country          first  last   peak export  last in surplus   2025 net")
  val summary = for (n <- countries) yield {
    val rs2 = rows.filter(_.country == n)
    // Per person, because that is what the figure plots and the chapter
    // prints. Norway's peak year differs by one from the peak in absolute
    // energy, its population having grown through the period.
    val peak = rs2.maxBy(_.netPerDay)
    val surplus = rs2.filter(_.net > 0)
    val last = rs2.last
    println(f"$n%-16s ${rs2.head.year}   ${last.year}   ${peak.year} ${peak.netPerDay}%7.1f  " +
            f"${if (surplus.isEmpty) "never" else surplus.last.year.toString}%15s  " +
            f"${last.netPerDay}%8.1f kWh/d")
    (n, peak, surplus, last)
  }
  def one(n: String) = summary.find(_._1 == n).get

  // Each country's surplus is one unbroken run of years, which is what lets
  // the chapter talk about a country "becoming" an importer on a date rather
  // than flickering across the line. All three were importers before their
  // own oil arrived, so the run has a start as well as an end.
  for ((n, from, to) <- Seq(("United Kingdom", 1981, 2004), ("Denmark", 1998, 2014),
                            ("Norway", 1975, 2025))) {
    val (_, _, surplus, _) = one(n)
    require(surplus.nonEmpty && surplus.head.year == from && surplus.last.year == to,
      s"chapter01NetExports: $n is in surplus from " +
      s"${surplus.headOption.map(_.year.toString).getOrElse("never")} to " +
      s"${surplus.lastOption.map(_.year.toString).getOrElse("never")}; chapter 1 says $from to $to")
    require(surplus.length == to - from + 1,
      s"chapter01NetExports: $n has ${surplus.length} years in surplus between $from and $to, " +
      s"not the ${to - from + 1} of an unbroken run; the chapter describes one crossing each way")
  }
  // Norway's run has not ended, which is the contrast the figure is drawn for.
  val (_, noPeak, _, noLast) = one("Norway")
  require(one("Norway")._3.last.year == noLast.year,
    "chapter01NetExports: Norway is no longer in surplus in the last year of the data; " +
    "chapter 1 says it has never stopped exporting")
  val exported = noLast.net / noLast.prod * 100
  println(f"  check Norway still exports ${exported}%.0f%% of what it produces in ${noLast.year}")
  require(exported > 85,
    f"chapter01NetExports: Norway now exports ${exported}%.0f%% of its production; " +
    "chapter 1 says it sells about nine-tenths of it")

  // The figures the chapter transcribes, each with a tolerance.
  def close(what: String, got: Double, printed: Double, tol: Double) =
    require(math.abs(got - printed) <= tol,
      f"chapter01NetExports: $what is now $got%.1f; chapter 1 prints $printed%.1f")
  close("Norway's net exports per person in the last year", noLast.netPerDay, 494.0, 15.0)
  close("Norway's net exports per person at its peak", noPeak.netPerDay, 1074.0, 25.0)
  require(noPeak.year == 2000,
    s"chapter01NetExports: Norway's exports per person now peak in ${noPeak.year}; chapter 1 says 2000")
  close("Britain's net imports per person in the last year", one("United Kingdom")._4.netPerDay, -15.4, 2.0)
  close("Denmark's net imports per person in the last year", one("Denmark")._4.netPerDay, -16.2, 3.0)
  close("Britain's net exports per person at its peak", one("United Kingdom")._2.netPerDay, 29.4, 3.0)

  // And the tie to figure 1.2: a different publisher's unit, a different
  // conversion, but it has to be the same sea. Both put the peak in 2000.
  val sibling = dir / "north-sea-oil.csv"
  require(os.exists(sibling),
    "chapter01NetExports: data-refresh/north-sea-oil.csv is missing - run chapter01 first, " +
    "since figure 1.2's peak year is checked against this one")
  val lines = os.read.lines(sibling)
  val head = lines.head.split(",").zipWithIndex.toMap
  val peakBarrels = lines.tail.map(_.split(","))
    .maxBy(f => f(head("total_kbd")).toDouble)(Ordering.Double.TotalOrdering)(head("year")).toInt
  val peakEnergy = rows.groupBy(_.year).view.mapValues(_.map(_.prod).sum).toSeq.maxBy(_._2)._1
  println(s"  check production peaks in $peakEnergy here and in $peakBarrels in figure 1.2")
  require(peakEnergy == peakBarrels,
    s"chapter01NetExports: this data peaks in $peakEnergy and figure 1.2 in $peakBarrels; " +
    "the two figures are meant to be the same sea in different units")

  os.write.over(dir / "north-sea-net-exports.csv", tradeCsv(rows))
  println("wrote data-refresh/north-sea-net-exports.csv")
  println("render:")
  println("  uv run figures/north_sea_net_exports.py data-refresh/north-sea-net-exports.csv " +
          "without-hot-air/Images/fig-north-sea-net-exports.svg")
}

// ---- Chapter 1: figure 1.3a, the gas the electricity runs on ----
// Figure 1.3 shows that Britain's feared generating gap did not appear. What
// it does not show is what the remaining fleet burns, which is gas, and where
// that gas comes from. So this asks figure 1.2a's question of the other fuel,
// and the answer is a good deal shorter than the oil one.
//
// Britain's gas surplus lasted nine years. Its oil surplus lasted
// twenty-four. The country went from importer to exporter and back inside a
// decade, and it never exported much: the best year is about a fifth of what
// oil managed at its own peak.
//
// The Netherlands is added here because the North Sea's gas story cannot be
// told without Groningen, and because it ends differently from the others: a
// fifty-three-year unbroken surplus that stops in 2017 not because the gas ran
// out but because the earthquakes it caused made the field politically
// impossible. Denmark is the fourth case - a surplus interrupted for four
// years by the rebuilding of the Tyra hub and then resumed, which is what a
// pause for engineering looks like beside three depletion stories.
//
// Same source, same units and the same cautions as figure 1.2a: production
// minus inland consumption is a proxy for net trade rather than customs data,
// and these are energy series rather than volumes.
@main
def chapter01GasTrade(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val countries = Seq("United Kingdom", "Norway", "Denmark", "Netherlands")
  val rows = netTrade("Gas", countries, "chapter01GasTrade")

  println("country          first  last   peak export  surplus years   last net")
  val summary = for (n <- countries) yield {
    val rs = rows.filter(_.country == n)
    val peak = rs.maxBy(_.netPerDay)
    val surplus = rs.filter(_.net > 0)
    val last = rs.last
    println(f"$n%-16s ${rs.head.year}   ${last.year}   ${peak.year} ${peak.netPerDay}%7.1f  " +
            f"${surplus.length}%5d (${surplus.headOption.map(_.year.toString).getOrElse("-")}" +
            f"-${surplus.lastOption.map(_.year.toString).getOrElse("-")})  ${last.netPerDay}%8.1f kWh/d")
    (n, peak, surplus, last)
  }
  def one(n: String) = summary.find(_._1 == n).get
  def close(what: String, got: Double, printed: Double, tol: Double) =
    require(math.abs(got - printed) <= tol,
      f"chapter01GasTrade: $what is now $got%.1f; chapter 1 prints $printed%.1f")

  // The chapter's central comparison, and the reason the figure exists: gas
  // gave Britain nine years where oil gave it twenty-four.
  val ukGas = one("United Kingdom")._3
  require(ukGas.nonEmpty && ukGas.head.year == 1995 && ukGas.last.year == 2003,
    s"chapter01GasTrade: Britain's gas surplus now runs " +
    s"${ukGas.headOption.map(_.year.toString).getOrElse("never")} to " +
    s"${ukGas.lastOption.map(_.year.toString).getOrElse("never")}; chapter 1 says 1995 to 2003")
  require(ukGas.length == 9,
    s"chapter01GasTrade: Britain has ${ukGas.length} years of gas surplus, not the nine the chapter counts")
  // Against the oil figure, read from its own committed table by column name.
  val oilCsv = dir / "north-sea-net-exports.csv"
  require(os.exists(oilCsv),
    "chapter01GasTrade: data-refresh/north-sea-net-exports.csv is missing - run chapter01NetExports " +
    "first, since the chapter's comparison of the two surpluses is checked against it")
  val lines = os.read.lines(oilCsv)
  val head = lines.head.split(",").zipWithIndex.toMap
  val ukOilSurplus = lines.tail.map(_.split(","))
    .filter(f => f(head("country")) == "United Kingdom" && f(head("net_twh")).toDouble > 0)
  require(ukOilSurplus.length == 24,
    s"chapter01GasTrade: figure 1.2a gives Britain ${ukOilSurplus.length} years of oil surplus; " +
    "chapter 1 says twenty-four, and the comparison with gas's nine rests on it")
  println(s"  check Britain has ${ukGas.length} years of gas surplus against ${ukOilSurplus.length} of oil")
  // And the chapter's other cross-figure claim: Britain's best gas year is
  // about a fifth of its best oil year, per person.
  val ukOilPeak = lines.tail.map(_.split(","))
    .filter(_(head("country")) == "United Kingdom")
    .map(_(head("net_kwh_per_day")).toDouble).max
  val share = one("United Kingdom")._2.netPerDay / ukOilPeak
  println(f"  check Britain's best gas year is ${share}%.2f of its best oil year")
  require(math.abs(share - 0.20) < 0.04,
    f"chapter01GasTrade: Britain's best gas year is now ${share}%.2f of its best oil year; " +
    "chapter 1 says about a fifth")

  // The Netherlands ends its run without running out, which is the case the
  // chapter draws the contrast with, so both ends of it are pinned.
  val nl = one("Netherlands")._3
  require(nl.nonEmpty && nl.head.year == 1965 && nl.last.year == 2017 && nl.length == 53,
    s"chapter01GasTrade: the Dutch surplus is now ${nl.length} years, " +
    s"${nl.headOption.map(_.year.toString).getOrElse("never")} to " +
    s"${nl.lastOption.map(_.year.toString).getOrElse("never")}; chapter 1 says 53 unbroken years to 2017")
  // Denmark's is interrupted rather than ended, which is the fourth case and
  // the only one in either figure where a surplus comes back.
  val dk = one("Denmark")._3
  val dkGap = (dk.head.year to dk.last.year).toSet -- dk.map(_.year).toSet
  println(s"  check the Danish surplus is interrupted in ${dkGap.toSeq.sorted.mkString(", ")}")
  require(dkGap == Set(2020, 2021, 2022, 2023),
    s"chapter01GasTrade: the Danish gas surplus is now interrupted in " +
    s"${dkGap.toSeq.sorted.mkString(", ")}; chapter 1 says the four Tyra years, 2020 to 2023")
  require(dk.last.year == rows.filter(_.country == "Denmark").last.year,
    "chapter01GasTrade: Denmark is no longer in surplus in the last year; the chapter says it came back")

  // And the figures the chapter transcribes.
  close("Britain's best gas year, per person", one("United Kingdom")._2.netPerDay, 5.6, 1.0)
  close("Britain's net gas imports in the last year, per person", one("United Kingdom")._4.netPerDay, -12.3, 2.0)
  val noGas = one("Norway")
  // "Never crosses zero" is a claim about every year, not about the last one.
  // The oil task checks Norway's run the same way, and the data makes it cheap:
  // all 49 years of the gas series are in surplus.
  val noYears = rows.count(_.country == "Norway")
  require(noGas._3.length == noYears,
    s"chapter01GasTrade: Norway is in surplus in ${noGas._3.length} of its $noYears years; " +
    "chapter 1 says its line never crosses zero")
  // The run's start year, which the footnote prints. Its end needs no check:
  // the length test above already establishes that the surplus is the whole
  // series, so the last surplus year is the last year by construction.
  require(noGas._3.head.year == 1977,
    s"chapter01GasTrade: Norway's gas series now starts in ${noGas._3.head.year}; " +
    "the footnote in chapter 1 says 1977")
  // The chapter used to call this series "still climbing", which its own figure
  // contradicted: it peaked in 2017 and has wandered since. The peak year and
  // the fact that the last year is below it are both pinned now.
  require(noGas._2.year == 2017,
    s"chapter01GasTrade: Norway's gas exports per person now peak in ${noGas._2.year}; chapter 1 says 2017")
  // Not "below the peak", which is true of the maximum by construction and so
  // could never fail: meaningfully below it, and wandering rather than sliding.
  require(noGas._4.netPerDay < noGas._2.netPerDay * 0.98,
    f"chapter01GasTrade: Norway's last year (${noGas._4.netPerDay}%.1f kWh/d) is now within 2%% of its " +
    f"${noGas._2.year} peak (${noGas._2.netPerDay}%.1f); chapter 1 says it peaked then and has not returned")
  val since = rows.filter(r => r.country == "Norway" && r.year >= noGas._2.year).map(_.netPerDay)
  val (rises, falls) = (since.sliding(2).count(w => w(1) > w.head), since.sliding(2).count(w => w(1) < w.head))
  println(f"  check Norway has $rises%d up years and $falls%d down years since ${noGas._2.year}")
  require(rises > 1 && falls > 1,
    s"chapter01GasTrade: since ${noGas._2.year} Norway has $rises rises and $falls falls; " +
    "chapter 1 says the series has wandered up and down rather than trended")
  close("Norway's net gas exports in the last year, per person", noGas._4.netPerDay, 570.8, 20.0)
  close("Norway's peak gas exports, per person", noGas._2.netPerDay, 611.8, 20.0)
  require(one("Netherlands")._2.year == 1976,
    s"chapter01GasTrade: the Dutch peak is now ${one("Netherlands")._2.year}; chapter 1 says 1976, " +
    "and 1977 is within a third of a per cent of it")
  close("the Dutch peak, per person", one("Netherlands")._2.netPerDay, 94.2, 5.0)
  close("Dutch net gas imports in the last year, per person", one("Netherlands")._4.netPerDay, -27.3, 4.0)
  require(one("Denmark")._2.year == 2005,
    s"chapter01GasTrade: the Danish peak is now ${one("Denmark")._2.year}; the figure's alt text says " +
    "2005, and 2008 is within about one per cent of it")
  close("the Danish peak, per person", one("Denmark")._2.netPerDay, 28.9, 3.0)
  close("Danish net gas exports in the last year, per person", one("Denmark")._4.netPerDay, 6.7, 2.0)

  os.write.over(dir / "north-sea-gas-trade.csv", tradeCsv(rows))
  println("wrote data-refresh/north-sea-gas-trade.csv")
  println("render:")
  println("  uv run figures/north_sea_gas_trade.py data-refresh/north-sea-gas-trade.csv " +
          "without-hot-air/Images/fig-north-sea-gas-trade.svg")
}

// ---- Figure 20.9 remade: the carbon pollution of Europe's new cars ----
// MacKay's figure 20.9 is a histogram of the carbon pollution of cars on sale in
// the UK in 2006, counted off a car-buying website, with a second scale in kWh
// per 100 km at 240 g CO2 per kWh. The equivalent record today is the register
// each member state must keep under Regulation (EU) 2019/631 - one row per new
// car registered, with its type-approval CO2 figure - which the EEA republishes
// through Discodata. That is a heavier basis than his: registrations rather than
// models on sale, and the EU plus Norway and Iceland rather than Britain. The
// caption says so; the figure is drawn as a share of registrations so that the
// two years plotted can be compared to each other rather than to him.
@main
def carCo2(): Unit = {
  java.util.Locale.setDefault(java.util.Locale.US)
  val dir = os.pwd / "data-refresh"; os.makeDir.all(dir)
  val cache = dir / "api-cache"

  // `Ft` is the fuel type as the member state reported it. Full hybrids are
  // reported as plain petrol, so in practice 'petrol/electric' is the plug-in
  // hybrid - which the averages below confirm: a full hybrid could not average
  // 25 g/km on the test cycle, and a plug-in hybrid does, because the cycle
  // credits it with electric running it does not do on the road.
  val KLASS =
    "CASE WHEN Ft IN ('electric','hydrogen') THEN 'zero' " +
    "WHEN Ft IN ('petrol/electric','diesel/electric') THEN 'phev' " +
    "ELSE 'ice' END"

  def disco(query: String, cacheName: String): ujson.Value = {
    val f = cache / cacheName
    if (!os.exists(f)) {
      val r = requests.get("https://discodata.eea.europa.eu/sql",
        params = Map("query" -> query, "p" -> "1", "nrOfHits" -> "5000"),
        readTimeout = 300000, connectTimeout = 30000)
      os.makeDir.all(cache); os.write.over(f, r.text())
      Thread.sleep(3000)
    }
    val js = ujson.read(os.read(f))
    // Discodata answers 200 with an `errors` array rather than a status code,
    // so a failed query would otherwise be cached as an empty result set.
    if (js.obj.contains("errors")) { os.remove(f); sys.error(s"discodata: ${js("errors")}") }
    js("results")
  }

  /** One row per (5 g/km bin, powertrain class): how many cars were registered. */
  def histogram(table: String): Seq[(Int, String, Long)] = {
    val bin = "(CAST([Ewltp (g/km)] AS int)/5)*5"
    val q = s"SELECT $bin AS bin, $KLASS AS klass, COUNT(*) AS n " +
            s"FROM [CO2Emission].[latest].[$table] " +
            s"WHERE [Ewltp (g/km)] IS NOT NULL GROUP BY $bin, $KLASS ORDER BY bin"
    disco(q, s"eea-$table-hist.json").arr
      .map(r => (r("bin").num.toInt, r("klass").str, r("n").num.toLong)).toSeq
  }

  // 2025 is the preliminary data - the final set is published a year later and
  // moves the total by well under a percent - and 2020 is final. Both are in the
  // WLTP era, so the two histograms are on one test procedure; MacKay's 2006
  // figures are NEDC and are not, which the note spells out.
  val YEARS = Seq((2020, "co2cars_2020Fv22"), (2025, "co2cars_2025Pv31"))

  val out = new StringBuilder; out ++= "year,bin,klass,n\n"
  val summary = collection.mutable.Map[Int, (Long, Double, Double, Double, Double)]()
  for ((year, table) <- YEARS) {
    val rows = histogram(table)
    require(rows.nonEmpty, s"carCo2: no rows for $table")
    val tot = rows.map(_._3).sum
    require(tot > 8e6 && tot < 14e6, s"carCo2: $year total $tot is not a European year of registrations")
    // The bin is the lower edge, so a car in it emits between bin and bin+5;
    // the midpoint is the honest representative value for a mean.
    def mid(b: Int) = b + 2.5
    val mean = rows.map { case (b, _, n) => mid(b) * n }.sum / tot
    def share(k: String) = rows.filter(_._2 == k).map(_._3).sum.toDouble / tot
    for ((b, k, n) <- rows.sortBy(r => (r._1, r._2))) out ++= s"$year,$b,$k,$n\n"
    summary(year) = (tot, mean, share("zero"), share("phev"),
      rows.filter(_._1 >= 200).map(_._3).sum.toDouble / tot)
    println(f"  $year  ${tot}%,d cars   mean ${mean}%5.1f g/km   " +
            f"zero ${100 * share("zero")}%4.1f%%   plug-in hybrid ${100 * share("phev")}%4.1f%%   " +
            f"at or above 200 g/km ${100 * summary(year)._5}%4.1f%%")
  }
  os.write.over(dir / "car-co2-distribution.csv", out.toString)
  println("wrote data-refresh/car-co2-distribution.csv")

  // The official average of a year's registrations, exactly rather than from
  // the bins, and the same average with each of its three conventions replaced
  // by a measurement. The replacements are quoted rather than computed here;
  // the note names all three sources.
  val stats = disco(
    s"SELECT $KLASS AS klass, COUNT(*) AS n, AVG(CAST([Ewltp (g/km)] AS float)) AS mean " +
    s"FROM [CO2Emission].[latest].[${YEARS.last._2}] " +
    s"WHERE [Ewltp (g/km)] IS NOT NULL GROUP BY $KLASS", "eea-2025-classmeans.json").arr
    .map(r => r("klass").str -> (r("n").num.toLong, r("mean").num)).toMap
  val rows2025 = histogram(YEARS.last._2)
  val tot2025 = rows2025.map(_._3).sum
  val G_PER_KWH = 240.0   // MacKay's own conversion on the second scale of figure 20.9
  val n25 = stats.values.map(_._1).sum
  val official = stats.values.map { case (n, m) => n * m }.sum / n25
  require(math.abs(official - summary(2025)._2) < 3.0,
    f"carCo2: exact mean $official%.2f and binned mean ${summary(2025)._2}%.2f disagree")

  // 21 kWh per 100 km at 213 g per kWh is 4473 g per 100 km, so divide by 100
  // to get grams per kilometre - the unit the rest of the figure is in.
  val BEV_GRID  = 21.0 * 213.0 / 100.0  // real-world consumption x EU grid intensity
  val PHEV_REAL = 145.0                 // on-board monitoring, 2024 registrations
  val ICE_REAL  = 169.0                 // the same source, conventional cars
  def swap(zero: Double, phev: Double, ice: Double) =
    (stats("zero")._1 * zero + stats("phev")._1 * phev + stats("ice")._1 * ice) / n25
  val onRoad = swap(BEV_GRID, PHEV_REAL, ICE_REAL)
  for ((k, (n, m)) <- stats.toSeq.sortBy(-_._2._1))
    println(f"  2025 $k%-5s ${n}%,10d cars  official mean ${m}%5.1f g/km")
  println(f"  2025 official mean                                ${official}%5.1f g/km")
  println(f"  ... zeros at ${BEV_GRID}%.0f, the rest official                 ${swap(BEV_GRID, stats("phev")._2, stats("ice")._2)}%5.1f g/km")
  println(f"  ... and plug-in hybrids at $PHEV_REAL%.0f                      ${swap(BEV_GRID, PHEV_REAL, stats("ice")._2)}%5.1f g/km")
  println(f"  ... and combustion cars at $ICE_REAL%.0f                      ${onRoad}%5.1f g/km")
  println(f"  the whole correction is a factor of ${onRoad / official}%.2f")

  // MacKay's own legislative proposal, scored against what Europe actually
  // bought. He suggests banning the sale of any car over 80 kWh per 100 km,
  // then 60, then 40; at his own 240 g per kWh those are 192, 144 and 96 g/km.
  // Twice: as the type-approval figure reports each car, and with the same
  // three substitutions as above, where a combustion car is scaled by the ratio
  // the on-board meters show rather than car by car - a crude correction, and
  // the note says so.
  val iceScale = ICE_REAL / stats("ice")._2
  for (kwh <- Seq(80, 60, 40)) {
    val lim = kwh * G_PER_KWH / 100.0
    def over(f: (Int, String) => Double) =
      100.0 * rows2025.filter { case (b, k, _) => f(b, k) >= lim }.map(_._3).sum / tot2025
    val paper = over((b, _) => b + 2.5)
    val road = over((b, k) => k match {
      case "zero" => BEV_GRID; case "phev" => PHEV_REAL; case _ => (b + 2.5) * iceScale })
    println(f"  MacKay's ceiling of $kwh%2d kWh/100 km (${lim}%.0f g/km): " +
            f"$paper%4.1f%% of 2025 registrations fail on paper, $road%4.1f%% on the road")
  }

  // The figure needs these five numbers as well as the histogram, and they are
  // written beside it so the script that draws it quotes rather than recomputes.
  val ann = new StringBuilder; ann ++= "name,value\n"
  for ((k, v) <- Seq("official_mean" -> official, "onroad_mean" -> onRoad,
                     "bev_grid" -> BEV_GRID, "phev_real" -> PHEV_REAL, "ice_real" -> ICE_REAL))
    ann ++= f"$k,$v%.2f\n"
  os.write.over(dir / "car-co2-annotations.csv", ann.toString)
  println("wrote data-refresh/car-co2-annotations.csv")
  println("render:")
  println("  uv run figures/car_co2.py data-refresh/car-co2-distribution.csv " +
          "data-refresh/car-co2-annotations.csv without-hot-air/Images/fig-car-co2.svg")
}

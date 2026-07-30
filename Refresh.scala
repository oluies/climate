//| mvnDeps:
//| - org.duckdb:duckdb_jdbc:1.1.3

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
  os.write.over(os.pwd / "without-hot-air" / "Images" / "fig-cost-decline.svg", renderCostSvg(base, last, rows))
  print(tbl.toString)
  println(f"solar module USD/W: $mod2008%.2f (2008) -> $modNow%.2f ($modYr); wrote figure")
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
  os.write.over(os.pwd / "without-hot-air" / "Images" / "fig-gb-capture.svg", renderCaptureSvg(year, order, capMap.toMap, avgP))
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

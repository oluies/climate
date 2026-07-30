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

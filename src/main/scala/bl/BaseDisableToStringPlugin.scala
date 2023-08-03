package bl

import scala.util.matching.Regex

private[bl] final case class PrettyPrinter(level: Int, inQuotes: Boolean, backslashed: Boolean) {
  val indent = List.fill(level)("  ").mkString

  def transform(char: Char): (PrettyPrinter, String) = {
    val woSlash = copy(backslashed = false)
    val (pp, f): (PrettyPrinter, PrettyPrinter => String) = char match {
      case '"' if inQuotes && !backslashed => (woSlash.copy(inQuotes = false), (_: PrettyPrinter) => s"$char")
      case '"' if !inQuotes => (woSlash.copy(inQuotes = true), (_: PrettyPrinter) => s"$char")
      case '\\' if inQuotes && !backslashed => (copy(backslashed = true), (_: PrettyPrinter) => s"$char")

      case ',' if !inQuotes => (woSlash, (p: PrettyPrinter) => s",\n${p.indent}")
      case '(' if !inQuotes => (woSlash.copy(level = level + 1), (p: PrettyPrinter) => s"(\n${p.indent}")
      case ')' if !inQuotes => (woSlash.copy(level = level - 1), (p: PrettyPrinter) => s"\n${p.indent})")
      case _ => (woSlash, (_: PrettyPrinter) => s"$char")
    }
    (pp, f(pp))
  }
}

trait BaseDisableToStringPlugin { self =>
  @volatile protected var configuredTypes = Set.empty[Regex]

  protected final def parseOpts(opts: List[String], error: String => Unit): Unit =
    opts.foreach(opt => opt.split(':').toList match {
      case "all" :: Nil => self.synchronized { configuredTypes = configuredTypes + ".*".r }
      case s"literal=$s" :: Nil => self.synchronized { configuredTypes = configuredTypes + ("\\Q" ++ s ++ "\\E").r }
      case s"regex=$s" :: Nil => self.synchronized { configuredTypes = configuredTypes + s.r }
      case _ => error(s"disableToString: invalid option `$opt`")
    })

  protected final def prettyPrint(raw: String): String =
    raw.foldLeft((PrettyPrinter(0, false, false), new StringBuilder(""))) { case ((pp, sb), char) =>
      val (newPP, res) = pp.transform(char)
      (newPP, sb.append(res))
    }._2.toString.replaceAll("""\(\s+\)""", "()")

  protected final def code(s: String): String = s"`$s`"

  protected final lazy val catsShow = "cats.Show"
  protected final lazy val scalaz = "scalaz"
  protected final lazy val scalazShow = s"$scalaz.Show"
}

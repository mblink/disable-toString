package bl

import scala.util.chaining._
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

private[bl] abstract class DisableToStringPluginHelper[Tree, Type, Evidence] { self =>
  @volatile var configuredTypes = Set.empty[Regex]

  final def parseOpts(opts: List[String], error: String => Unit): Unit =
    opts.foreach(opt => opt.split(':').toList match {
      case "all" :: Nil => self.synchronized { configuredTypes = configuredTypes + ".*".r }
      case s"literal=$s" :: Nil => self.synchronized { configuredTypes = configuredTypes + ("\\Q" ++ s ++ "\\E").r }
      case s"regex=$s" :: Nil => self.synchronized { configuredTypes = configuredTypes + s.r }
      case _ => error(s"disableToString: invalid option `$opt`")
    })

  final def prettyPrint(raw: String): String =
    raw.foldLeft((PrettyPrinter(0, false, false), new StringBuilder(""))) { case ((pp, sb), char) =>
      val (newPP, res) = pp.transform(char)
      (newPP, sb.append(res))
    }._2.toString.replaceAll("""\(\s+\)""", "()")

  final def code(s: String): String = s"`$s`"

  final lazy val catsShow = "cats.Show"
  final lazy val scalaz = "scalaz"
  final lazy val scalazShow = s"$scalaz.Show"

  val NoType: Type

  def treeType(tree: Tree): Type

  def fullTypeName(tpe: Type)(implicit ev: Evidence): String

  def eqTypes(a: Type, b: Type)(implicit ev: Evidence): Boolean
  def subTypeOf(a: Type, b: Type)(implicit ev: Evidence): Boolean

  def dealiasType(tpe: Type)(implicit ev: Evidence): Type

  def widenType(tpe: Type)(implicit ev: Evidence): Type

  final def normalizeType(tpe: Type)(implicit ev: Evidence): Type =
    Option(tpe).fold[Type](NoType)(t => widenType(dealiasType(t)))

  def stringType(implicit ev: Evidence): Type

  def optionalType(name: String)(implicit ev: Evidence): Option[Type]

  final def stringTypes(implicit ev: Evidence): Set[Type] =
    Set(stringType) ++ optionalType(s"$catsShow.Shown") ++ optionalType(s"$scalaz.Cord")

  final def isStringOrShownType(tpe: Type)(implicit ev: Evidence): (Boolean, Type) =
    tpe match {
      case null => (false, NoType)
      case t if eqTypes(t, stringType) => (true, t)
      case _ => normalizeType(tpe).pipe(t => (stringTypes.exists(subTypeOf(t, _)), t))
    }

  final def isStringOrShownTree(tree: Tree)(implicit ev: Evidence): (Boolean, Type) =
    isStringOrShownType(treeType(tree))

  object StringOrShownType {
    def unapply(tree: Tree)(implicit ev: Evidence): Option[Type] =
      isStringOrShownTree(tree).pipe { case (b, t) => if (b) Some(t) else None }
  }

  object DisabledType {
    def unapply(tpe: Type)(implicit ev: Evidence): Option[Type] = {
      val (stringOrShown, trueType) = isStringOrShownType(tpe)
      val tpeName = fullTypeName(trueType)
      if (!stringOrShown && configuredTypes.exists(_.findFirstIn(tpeName).nonEmpty)) Some(trueType) else None
    }

    def unapply(tree: Tree)(implicit ev: Evidence, d: DummyImplicit): Option[Type] = unapply(treeType(tree))
  }
}

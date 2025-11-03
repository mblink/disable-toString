package bl

import scala.annotation.tailrec
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.Reporting.WarningCategory
import scala.util.Try
import scala.util.chaining._

class DisableToStringPlugin(override val global: Global) extends Plugin with BaseDisableToStringPlugin { self =>
  import global.{Try => _, _}

  val name = "disableToString"
  val description = "Warns when calling `.toString` or interpolating a non-String value"

  override def init(opts: List[String], error: String => Unit): Boolean = {
    parseOpts(opts, error)
    true
  }

  private def showTree(tree: Tree, pretty: Boolean): String =
    if (pretty) prettyPrint(showRaw(tree)) else showRaw(tree)

  private def debugStr(name: String, tree: Tree, pretty: Boolean = true): String =
    s"===\n$name ${tree.pos}:\n${show(tree)}\n${showTree(tree, pretty)}"

  protected def debug(args: (String, Any)*): Unit = println(s"""
    |********************************************************************************
    |${args.map { case (s, v) => if (v.isInstanceOf[Tree]) debugStr(s, v.asInstanceOf[Tree]) else s"$s: $v" }.mkString("\n\n")}
    |********************************************************************************
    |""".stripMargin)

  protected def debug(name: String, tree: Tree, pretty: Boolean = true): Unit =
    println(debugStr(name, tree, pretty))

  @tailrec private def dealiasType(t: Type): Type = t.dealias match {
    case x if x == t => x
    case x => dealiasType(x)
  }

  @tailrec private def widenType(t: Type): Type = t.widen match {
    case x if x == t => x
    case x => widenType(x)
  }

  private def normalizeType(tpe: Type): Type =
    Option(tpe).fold[Type](NoType)(t => widenType(dealiasType(t)))

  private lazy val STRING_TPE: Type = global.typeOf[String]
  private lazy val STRING_TPES: Set[Type] =
    (Set(STRING_TPE) ++
      Try(global.rootMirror.staticClass(s"$catsShow.Shown").toType).toOption ++
      Try(global.rootMirror.staticClass(s"$scalaz.Cord").toType).toOption)

  private lazy val ITERABLE_ONCE_TPE = typeOf[IterableOnce[Unit]].typeConstructor

  private def isStringOrShown(tpe: Type): (Boolean, Type) =
    tpe match {
      case null => (false, NoType)
      case t if t =:= STRING_TPE => (true, t)
      case _ => normalizeType(tpe).pipe(t => (STRING_TPES.exists(t <:< _), t))
    }

  private def isStringOrShown(tree: Tree): (Boolean, Type) =
    isStringOrShown(tree.tpe)

  private object StringOrShownType {
    def unapply(tree: Tree): Option[Type] =
      isStringOrShown(tree).pipe { case (b, t) => if (b) Some(t) else None }
  }

  private object IterableOnceType {
    def unapply(tree: Tree): Option[Type] =
      normalizeType(tree.tpe) match {
        case t @ TypeRef(_, _, List(tpe)) if t.typeConstructor <:< ITERABLE_ONCE_TPE => Some(tpe)
        case _ => None
      }
  }

  private object DisabledType {
    def unapply(tpe: Type): Option[Type] = {
      val (stringOrShown, trueType) = isStringOrShown(tpe)
      val tpeName = trueType.typeSymbol.fullName
      if (!stringOrShown && configuredTypes.exists(_.findFirstIn(tpeName).nonEmpty)) Some(trueType) else None
    }

    def unapply(tree: Tree): Option[Type] = unapply(tree.tpe)
  }

  private lazy val CATS_SHOW_TPE: Option[ClassSymbol] = Try(global.rootMirror.staticClass(catsShow)).toOption
  private lazy val SCALAZ_SHOW_TPE: Option[ClassSymbol] = Try(global.rootMirror.staticClass(scalazShow)).toOption
  private lazy val SHOW_TPES: Set[Symbol] = Set() ++ CATS_SHOW_TPE ++ SCALAZ_SHOW_TPE

  private def isShowTpe(tree: Tree): Boolean =
    SHOW_TPES.contains(tree.tpe.typeSymbol)

  private lazy val SHOW_FNS: Set[Symbol] = Set() ++
    CATS_SHOW_TPE.map(_.companion.asModule.moduleClass.asClass.toType.decl(TermName("show"))) ++
    Set("show", "shows").flatMap(f => SCALAZ_SHOW_TPE.map(_.companion.asModule.moduleClass.asClass.toType.decl(TermName(f))))

  private def isShowFn(fn: Tree): Boolean =
    SHOW_FNS.contains(fn.symbol)

  private def checkTree(tree: Tree): Unit =
    tree match {
      // Disallow calls to `.toString` on disabled types
      case Select(t @ DisabledType(tpe), TermName("toString")) =>
        runReporting.warning(
          t.pos,
          s"Use a ${code("cats.Show")} instance instead of ${code(s"$tpe.toString")}",
          WarningCategory.Other,
          "")

      // Disallow string concatenation of disabled types
      case Apply(Select(lhs @ StringOrShownType(_), TermName("$plus")), rhss) =>
        rhss.collect {
          case t @ DisabledType(tpe) => runReporting.warning(
            t.pos,
            s"Only strings can be concatenated. Consider defining a ${code(s"$catsShow[$tpe]")} " ++
              s"and using ${code("""show"..."""")} from ${code("cats.syntax.show._")}",
            WarningCategory.Other,
            "")
        }: Unit

        checkTree(lhs)
        rhss.foreach(checkTree)

      // Disallow `mkString` calls on iterables of disabled types
      case Select(t @ IterableOnceType(DisabledType(_)), TermName("mkString")) =>
        runReporting.warning(
          t.pos,
          s"Use ${code(s"cats.Foldable[${t.tpe.typeConstructor}].mkString_")} instead of ${code(s"${t.tpe}.mkString")}\n" ++
          s"or convert elements to ${code("String")}s before calling ${code("mkString")}",
          WarningCategory.Other,
          "")

      // Allow the above when defining a `Show` instance
      case Template(tpes, _, _) if tpes.exists(isShowTpe) =>
        ()

      // Allow the above when inside a `Show.show` or `Show.shows` call
      case Apply(fn, _) if isShowFn(fn) =>
        ()

      case _ =>
        tree.children.foreach(checkTree)
    }


  private object phase extends PluginComponent {
    override val phaseName: String = self.name
    override val global: self.global.type = self.global
    override final def newPhase(prev: Phase): Phase = new StdPhase(prev) {
      override def apply(unit: CompilationUnit): Unit = checkTree(unit.body)
    }

    override val runsAfter: List[String] = List("typer")
  }

  override lazy val components: List[PluginComponent] = List(phase)
}

package bl

import scala.annotation.tailrec
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.Reporting.WarningCategory
import scala.util.Try

class DisableToStringPlugin(override val global: Global) extends Plugin { self =>
  import global.{NoType => _, Try => _, _}

  val name = "disableToString"
  val description = "Warns when calling `.toString` or interpolating a non-String value"

  private object helper extends DisableToStringPluginHelper[Tree, Type, DummyImplicit] {
    val NoType = global.NoType

    def treeType(tree: Tree): Type = tree.tpe

    def fullTypeName(tpe: Type)(implicit ev: DummyImplicit): String = tpe.typeSymbol.fullName

    def eqTypes(a: Type, b: Type)(implicit ev: DummyImplicit): Boolean = a =:= b
    def subTypeOf(a: Type, b: Type)(implicit ev: DummyImplicit): Boolean = a <:< b

    @tailrec def dealiasType(t: Type)(implicit ev: DummyImplicit): Type = t.dealias match {
      case x if x == t => x
      case x => dealiasType(x)
    }

    @tailrec def widenType(t: Type)(implicit ev: DummyImplicit): Type = t.widen match {
      case x if x == t => x
      case x => widenType(x)
    }

    def stringType(implicit ev: DummyImplicit): Type = global.typeOf[String]

    def optionalType(name: String)(implicit ev: DummyImplicit): Option[Type] =
      Try(global.rootMirror.staticClass(name).toType).toOption
  }

  import helper._

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

  private lazy val ITERABLE_ONCE_TPE = typeOf[IterableOnce[Unit]].typeConstructor

  private object IterableOnceType {
    def unapply(tree: Tree): Option[Type] =
      normalizeType(tree.tpe) match {
        case t @ TypeRef(_, _, List(tpe)) if t.typeConstructor <:< ITERABLE_ONCE_TPE => Some(tpe)
        case _ => None
      }
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

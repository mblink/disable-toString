package bl

import dotty.tools.dotc.ast.tpd.*
import dotty.tools.dotc.core.{Flags, Symbols}
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Names.{Name, termName, TermName}
import dotty.tools.dotc.core.Types.{AppliedType, Type}
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.report
import dotty.tools.dotc.transform.MegaPhase
import scala.annotation.tailrec

class DisableToStringPlugin extends StandardPlugin { self =>
  val name = "disableToString"
  val description = "Warns when calling `.toString` or interpolating a non-String value"

  private object helper extends DisableToStringPluginHelper[Tree, Type, Context] {
    val NoType = dotty.tools.dotc.core.Types.NoType

    def treeType(tree: Tree): Type = tree.tpe

    def fullTypeName(tpe: Type)(implicit ev: Context): String = tpe.typeSymbol.fullName.toString

    def eqTypes(a: Type, b: Type)(implicit ev: Context): Boolean = a =:= b
    def subTypeOf(a: Type, b: Type)(implicit ev: Context): Boolean = a <:< b

    @tailrec def dealiasType(t: Type)(using ev: Context): Type = t.dealias match {
      case x if x == t => x
      case x => dealiasType(x)
    }

    @tailrec def widenType(t: Type)(using ev: Context): Type = t.widen match {
      case x if x == t => x
      case x => widenType(x)
    }

    def stringType(implicit ev: Context): Type = Symbols.requiredClassRef("java.lang.String")

    def optionalClassSymbol(name: String)(using Context): Option[Symbols.ClassSymbol] =
      Symbols.getClassIfDefined(name) match {
        case sym: Symbols.ClassSymbol => Some(sym)
        case _ => None
      }

    def optionalType(name: String)(implicit ev: Context): Option[Type] = optionalClassSymbol(name).map(_.typeRef)
  }

  import helper.*

  override def init(opts: List[String]): List[PluginPhase] = {
    parseOpts(opts, System.err.println(_))
    List(phase)
  }

  private def anyType(using Context): Type =
    Symbols.requiredClassRef("scala.Any")

  private def iterableType(using Context): Type =
    AppliedType(Symbols.requiredClassRef("scala.collection.Iterable"), List(anyType))

  private object IterableOnceType {
    def unapply(tree: Tree)(using Context): Option[(Type, Type)] =
      normalizeType(tree.tpe) match {
        case AppliedType(t, List(a)) if AppliedType(t, List(anyType)) <:< iterableType => Some((t, a))
        case _ => None
      }
  }

  private def catsShowType(using Context): Option[Symbols.ClassSymbol] = optionalClassSymbol(catsShow)
  private def scalazShowType(using Context): Option[Symbols.ClassSymbol] = optionalClassSymbol(scalazShow)
  private def showTypes(using Context): Set[Symbols.Symbol] = Set() ++ catsShowType ++ scalazShowType

  private def isShowType(tree: Tree)(using Context): Boolean =
    showTypes.contains(tree.tpe.typeSymbol)

  private def showFns(using Context): Set[Symbols.Symbol] = Set() ++
    catsShowType.map(_.companionModule.typeRef.decl(termName("show")).symbol) ++
    Set("show", "shows").flatMap(f => scalazShowType.map(_.companionModule.typeRef.decl(termName(f)).symbol))

  private def isShowFn(fn: Tree)(using Context): Boolean =
    showFns.contains(fn.symbol)

  @tailrec private def hasCaseClassOwner(sym: Symbols.Symbol)(using Context): Boolean =
    sym.owner match {
      case Symbols.NoSymbol => false
      case owner if owner.is(Flags.CaseClass) => true
      case owner => hasCaseClassOwner(owner)
    }

  private object TermName {
    def unapply(name: Name): Option[String] =
      Some(name).collect { case n: TermName => n.toString }
  }

  private object phase extends PluginPhase {
    val phaseName = self.name
    override val runsAfter = Set("interpolators")

    override protected def singletonGroup: MegaPhase = new MegaPhase(Array(this)) {
      override def transformTree(tree: Tree, start: Int)(using Context): Tree = {
        tree match {
          case t @ Select(DisabledType(tpe), TermName("toString")) =>
            report.warning(
              s"Use a ${code("cats.Show")} instance instead of ${code(s"${tpe.show}.toString")}",
              t/*,
              WarningCategory.Other,
              ""*/)
            super.transformTree(t, start)

          // Disallow string concatenation of disabled types
          case t @ Apply(Select(lhs @ StringOrShownType(_), plus @ TermName("+" | "$plus")), rhss) =>
            rhss.collect {
              case t @ DisabledType(tpe) =>
                report.warning(
                  s"Only strings can be concatenated. Consider defining a ${code(s"$catsShow[${tpe.show}]")} " ++
                    s"and using ${code("""show"..."""")} from ${code("cats.syntax.show._")}",
                  t/*,
                  WarningCategory.Other,
                  ""*/)
            }

            super.transformTree(t, start)

          // Disallow `mkString` calls on iterables of disabled types
          case t @ Select(IterableOnceType(iterableType, DisabledType(typeArg)), TermName("mkString")) =>
            report.warning(
              s"Use ${code(s"cats.Foldable[${iterableType.show}].mkString_")} instead of " ++
                s"${code(s"${iterableType.show}[${typeArg.show}].mkString")}\n" ++
                s"or convert elements to ${code("String")}s before calling ${code("mkString")}",
              t/*,
              WarningCategory.Other,
              ""*/)

            super.transformTree(t, start)

          // Allow the above when defining a `Show` instance
          case t @ Template(_, _, _, _) if t.parentsOrDerived.exists(isShowType) =>
            t

          // Allow the above when inside a `Show.show` or `Show.shows` call
          case t @ Apply(fn, _) if isShowFn(fn) =>
            t

          // Allow the above in `case class` generated methods
          case t @ DefDef(_, _, _, _) if t.symbol.is(Flags.Synthetic) && hasCaseClassOwner(t.symbol) =>
            t

          case t =>
            super.transformTree(t, start)
        }
      }
    }
  }
}

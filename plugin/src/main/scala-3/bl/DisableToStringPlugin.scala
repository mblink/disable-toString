package bl

import dotty.tools.dotc.ast.tpd.*
import dotty.tools.dotc.core.{Flags, Symbols}
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Names.{Name, termName, TermName}
import dotty.tools.dotc.core.Types.{AppliedType, NoType, Type}
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.report
import dotty.tools.dotc.transform.MegaPhase
import scala.annotation.tailrec
import scala.util.chaining.*

class DisableToStringPlugin extends StandardPlugin with BaseDisableToStringPlugin { self =>
  val name = "disableToString"
  val description = "Warns when calling `.toString` or interpolating a non-String value"

  override def init(opts: List[String]): List[PluginPhase] = {
    parseOpts(opts, System.err.println(_))
    List(Phase())
  }

  @tailrec private def dealiasType(t: Type)(using Context): Type = t.dealias match {
    case x if x == t => x
    case x => dealiasType(x)
  }

  @tailrec private def widenType(t: Type)(using Context): Type = t.widen match {
    case x if x == t => x
    case x => widenType(x)
  }

  private def normalizeType(tpe: Type)(using Context): Type =
    Option(tpe).fold[Type](NoType)(t => widenType(dealiasType(t)))

  private def stringType(using Context): Type = Symbols.requiredClassRef("java.lang.String")

  private def optionalClassSymbol(name: String)(using Context): Option[Symbols.ClassSymbol] =
    Symbols.getClassIfDefined(name) match {
      case sym: Symbols.ClassSymbol => Some(sym)
      case _ => None
    }

  private def optionalType(name: String)(using Context): Option[Type] =
    optionalClassSymbol(name).map(_.typeRef)

  private def stringTypes(using Context): Set[Type] =
    Set(stringType) ++ optionalType(s"$catsShow.Shown") ++ optionalType(s"$scalaz.Cord")

  private def anyType(using Context): Type =
    Symbols.requiredClassRef("scala.Any")

  private def iterableType(using Context): Type =
    AppliedType(Symbols.requiredClassRef("scala.collection.Iterable"), List(anyType))

  private def isStringOrShown(tpe: Type)(using Context): (Boolean, Type) =
    tpe match {
      case null => (false, NoType)
      case t if t =:= stringType => (true, t)
      case _ => normalizeType(tpe).pipe(t => (stringTypes.exists(t <:< _), t))
    }

  private def isStringOrShown(tree: Tree)(using Context): (Boolean, Type) =
    isStringOrShown(tree.tpe)

  private object StringOrShownType {
    def unapply(tree: Tree)(using Context): Option[Type] =
      isStringOrShown(tree).pipe { case (b, t) => if (b) Some(t) else None }
  }

  private object IterableOnceType {
    def unapply(tree: Tree)(using Context): Option[(Type, Type)] =
      normalizeType(tree.tpe) match {
        case AppliedType(t, List(a)) if AppliedType(t, List(anyType)) <:< iterableType => Some((t, a))
        case _ => None
      }
  }

  private object DisabledType {
    def unapply(tpe: Type)(using Context): Option[Type] = {
      val (stringOrShown, trueType) = isStringOrShown(tpe)
      val tpeName = trueType.typeSymbol.fullName.toString.replace("$.", ".")
      if (!stringOrShown && configuredTypes.exists(_.findFirstIn(tpeName).nonEmpty)) Some(trueType) else None
    }

    def unapply(tree: Tree)(using Context): Option[Type] = unapply(tree.tpe)
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

  private class Phase extends PluginPhase {
    val phaseName = self.name
    override val runsAfter = Set("interpolators")

    override protected def singletonGroup: MegaPhase = new MegaPhase(Array(this)) {
      override def transformTree(tree: Tree, start: Int)(using Context): Tree = {
        tree match {
          case t @ Select(DisabledType(tpe), TermName("toString")) =>
            report.warning(
              s"Use a ${code("cats.Show")} instance instead of ${code(s"${tpe.show}.toString")}",
              t)
            super.transformTree(t, start)

          // Disallow string concatenation of disabled types
          case t @ Apply(Select(lhs @ StringOrShownType(_), TermName("+" | "$plus")), rhss) =>
            rhss.foreach {
              case t @ DisabledType(tpe) =>
                report.warning(
                  s"Only strings can be concatenated. Consider defining a ${code(s"$catsShow[${tpe.show}]")} " ++
                    s"and using ${code("""show"..."""")} from ${code("cats.syntax.show._")}",
                  t)

              case _ => ()
            }

            super.transformTree(t, start)

          // Disallow `mkString` calls on iterables of disabled types
          case t @ Select(IterableOnceType(iterableType, DisabledType(typeArg)), TermName("mkString")) =>
            report.warning(
              s"Use ${code(s"cats.Foldable[${iterableType.show}].mkString_")} instead of " ++
                s"${code(s"${iterableType.show}[${typeArg.show}].mkString")}\n" ++
                s"or convert elements to ${code("String")}s before calling ${code("mkString")}",
              t)

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

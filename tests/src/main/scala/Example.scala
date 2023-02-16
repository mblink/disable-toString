import cats.syntax.show._

case class Foo(s: String)

case class Bar(i: Int)
object Bar {
  implicit val scalazNewShowToString: scalaz.Show[Bar] = new scalaz.Show[Bar] {
    override def show(b: Bar): scalaz.Cord = scalaz.Cord("Bar(" ++ b.i.toString ++ ")")
  }

  val scalazNewShowInterp: scalaz.Show[Bar] = new scalaz.Show[Bar] {
    override def show(b: Bar): scalaz.Cord = scalaz.Cord(s"Bar(${b.i})")
  }

  val scalazNewShowToStringAliased: scalaz.Show[Bar] = new scalaz.Show[Bar] {
    override def show(b: Bar): scalaz.Cord = scalaz.Cord("Bar(" ++ b.i.toString ++ ")")
  }

  val scalazNewShowInterpAliased: scalaz.Show[Bar] = new scalaz.Show[Bar] {
    override def show(b: Bar): scalaz.Cord = scalaz.Cord(s"Bar(${b.i})")
  }

  val scalazShowFuncToString: scalaz.Show[Bar] = scalaz.Show.show(b => scalaz.Cord("Bar(" ++ b.i.toString ++ ")"))
  val scalazShowFuncInterp: scalaz.Show[Bar] = scalaz.Show.show(b => scalaz.Cord(s"Bar(${b.i})"))

  val scalazShowFuncToStringAliased: scalaz.Show[Bar] = scalaz.Show.show(b => scalaz.Cord("Bar(" ++ b.i.toString ++ ")"))
  val scalazShowFuncInterpAliased: scalaz.Show[Bar] = scalaz.Show.show(b => scalaz.Cord(s"Bar(${b.i})"))

  val scalazShowsFuncToString: scalaz.Show[Bar] = scalaz.Show.shows(b => "Bar(" ++ b.i.toString ++ ")")
  val scalazShowsFuncInterp: scalaz.Show[Bar] = scalaz.Show.shows(b => s"Bar(${b.i})")

  val scalazShowsFuncToStringAliased: scalaz.Show[Bar] = scalaz.Show.shows(b => "Bar(" ++ b.i.toString ++ ")")
  val scalazShowsFuncInterpAliased: scalaz.Show[Bar] = scalaz.Show.shows(b => s"Bar(${b.i})")

  implicit val catsNewShowToString: cats.Show[Bar] = new cats.Show[Bar] {
    override def show(b: Bar): String = "Bar(" ++ b.i.toString ++ ")"
  }

  val catsNewShowInterp: cats.Show[Bar] = new cats.Show[Bar] {
    override def show(b: Bar): String = s"Bar(${b.i})"
  }

  val catsNewShowToStringAliased: cats.Show[Bar] = new cats.Show[Bar] {
    override def show(b: Bar): String = "Bar(" ++ b.i.toString ++ ")"
  }

  val catsNewShowInterpAliased: cats.Show[Bar] = new cats.Show[Bar] {
    override def show(b: Bar): String = s"Bar(${b.i})"
  }

  val catsShowFuncToString: cats.Show[Bar] = cats.Show.show(b => "Bar(" ++ b.i.toString ++ ")")
  val catsShowFuncInterp: cats.Show[Bar] = cats.Show.show(b => s"Bar(${b.i})")

  val catsShowFuncToStringAliased: cats.Show[Bar] = cats.Show.show(b => "Bar(" ++ b.i.toString ++ ")")
  val catsShowFuncInterpAliased: cats.Show[Bar] = cats.Show.show(b => s"Bar(${b.i})")
}

object DisableToString {
  val stringLitVal = "1"
  val stringVal = 1.toString: @annotation.nowarn("msg=instead of `Int.toString`")
  def stringDef() = "1"
  val interpString = s"$stringLitVal b $stringVal c ${stringDef()} d"

  val intVal = 1
  def intDef() = 1
  val interpIntLit = s"a ${1: @strConcatOk} b"
  val interpIntVal = s"a ${intVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Int")} b"
  val interpIntDef = s"a ${intDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Int")} b"

  val boolVal = true
  def boolDef() = false
  val interpBoolLit = s"a ${true: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Boolean")} b"
  val interpBoolVal = s"a ${boolVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Boolean")} b"
  val interpBoolDef = s"a ${boolDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Boolean")} b"

  val fooVal = Foo("foo")
  def fooDef() = Foo("bar")
  val interpFooLit = s"a ${Foo("baz"): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Foo")} b"
  val interpFooVal = s"a ${fooVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Foo")} b"
  val interpFooDef = s"a ${fooDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Foo")} b"
  val interpFooValMember = s"a ${fooVal.s} b"
  val interpFooDefMember = s"a ${fooDef().s} b"

  val barVal = Bar(1)
  def barDef() = Bar(2)
  val interpBarLit = s"a ${Bar(3): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Bar")} b"
  val interpBarVal = s"a ${barVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Bar")} b"
  val interpBarDef = s"a ${barDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Bar")} b"
  val interpBarValMember = s"a ${barVal.i: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Int")} b"
  val interpBarDefMember = s"a ${barDef().i: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Int")} b"
  val showInterpBarLit = show"a ${Bar(3)} b"
  val showInterpBarVal = show"a $barVal b"
  val showInterpBarDef = show"a ${barDef()} b"

  val stringLitValPlusIntVal = "1" + (intVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Int"))
  val stringLitValPlusIntDef = "1" + (intDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Int"))
  val stringLitValPlusBoolVal = "1" + (boolVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Boolean"))
  val stringLitValPlusBoolDef = "1" + (boolDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Boolean"))
  val stringLitValPlusFooVal = "1" + (fooVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Foo"))
  val stringLitValPlusFooDef = "1" + (fooDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Foo"))
  val stringLitValPlusBarVal = "1" + (barVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Bar"))
  val stringLitValPlusBarDef = "1" + (barDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Bar"))

  def stringLitDefPlusIntVal() = "1" + (intVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Int"))
  def stringLitDefPlusIntDef() = "1" + (intDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Int"))
  def stringLitDefPlusBoolVal() = "1" + (boolVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Boolean"))
  def stringLitDefPlusBoolDef() = "1" + (boolDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Boolean"))
  def stringLitDefPlusFooVal() = "1" + (fooVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Foo"))
  def stringLitDefPlusFooDef() = "1" + (fooDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Foo"))
  def stringLitDefPlusBarVal() = "1" + (barVal: @annotation.nowarn("msg=Consider defining a `cats.Show\\[Bar"))
  def stringLitDefPlusBarDef() = "1" + (barDef(): @annotation.nowarn("msg=Consider defining a `cats.Show\\[Bar"))

  @annotation.nowarn("msg=Consider defining a `cats.Show\\[S") def badSingleton[S <: Singleton](s: S) = s"a $s b"
  def goodSingleton[S <: Singleton with String](s: S) = s"a $s b"

  val cordToString = scalaz.Cord("foo").toString
  val shownToString = cats.Show.Shown("bar").toString

  val cordInterp = s"a ${scalaz.Cord("foo")} b"
  val shownInterp = s"a ${cats.Show.Shown("bar")} b"

  def cordFToString(x: scalaz.Cord) = s"a $x b"
  def shownFToString(x: cats.Show.Shown) = s"a $x b"

  val showInterpToString = show"a ${Bar(1)} c".toString
  val showInterpInterp = s"${show"a ${Bar(1)} c"}"

  def optionF(o: Option[Int]) = s"a ${o.map(i => show"a $i b".toString).getOrElse("")} b"

  val stringMkString = List("1", "2", "3").mkString(", ")
  val intMkString = List(1, 2, 3).mkString(", "): @annotation.nowarn("msg=Use `cats.Foldable\\[List\\].mkString_`")
}

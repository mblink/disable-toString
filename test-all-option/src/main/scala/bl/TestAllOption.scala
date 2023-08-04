package bl

object TestAllOption {
  @annotation.nowarn("msg=Consider defining a `cats.Show\\[S") def badSingleton[S <: Singleton](s: S) = s"a $s b"
}

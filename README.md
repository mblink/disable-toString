# Disable `toString` plugin

This is a Scala 2 and 3 compiler plugin to trigger compiler warnings (which can be turned into errors with
`-Xfatal-warnings`), when certain types are converted to `String`s via global methods like `.toString`, string
interpolation, and calls to `mkString` on iterables of that type.

## Install

Add the resolver and compiler plugin dependency to your `build.sbt`:

```scala
resolvers += "bondlink-maven-repo" at "https://raw.githubusercontent.com/mblink/maven-repo/main"
addCompilerPlugin("bondlink" %% "disable-to-string-plugin" % "0.1.1")
```

## Configure

To configure which types trigger warnings, pass additional flags to `scalac`. Available options are:

| Option | Behavior |
|--------|----------|
|`-P:disableToString:all`|Disable string conversions on all types|
|`-P:disableToString:literal=com.example.Type`|Disable string conversions for a type named exactly `com.example.Type`|
|`-P:disableToString:regex=com\\.example\\.(Example)?Type`|Disable string conversions for types matching the given regex|

You can add these in SBT with

```scala
scalacOptions += "-P:disableToString:all"
```

## Use

Once you've configured types through `scalac` options, you should see warnings reported when compiling code:

```scala
val test1 = 1.toString
         /* ^
            Use a `cats.Show` instance instead of `Int.toString` */

val test2 = s"foo ${1}"
                 /* ^
                    Only strings can be concatenated. Consider defining a `cats.Show[Int]` and using `show"..."` from
                    `cats.syntax.show._` */

val test3 = List(1, 2).mkString(",")
              /* ^
                 Use `cats.Foldable[List].mkString_` instead of `List[Int].mkString`
                 or convert elements to `String`s before calling `mkString` */
```

The plugin encourages use of `cats.Show`, but also works with `scalaz.Show`. All of the cases above are allowed
within `Show` instances so code like this will compile without warnings:

```scala
new cats.Show[Int] { def show(i: Int) = i.toString }
cats.Show.show((i: Int) => i.toString)

new scalaz.Show[Int] { def show(i: Int) = scalaz.Cord(i.toString) }
scalaz.Show.show((i: Int) => i.toString)
```

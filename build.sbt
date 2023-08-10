Global / onChangedBuildSource := ReloadOnSourceChanges

val scalaVersions = Seq("2.13.11", "3.3.0")

def foldScalaV[A](scalaVersion: String)(_2: => A, _3: => A): A =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, _)) => _2
    case Some((3, _)) => _3
  }

val baseSettings = Seq(
  crossScalaVersions := scalaVersions,
  scalaVersion := scalaVersions.find(_.startsWith("3.")).get,
  organization := "bondlink",
  version := "0.2.2",
  gitPublishDir := file("/src/maven-repo"),
)

lazy val disableToStringPlugin = project.in(file("."))
  .settings(baseSettings)
  .settings(
    name := "disable-to-string-plugin",
    libraryDependencies += foldScalaV(scalaVersion.value)(
      "org.scala-lang" % "scala-compiler",
      "org.scala-lang" %% "scala3-compiler",
    ) % scalaVersion.value % "provided",
  )

val testSettingsNoSrc = baseSettings ++ Seq(
  publish := {},
  publishLocal := {},
  gitRelease := {},
  scalacOptions ++= {
    val jar = (disableToStringPlugin / Compile / Keys.`package`).value
    Seq(
      s"-Xplugin:${jar.getAbsolutePath}",
      s"-Jdummy$name=${jar.lastModified}",
    )
  },
  libraryDependencies ++= Seq(
    "org.scalaz" %% "scalaz-core" % "7.3.7",
    "org.typelevel" %% "cats-core" % "2.9.0",
  ),
  resolvers += "bondlink-maven-repo" at "https://raw.githubusercontent.com/mblink/maven-repo/main",
)

val testSettings = testSettingsNoSrc ++ Seq(
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "tests" / "src" / "main" / "scala",
)

lazy val testAllOption = project.in(file("test-all-option"))
  .settings(testSettings)
  .settings(
    scalacOptions += "-P:disableToString:all",
  )
  .aggregate(disableToStringPlugin)

lazy val testLiteralOption = project.in(file("test-literal-option"))
  .settings(testSettings)
  .settings(
    scalacOptions ++= Seq(
      "-P:disableToString:literal=scala.Boolean",
      "-P:disableToString:literal=scala.Int",
      "-P:disableToString:literal=bl.Foo",
      "-P:disableToString:literal=bl.Bar",
      "-P:disableToString:literal=bl.testObj.Baz",
    )
  )
  .aggregate(disableToStringPlugin)

lazy val testRegexOption = project.in(file("test-regex-option"))
  .settings(testSettings)
  .settings(
    scalacOptions += "-P:disableToString:regex=^(scala\\.Boolean|scala\\.Int|bl\\.(Foo|Bar|testObj\\.Baz))$",
  )
  .aggregate(disableToStringPlugin)

lazy val tests = project.in(file("all-tests"))
  .settings(testSettingsNoSrc)
  .aggregate(testAllOption, testLiteralOption, testRegexOption)

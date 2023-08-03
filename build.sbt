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
  version := "0.1.1",
  gitPublishDir := file("/src/maven-repo"),
)

lazy val disableToStringPlugin = project.in(file("."))
  .settings(baseSettings)
  .settings(
    name := "disable-to-string-plugin",
    libraryDependencies += foldScalaV(scalaVersion.value)(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
      "org.scala-lang" %% "scala3-compiler" % scalaVersion.value % "provided",
    ),
  )

lazy val tests = project.in(file("tests"))
  .settings(baseSettings)
  .settings(
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
    // addCompilerPlugin("bondlink" %% "nowarn-plugin" % "1.0.0"),
    scalacOptions ++= Seq(
      "-P:disableToString:all",
      // "-P:nowarn:toStringOk:msg=Use a `cats.Show` instance instead of `.*\\.toString`",
      // "-P:nowarn:strConcatOk:msg=Only strings can be concatenated. Consider defining a `cats.Show"
    )
  )
  .aggregate(disableToStringPlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges

val baseSettings = Seq(
  scalaVersion := "2.13.10",
  organization := "bondlink",
  version := "0.1.0",
  gitPublishDir := file("/src/maven-repo"),
)

lazy val disableToStringPlugin = project.in(file("."))
  .settings(baseSettings)
  .settings(
    name := "disable-to-string-plugin",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
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
    addCompilerPlugin("bondlink" %% "nowarn-plugin" % "1.0.0"),
    scalacOptions ++= Seq(
      "-P:disableToString:all",
      "-P:nowarn:toStringOk:msg=Use a `cats.Show` instance instead of `.*\\.toString`",
      "-P:nowarn:strConcatOk:msg=Only strings can be concatenated. Consider defining a `cats.Show"
    )
  )
  .aggregate(disableToStringPlugin)

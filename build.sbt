Global / onChangedBuildSource := ReloadOnSourceChanges

val scala2 = "2.13.18"
val scala3 = "3.3.7"

ThisBuild / scalaVersion := scala3

// GitHub Actions config
val javaVersions = Seq(8, 11, 17, 21, 25).map(v => JavaSpec.temurin(v.toString))

ThisBuild / githubWorkflowJavaVersions := javaVersions
ThisBuild / githubWorkflowArtifactUpload := false
ThisBuild / githubWorkflowBuildMatrixFailFast := Some(false)
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowPublishTargetBranches := Seq()

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Run(List("sbt Test/compile"), name = Some("test")),
)

def foldScalaV[A](scalaVersion: String)(_2: => A, _3: => A): A =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, _)) => _2
    case Some((3, _)) => _3
  }

val baseSettings = Seq(
  organization := "bondlink",
  version := "0.2.2",
  publish / skip := true,
)

baseSettings

val publishSettings = Seq(
  publish / skip := false,
  publishTo := Some("BondLink S3".at("s3://bondlink-maven-repo")),
)

def baseProj(id: String, nme: String) =
  sbt.internal.ProjectMatrix(id, file(id))
    .jvmPlatform(scalaVersions = Seq(scala2, scala3))
    .settings(baseSettings ++ Seq(name := nme))

lazy val disableToStringPlugin = baseProj("plugin", "disable-to-string-plugin")
  .settings(publishSettings ++ Seq(
    libraryDependencies += foldScalaV(scalaVersion.value)(
      "org.scala-lang" % "scala-compiler",
      "org.scala-lang" %% "scala3-compiler",
    ) % scalaVersion.value % "provided",
  ))

val testSettings = baseSettings ++ Seq(
  scalacOptions ++= Def.taskDyn {
    val scalaV = scalaVersion.value
    Def.task {
      val jar = (disableToStringPlugin.jvm(scalaV) / Compile / Keys.`package`).value
      Seq(
        s"-Xplugin:${jar.getAbsolutePath}",
        s"-Jdummy$name=${jar.lastModified}",
      )
    }
  }.value,
  libraryDependencies ++= Seq(
    "org.scalaz" %% "scalaz-core" % "7.3.9",
    "org.typelevel" %% "cats-core" % "2.13.0",
  ),
  resolvers += "bondlink-maven-repo" at "https://maven.bondlink-cdn.com",
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "tests" / "src" / "main" / "scala",
)

lazy val testAllOption = baseProj("test-all-option", "test-all-option")
  .settings(testSettings)
  .settings(
    scalacOptions += "-P:disableToString:all",
  )
  .aggregate(disableToStringPlugin)

lazy val testLiteralOption = baseProj("test-literal-option", "test-literal-option")
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

lazy val testRegexOption = baseProj("test-regex-option", "test-regex-option")
  .settings(testSettings)
  .settings(
    scalacOptions += "-P:disableToString:regex=^(scala\\.Boolean|scala\\.Int|bl\\.(Foo|Bar|testObj\\.Baz))$",
  )
  .aggregate(disableToStringPlugin)

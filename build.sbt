name := "testMacros"

version := "1.0"

val monocleVersion = "1.1.1"

lazy val testMacros = project in file(".") settings(commonSettings ++ mainSettings: _*) aggregate (main, macros)

lazy val macrosSpecificSettings = Seq(libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _))
lazy val mainSettings = Seq(
  mainClass in Compile := Some("Playground")
)
lazy val main = project in file("./main") settings (commonSettings: _*) dependsOn macros

lazy val macros = project in file("./macros") settings (commonSettings ++ macrosSpecificSettings: _*)

lazy val commonSettings = Defaults.coreDefaultSettings ++ Seq(
  scalaVersion := "2.11.6",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "2.2.2" % "test",
    "com.github.julien-truffaut"  %%  "monocle-core" % monocleVersion
  ),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full),
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature"
  )
)

    
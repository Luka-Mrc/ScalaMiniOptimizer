ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "rs.ftn.uns"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(Antlr4Plugin)
  .settings(
    name := "ScalaMiniOptimizer",

    Antlr4 / antlr4Version     := "4.13.2",
    Antlr4 / antlr4PackageName := Some("minioptimizer.parser.generated"),
    Antlr4 / antlr4GenVisitor  := true,
    Antlr4 / antlr4GenListener := false,

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),

    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
    ),

    Test / fork := true
  )

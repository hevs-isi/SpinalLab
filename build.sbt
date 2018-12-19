
lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.spinalhdl",
      scalaVersion := "2.11.6",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "hevsLab",
    libraryDependencies += "com.github.spinalhdl" % "spinalhdl-core_2.11" % "1.2.2",
    libraryDependencies += "com.github.spinalhdl" % "spinalhdl-lib_2.11" % "1.2.2",
    libraryDependencies += "jfree" % "jfreechart" % "1.0.13"
  ).dependsOn(vexRiscv)
lazy val vexRiscv = RootProject(file("ext/VexRiscv"))

addCompilerPlugin("org.scala-lang.plugins" % "scala-continuations-plugin_2.11.6" % "1.0.2")
scalacOptions += "-P:continuations:enable"
fork := true

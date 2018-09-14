
lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.spinalhdl",
      scalaVersion := "2.11.6",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "hevsLab"
  ).dependsOn(vexRiscv)
lazy val vexRiscv = RootProject(uri("git://github.com/SpinalHDL/VexRiscv.git#791608f6555a92f07632364fc2f100a97e1c447a"))


addCompilerPlugin("org.scala-lang.plugins" % "scala-continuations-plugin_2.11.6" % "1.0.2")
scalacOptions += "-P:continuations:enable"
fork := true

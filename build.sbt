
lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.spinalhdl",
      scalaVersion := "2.11.6",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "SpinalLab",
    libraryDependencies += "com.github.spinalhdl" % "spinalhdl-core_2.11" % "1.3.0",
    libraryDependencies += "com.github.spinalhdl" % "spinalhdl-lib_2.11" % "1.3.0",
    libraryDependencies += "jfree" % "jfreechart" % "1.0.13"
  ).dependsOn(vexRiscv)
lazy val vexRiscv = RootProject(file("ext/VexRiscv"))


fork := true

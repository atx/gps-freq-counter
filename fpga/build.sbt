organization := "atx.name"

version := "0.0-SNAPSHOT"

name := "gfc"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	"edu.berkeley.cs" %% "chisel3" % "3.0.2",
	"edu.berkeley.cs" %% "chisel-iotesters" % "1.1.2"
)

resolvers ++= Seq(
	Resolver.sonatypeRepo("snapshots"),
	Resolver.sonatypeRepo("releases")
)

unmanagedBase := baseDirectory.value / "lib"

initialCommands in console += "import chisel3._"

logBuffered in Test := false
parallelExecution in Test := false

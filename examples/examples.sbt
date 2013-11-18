import Http4sDependencies._

name := "http4s-examples"

description := "Examples of using http4s on various backends"

seq(Revolver.settings: _*)

libraryDependencies ++= Seq(
  logbackClassic,
  javaxServletApi,
  jettyServer,
  jettyServlet,
  jspApi % "runtime" // http://forums.yourkit.com/viewtopic.php?f=2&t=3733
)

mainClass in Revolver.reStart := Some("org.http4s.grizly.GrizzlyExample")

fork in run := true
import Http4sDependencies._

name := "http4s-examples"

description := "Examples of using http4s on various backends"

seq(Revolver.settings: _*)

libraryDependencies ++= Seq(
  logbackClassic,
  javaxServletApi,
  jettyServer,
  jettyServlet
)

mainClass in Revolver.reStart := Some("org.http4s.netty.Netty4Example")

fork in run := true
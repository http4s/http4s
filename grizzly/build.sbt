name := "http4s-grizzly"

description := "Glassfish Grizzly backend for http4s"

libraryDependencies ++= Seq(
  "play" %% "play-iteratees" % "2.1-RC3",
  "org.specs2" %% "specs2" % "1.13" % "test",
  "junit" % "junit" % "4.11" % "test",
  "org.glassfish.grizzly" % "grizzly-http-server" % "2.2.19"           
)
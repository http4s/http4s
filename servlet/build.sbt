name := "http4s-servlet"

description := "Servlet backend for http4s"

libraryDependencies ++= Seq(
  "play" %% "play-iteratees" % "2.1-RC3",
  "org.specs2" %% "specs2" % "1.13" % "test",
  "junit" % "junit" % "4.11" % "test",
  "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  "org.eclipse.jetty" % "jetty-server" % "8.1.8.v20121106" % "test",
  "org.eclipse.jetty" % "jetty-servlet" % "8.1.8.v20121106" % "test"
)
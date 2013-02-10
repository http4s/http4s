name := "http4s-core"

description := "Core http4s framework"

libraryDependencies ++= Seq(
  "play" %% "play-iteratees" % "2.1.0",
  "org.specs2" %% "specs2" % "1.13" % "test",
  "junit" % "junit" % "4.11" % "test"
)
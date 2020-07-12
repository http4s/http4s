scalacOptions := Seq(
  "-deprecation"
)

// For MimeLoader plugin. Dogfooding and hoping it doesn't clash with
// our other sbt plugins.
libraryDependencies ++= List(
  "com.eed3si9n" %% "treehugger" % "0.4.4",
  "io.circe" %% "circe-generic" % "0.13.0",
  "org.http4s" %% "http4s-blaze-client" % "0.21.5",
  "org.http4s" %% "http4s-circe" % "0.21.5",
)

// Hack around a binary conflict in scalameta's dependency on
// fastparse as specified by sbt-doctest-0.9.5.
libraryDependencies += "org.scalameta" %% "scalameta" % "4.3.19"

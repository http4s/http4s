scalacOptions := Seq(
  "-deprecation"
)

// For MimeLoader plugin. Dogfooding and hoping it doesn't clash with
// our other sbt plugins.
libraryDependencies ++= List(
  "com.eed3si9n" %% "treehugger" % "0.4.3",
  "io.circe" %% "circe-generic" % "0.11.1",
  "org.http4s" %% "http4s-blaze-client" % "0.20.3",
  "org.http4s" %% "http4s-circe" % "0.20.3",
)

// Hack around a binary conflict in scalameta's dependency on
// fastparse as specified by sbt-doctest-0.9.5.
libraryDependencies += "org.scalameta" %% "scalameta" % "4.2.0"

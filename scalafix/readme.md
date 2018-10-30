# Scalafix rules for http4s

## Prerequisites
1. [Add scalafix to your repo](https://scalacenter.github.io/scalafix/docs/users/installation.html)
2. Add the following to your `build.sbt`
```scala
scalafixDependencies in ThisBuild += "com.alessandromarrella" %% "scalafix" % "0.0.1-SNAPSHOT"
addCompilerPlugin(scalafixSemanticdb)
scalacOptions += "-Yrangepos"
```

## Migrate from http4s 0.18 to 0.20
- Update the http4s dependencies to the latest 0.20 version
- Run `sbt` and in the REPL run: `scalafixEnable` and then `scalafix Http4s020To018`
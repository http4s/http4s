---
menu: main
title: Upgrading from 0.18
weight: 2
---

1. Make sure your scala version is >= `2.11.12` or `2.12.7` ([no 2.13 yet](https://github.com/scalameta/scalameta/issues/1695))
2. Add the scalafix plugin to your `project/plugins.sbt`
`addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.0")`
3. Add this line to your `build.sbt`
```
scalafixDependencies in ThisBuild += "org.http4s" %% "http4s-scalafix" % http4s020Version
```
4. Run
`sbt ";scalafixEnable; scalafix Http4s018To020"`
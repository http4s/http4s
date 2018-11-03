---
menu: main
title: Upgrading from 0.18
weight: 2
---

1. Make sure your scala version is >= `2.11.12` or `2.12.7`
2. Add the scalafix plugin to your `project/plugins.sbt`
```scala
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.0")
```
3. Add this line to your `build.sbt`
```scala
scalafixDependencies in ThisBuild += "org.http4s" %% "http4s-scalafix" % http4s020Version
```
4. Run
```sh
$ sbt ";scalafixEnable; scalafix Http4s018To020"
```
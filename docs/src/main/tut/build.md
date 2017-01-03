---
layout: default
title: Get http4s
---

Create a new directory, with the following build.sbt in the root:

```scala
scalaVersion := "2.11.8" // Also supports 2.10.x, or 2.12.1

val http4sVersion = "0.15.0a"

// If you prefer snapshots
// val http4sVersion = "0.15.0a-SNAPSHOT"
// resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion
)
```

The `a` is for scalaz 7.2, without would be 7.1 - see [the matrix] on the main
page. This convention exists because of the binary incompatibility between scalaz
7.1 and 7.2.

[the matrix]: /#getting-started

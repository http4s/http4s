---
menu: main
title: Upgrading from 0.18
weight: 2
---

## Automated upgrading with scalafix

Http4s 0.20 comes with a [scalafix](https://scalacenter.github.io/scalafix/) that does some of the migration automatically.

Before you upgrade manually, we recommend you run this scalafix.

1. Make sure your scala version is >= `2.11.12` or `2.12.7`
2. Add the scalafix plugin to your `project/plugins.sbt`
```sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.0")
```
1. Add this line to your `build.sbt` and upgrade the http4s version to the latest 0.20 release.
```sbt
scalafixDependencies in ThisBuild += "org.http4s" %% "http4s-scalafix" % http4s020Version
```
4. Run
```sh
$ sbt ";scalafixEnable; scalafix Http4s018To020"
```

Once you have applied it, you can remove the imports and try to compile your code.

The compiler errors should help you in showing what's left to upgrade.

For further information about the changes from 0.18, check the [changelog](https://http4s.org/changelog/)
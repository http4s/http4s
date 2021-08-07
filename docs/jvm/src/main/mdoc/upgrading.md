---
menu: main
title: Upgrading
weight: 2
---

## Automated upgrading with scalafix

http4s-0.22 comes with a [scalafix](https://scalacenter.github.io/scalafix/) that does some of the migration automatically.

Before you upgrade manually, we recommend you run this scalafix.

Add the scalafix plugin to your `project/plugins.sbt` or to your global plugins.
```sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.28")
```

Run
```sh
$ sbt ";scalafixEnable; scalafix github:http4s/http4s/v0_22"
```

The compiler errors should help you in showing what's left to upgrade.

For further information about the changes since 0.21, check the [changelog](https://http4s.org/changelog/)

## Help us help you!

If you see recurring patterns that could benefit from a scalafix, please [report them for consideration](https://github.com/http4s/http4s/issues/4858).  For general upgrade tips, please consider a [pull request to this document](https://github.com/http4s/http4s/edit/series/0.22/docs/src/main/mdoc/upgrading.md).

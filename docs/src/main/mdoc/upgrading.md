---
menu: main
title: Upgrading from 0.18
weight: 2
---

## Automated upgrading with scalafix

Http4s 0.20 comes with a [scalafix](https://scalacenter.github.io/scalafix/) that does some of the migration automatically.

Before you upgrade manually, we recommend you run this scalafix.

Make sure your scala version is >= `2.11.12` or `2.12.7`
   
Add the scalafix plugin to your `project/plugins.sbt` or to your global plugins.
```sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.1")
```

Run
```sh
$ sbt ";scalafixEnable; scalafix github:http4s/http4s/v0_20"
```

The compiler errors should help you in showing what's left to upgrade.

For further information about the changes from 0.18, check the [changelog](https://http4s.org/changelog/)
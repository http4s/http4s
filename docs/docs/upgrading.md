# Upgrading


## Automated Upgrading with Scalafix

http4s-0.22 comes with a [scalafix](https://scalacenter.github.io/scalafix/) that does some of the migration automatically.

Before you upgrade manually, we recommend you run this scalafix.

Add the scalafix plugin to your `project/plugins.sbt` or to your global plugins.
```sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.28")
```

Run
```sh
$ sbt ";scalafixEnable; scalafix github:http4s/http4s/v0_22?sha=series/0.22"
```

The compiler errors should help you in showing what's left to upgrade.

For further information about the changes since 0.21, check the [changelog](https://http4s.org/changelog/)


## Http4s 0.21 -> 0.22 Migration Guide

General Changes:

Header names are now `CIStrings` which can be created by importing `org.typelevel.ci._` and using the `ci` string interpolator.

| 0.21                             | 0.22                              |
| -------------------------------  | -------------------------------   |
| `headers.get(\`If-Match\`)`      | `headers.get[\`If-Match\`]`       |
| `Headers.of(`                    | `Headers(`                        |
| `Header("x-ms", "1")`            | `Header(ci"x-ms", "1")`           |
| `baseUri +?? ("p", w)`           | `baseUri +?? ("p" -> w)`          |
| `"x-ms".ci`                      | `ci"x-ms"`                        |
| `import org.http4s.server.blaze` | `import org.http4s.blaze.server`  |



## Help Us Help You!

If you see recurring patterns that could benefit from a scalafix, please [report them for consideration](https://github.com/http4s/http4s/issues/4858).  For general upgrade tips, please consider a [pull request to this document](https://github.com/http4s/http4s/edit/series/0.22/docs/src/main/mdoc/upgrading.md).

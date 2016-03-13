---
layout: default
title: Introduction
---

## Setup

Create a new directory with the following `build.sbt`:

```scala
scalaVersion := "2.11.7" // Also supports 2.10.x

lazy val http4sVersion = "0.13.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % htt4psVersion,
  "org.http4s" %% "http4s-blaze-server" % htt4psVersion,
  "org.http4s" %% "http4s-blaze-client" % htt4psVersion
)
```

This tutorial is compiled as part of the build using [tut].  Each page
is its own REPL session.  If you copy and paste code samples starting
from the top, you should be able to follow along in a REPL:

```
$ sbt console
```

## Your first service

An `HttpService` is a simple alias for
`Kleisli[Task, Request, Response]`.  If that's meaningful to you,
great.  If not, don't panic: [`Kleisli`] is a convenient wrapper
around `Request => Task[Response]`, and [`Task`] is an asynchronous
operation similar to `scala.concurrent.Future`.  You can follow along
now and learn about any unfamiliar types at your leisure.

First, we need some imports:

```tut:silent
import org.http4s._
import org.http4s.dsl._
```

Using the http4s-dsl, we can construct an `HttpService` by pattern
matching the request.  Let's build a service that matches requests to
`GET /hello/:name`, where `:name` is a path parameter of the person to
greet.

```tut:silent
val service = HttpService {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}
```

## Run your service

http4s supports multiple server backends.  In this example, we'll use
[blaze], the native backend supported by http4s.

[tut]: https://github.com/tpolecat/tut
[learn about Kleisli]
[Task: The Missing Manual]

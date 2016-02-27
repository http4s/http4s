---
layout: default
title: http4s
latest_release: 0.12.3
---

http4s is a Scala library for building HTTP client and server
applications in a manner that is:

* functional
* type safe
* asynchronous

The core is built on an immutable case class model of HTTP requests
and responses.  Headers are lazily parsed to semantically meaningful
types, and bodies are represented as [scalaz-streams](scalaz-stream).

http4s server applications can be deployed on the native platform,
[blaze], for maximum speed.  First-class support is also offered for
WAR deployments or an embedded Jetty or Tomcat server.  On the client,
applications can use either blaze or an async-http-client backend.

[scalaz-stream]: https://github.com/functional-streams-for-scala/fs2
[blaze]: https://github.com/http4s/blaze

## Getting started ##

The current release is {{page.latest_release}}, which supports Scala
2.10 and Scala 2.11 on scalaz-7.1.x.

```scala
val Http4sVersion = "{{page.latest_release}}"

libraryDependencies ++= Seq(
  // For server applications
  "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
  // A simple DSL based on extractor patterns
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  // For client applications
  "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
  // One of many integrated JSON libraries
  "org.http4s" %% "http4s-circe" % Http4sVersion
)  
```

Users of scalaz-7.2.x can try out the 0.14.0-SNAPSHOT, which is
published to the Sonatype Snapshots repo.

We invite you to proceed to the [http4s tutorial](docs/).

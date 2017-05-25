---
title: Quick Start
menu: tut
weight: 1
---

Getting started with http4s is easy.  Let's materialize an http4s
skeleton project from its [giter8 template]:

```sbt
// Linux/Mac
$ sbt -sbt-version 0.13.15 new http4s/http4s.g8
// Windows
$ sbt -sbt-version0.13.15 new http4s/http4s.g8
```

Follow the prompts.  For every step along the way, a default value is
provided in brackets.

`name`
: name of your project.

`organization`
: the organization you publish under.  It's common practice on the JVM
to make this a domain you own, in reverse order (i.e., TLD first).
`io.github.username` is also a fine choice.

`package`
: by default, your organization followed by the project name.

`scala_version`
: defaults to the latest available version of Scala

`http4s_version`
: defaults to the latest stable release of http4s.  See
the [versions] page for other suggestions.

At the end of the process, you'll see:

```
Template applied in ./http4s-quickstart
```

In addition to sbt build machinery, two Scala source files are
generated:

```sh
$ cd http4s-quickstart
$ find . -name '*.scala'
./src/main/scala/com/example/http4squickstart/HelloWorld.scala
./src/main/scala/com/example/http4squickstart/Server.scala
```

`HelloWorld.scala` defines a service that responds to HTTP requests on
`GET /hello/$USERNAME` with a JSON greeting.  `Server.scala` defines
an object that extends `App` to start a server.  sbt will find and run
any app that it finds in your project.  Let's try it:

```sh
$ sbt run
```

Depending on the state of your Ivy cache, several dependencies will
download.  This is a good time to grab a beverage.  When you come
back, you should see a line similar to this:

```
264 [run-main-0] INFO org.http4s.blaze.channel.nio1.NIO1SocketServerGroup - Service bound to address /127.0.0.1:8080
```

This indicates that /blaze/, htttp4s' native server backend, is
running our service on port 8080.  Let's try out the hello world
service with curl:

```sh
$ curl -i http://localhost:8080/hello/world
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Date: Thu, 01 Dec 2016 05:05:24 GMT
Content-Length: 26

{"message":"Hello, world"}
```

To shut down your server, simply press `^C` in your console.

With just a few commands, we have a fully functional app for creating
a simple JSON service.

[giter8 template]: https://github.com/http4s/http4s.g8
[versions]: /versions/

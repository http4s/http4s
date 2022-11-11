# Quick Start


Getting started with http4s is easy.  Let's materialize an http4s
skeleton project from its [giter8 template]:

```sbt
# for Scala 2.x
$ sbt new http4s/http4s.g8 --branch 1.0

# for Scala 3
$ sbt new http4s/http4s.g8 --branch 1.0-scala3
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

`sbt_version`
: the version of SBT for your generated project.

`scala_version`
: the version of Scala for your generated project.

`http4s_version`
: defaults to the latest stable release of http4s.  See
  the [versions] page for other suggestions.

`logback_version`
: the version of Logback for logging in your generated project.

At the end of the process, you'll see:

```
Template applied in ./quickstart
```

In addition to sbt build machinery, some Scala source files are
generated:

```sh
$ cd quickstart
$ find src/main -name '*.scala'
./src/main/scala/com/example/quickstart/HelloWorld.scala
./src/main/scala/com/example/quickstart/QuickstartRoutes.scala
./src/main/scala/com/example/quickstart/Jokes.scala
./src/main/scala/com/example/quickstart/Main.scala
./src/main/scala/com/example/quickstart/QuickstartServer.scala
```
`Main.scala` defines a runnable object `Main extends IOApp.Simple` with an entry point member `run`
which calls the `run` method of the object `QuickstartServer` defined on `QuickstartServer.scala`.
Starting ember, http4s' native server backend.

`QuickStartRoutes` has two `route` definitions. The `helloWorldRoutes` containing a simple `HttpRoutes`
that responds to `GET/hello/$USERNAME` with a JSON greeting.  Let's try it:

```sh
$ sbt run
```

Depending on the state of your Ivy cache, several dependencies will
download.  This is a good time to grab a beverage.  When you come
back, you should see a line similar to this:

```
[io-compute-1] INFO  o.h.e.s.EmberServerBuilderCompanionPlatform - Ember-Server service bound to address: [::]:8080
```

This indicates that ember is running our service on port 8080. Let's try out the
hello world service with curl:

```sh
$ curl -i http://localhost:8080/hello/world
HTTP/1.1 200 OK
Content-Type: application/json
Date: Sun, 28 Jun 2020 16:23:31 GMT
Content-Length: 26

{"message":"Hello, world"}
```

To shut down your server, simply press `^C` in your console. Note that
when running interactive SBT, `^C` will kill the SBT process. For rapid
application development, you may wish to add the [sbt-revolver] plugin
to your project and starting the server from the SBT prompt with `reStart`.

With just a few commands, we have a fully functional app for creating
a simple JSON service.

[giter8 template]: https://github.com/http4s/http4s.g8
[versions]: /versions/
[sbt-revolver]: https://github.com/spray/sbt-revolver

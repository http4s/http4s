---
menu: main
weight: 310
title: JSON handling
---

## Add the JSON support module(s)

http4s-core does not include JSON support, but integration with some
popular Scala JSON libraries are supported as modules.

### Circe

The http4s team recommends circe.  Only http4s-circe is required for
basic interop with circe, but to follow this tutorial, install all three:

```scala
val http4sVersion = "{{< version "http4s.doc" >}}"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-circe" % http4sVersion,
  // Optional for auto-derivation of JSON codecs
  "io.circe" %% "circe-generic" % "{{< version circe >}}",
  // Optional for string interpolation to JSON model
  "io.circe" %% "circe-literal" % "{{< version circe >}}"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
```

## Sending raw JSON

Let's create a function to produce a simple JSON greeting with circe. First, the imports:

```scala mdoc:silent
import cats.effect._
import io.circe._
import io.circe.literal._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
```

Then the actual code:

```scala mdoc
def hello(name: String): Json =
  json"""{"hello": $name}"""

val greeting = hello("world")
```

We now have a JSON value, but we don't have enough to render it:

```scala mdoc:fail
Ok(greeting).unsafeRunSync()
```

To encode a Scala value of type `A` into an entity, we need an
`EntityEncoder[A]` in scope.  The http4s-circe module includes a
`org.http4s.circe` object, which gives us exactly this for an
`io.circe.Json` value:

```scala mdoc:silent
import org.http4s.circe._
```

```scala mdoc
Ok(greeting).unsafeRunSync()
```

The same `EntityEncoder[Json]` we use on server responses is also
useful on client requests:

```scala mdoc:silent
import org.http4s.client.dsl.io._
```

```scala mdoc
POST(json"""{"name": "Alice"}""", uri"/hello")
```

## Encoding case classes as JSON

These JSON literals are nice, but in real apps, we prefer to operate
on case classes and use JSON as a serialization format near the edge
of the world.

Let's define a couple case classes:

```scala mdoc:silent
case class Hello(name: String)
case class User(name: String)
```

To transform a value of type `A` into `Json`, circe uses an
`io.circe.Encoder[A]`.  With circe's syntax, we can convert any value
to JSON as long as an implicit `Encoder` is in scope:

```scala mdoc:silent
import io.circe.syntax._
```

We haven't told Circe how we want to encode our case class.
Let's provide an encoder:

```scala mdoc
implicit val HelloEncoder: Encoder[Hello] =
  Encoder.instance { (hello: Hello) =>
    json"""{"hello": ${hello.name}}"""
  }

Hello("Alice").asJson
```

That was easy, but gets tedious for applications dealing in lots of
types.  Fortunately, circe can automatically derive an encoder for us,
using the field names of the case class as key names in a JSON object:

```scala mdoc:silent
import io.circe.generic.auto._
```

```scala mdoc
User("Alice").asJson
```

Equipped with an `Encoder` and `.asJson`, we can send JSON in requests
and responses for our case classes:

```scala mdoc
Ok(Hello("Alice").asJson).unsafeRunSync()
POST(User("Bob").asJson, uri"/hello")
```

If within some route we serve json only, we can use:

```scala mdoc:silent
{
import org.http4s.circe.CirceEntityEncoder._
}
```

Thus there's no more need in calling `asJson` on result.
However, it may introduce ambiguity errors when we also build
some json by hand within the same scope. 

## Receiving raw JSON

Just as we needed an `EntityEncoder[JSON]` to send JSON from a server
or client, we need an `EntityDecoder[JSON]` to receive it.

The `org.http4s.circe._` package provides an implicit
`EntityDecoder[Json]`.  This makes it very easy to decode a request or
response body to JSON using the [`as` syntax]:

```scala mdoc
Ok("""{"name":"Alice"}""").flatMap(_.as[Json]).unsafeRunSync()
POST("""{"name":"Bob"}""", uri"/hello").as[Json].unsafeRunSync()
```

Like sending raw JSON, this is useful to a point, but we typically
want to get to a typed model as quickly as we can.

## Decoding JSON to a case class

To get from an HTTP entity to `Json`, we use an `EntityDecoder[Json]`.
To get from `Json` to any type `A`, we need an `io.circe.Decoder[A]`.
http4s-circe provides the `jsonOf` function to make the connection all
the way from HTTP to your type `A`.  Specifically, `jsonOf[A]` takes
an implicit `Decoder[A]` and makes a `EntityDecoder[A]`:

```scala mdoc
implicit val userDecoder = jsonOf[IO, User]
Ok("""{"name":"Alice"}""").flatMap(_.as[User]).unsafeRunSync()

POST("""{"name":"Bob"}""", uri"/hello").as[User].unsafeRunSync()
```

If we are always decoding from JSON to a typed model, we can use
the following import:

```scala mdoc:silent
import org.http4s.circe.CirceEntityDecoder._
```

This creates an `EntityDecoder[A]` for every `A` that has a `Decoder` instance.

However, be cautious when using this. Having this implicit
in scope does mean that we would always try to decode HTTP entities
from JSON (even if it is XML or plain text, for instance).

For more convenience there is import combining both encoding 
and decoding derivation: 

```scala mdoc:silent
import org.http4s.circe.CirceEntityCodec._
```

## Putting it all together

### A Hello world service

Our hello world service will parse a `User` from a request and offer a
proper greeting.

```scala mdoc:silent:reset
import cats.effect._

import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._

import scala.concurrent.ExecutionContext.global

case class User(name: String)
case class Hello(greeting: String)

// Needed by `BlazeServerBuilder`. Provided by `IOApp`.
implicit val cs: ContextShift[IO] = IO.contextShift(global)
implicit val timer: Timer[IO] = IO.timer(global)

implicit val decoder = jsonOf[IO, User]

val jsonApp = HttpRoutes.of[IO] {
  case req @ POST -> Root / "hello" =>
    for {
	  // Decode a User request
	  user <- req.as[User]
	  // Encode a hello response
	  resp <- Ok(Hello(user.name).asJson)
    } yield (resp)
}.orNotFound

val server = EmberServerBuilder.default[IO].withHost("0.0.0.0").withPort(8081).withHttpApp(jsonApp).build
val fiber = server.use(_ => IO.never).start.unsafeRunSync()
```

## A Hello world client

Now let's make a client for the service above:

```scala mdoc:silent
import org.http4s.client.dsl.io._
import org.http4s.ember.client._
import cats.effect.IO
import io.circe.generic.auto._
import fs2.Stream

// Decode the Hello response
def helloClient(name: String): Stream[IO, Hello] = {
  // Encode a User request
  val req = POST(User(name).asJson, uri"http://localhost:8081/hello")
  // Create a client
  Stream.resource(EmberClientBuilder.default[IO].build).flatMap { httpClient =>
    // Decode a Hello response
    Stream.eval(httpClient.expect(req)(jsonOf[IO, Hello]))
  }
}
```

Finally, we post `User("Alice")` to our Hello service and expect
`Hello("Alice")` back:

```scala mdoc
val helloAlice = helloClient("Alice")
helloAlice.compile.last.unsafeRunSync()
```

Finally, shut down our example server.

```scala mdoc:silent
fiber.cancel.unsafeRunSync()
```

[circe-generic]: https://github.com/travisbrown/circe#codec-derivation
[`as` syntax]: ../api/org/http4s/MessageOps.html#as[T](implicitF:cats.FlatMap[F],implicitdecoder:org.http4s.EntityDecoder[F,T]):F[T]

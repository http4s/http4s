---
menu: main
weight: 310
title: JSON handling
---

## Add the JSON support module(s)

http4s-core does not include JSON support, but integration with three
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

### Argonaut

Circe is a fork of argonaut, another popular JSON library in the Scala
community.  The functionality is similar:

```scala
libraryDependencies += Seq(
  "org.http4s" %% "http4s-argonaut" % http4sVersion,
  // Optional for auto-derivation of JSON codecs
  "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "{{< version "argonaut-shapeless_6.2" >}}"
)
```

For those not ready to upgrade to argonaut-6.2, an `http4s-argonaut61`
is also available.  It is source compatible, but compiled against
Argonaut 6.1.

### Json4s

Json4s is less functionally pure than Circe or Argonaut, but older and
integrated with many Scala libraries.  It comes with two backends.
You should pick one of these dependencies:

```scala
libraryDependencies += "org.http4s" %% "http4s-json4s-native" % http4sVersion
libraryDependencies += "org.http4s" %% "http4s-json4s-jackson" % http4sVersion
```

There is no extra codec derivation library for json4s, as it generally
bases its codecs on runtime reflection.

## Sending raw JSON

Let's create a function to produce a simple JSON greeting with circe. First, the imports:

```tut:silent
import cats.effect._
import io.circe._
import io.circe.literal._
import org.http4s._
import org.http4s.dsl.io._
```

Then the actual code:

```tut:book
def hello(name: String): Json =
  json"""{"hello": $name}"""

val greeting = hello("world")
```

We now have a JSON value, but we don't have enough to render it:

```tut:fail
Ok(greeting).unsafeRunSync
```

To encode a Scala value of type `A` into an entity, we need an
`EntityEncoder[A]` in scope.  The http4s-circe module includes a
`org.http4s.circe` object, which gives us exactly this for an
`io.circe.Json` value:

```tut:silent
import org.http4s.circe._
```

```tut:book
Ok(greeting).unsafeRunSync
```

The same `EntityEncoder[Json]` we use on server responses is also
useful on client requests:

```tut:silent
import org.http4s.client._
import org.http4s.client.dsl.io._
```

```tut:book
POST(json"""{"name": "Alice"}""", Uri.uri("/hello")).unsafeRunSync
```

## Encoding case classes as JSON

These JSON literals are nice, but in real apps, we prefer to operate
on case classes and use JSON as a serialization format near the edge
of the world.

Let's define a couple case classes:

```tut:silent
case class Hello(name: String)
case class User(name: String)
```

To transform a value of type `A` into `Json`, circe uses an
`io.circe.Encoder[A]`.  With circe's syntax, we can convert any value
to JSON as long as an implicit `Encoder` is in scope:

```tut:silent
import io.circe.syntax._
```

```tut:fail
Hello("Alice").asJson
```

Oops!  We haven't told Circe how we want to encode our case class.
Let's provide an encoder:

```tut:book
implicit val HelloEncoder: Encoder[Hello] =
  Encoder.instance { hello: Hello =>
    json"""{"hello": ${hello.name}}"""
  }

Hello("Alice").asJson
```

That was easy, but gets tedious for applications dealing in lots of
types.  Fortunately, circe can automatically derive an encoder for us,
using the field names of the case class as key names in a JSON object:

```tut:silent
import io.circe.generic.auto._
```

```tut:book
User("Alice").asJson
```

Equipped with an `Encoder` and `.asJson`, we can send JSON in requests
and responses for our case classes:

```tut:book
Ok(Hello("Alice").asJson).unsafeRunSync
POST(User("Bob").asJson, Uri.uri("/hello")).unsafeRunSync
```

If within some route we serve json only, we can use:

```tut:silent
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

```tut:book
Ok("""{"name":"Alice"}""").flatMap(_.as[Json]).unsafeRunSync
POST("""{"name":"Bob"}""", Uri.uri("/hello")).flatMap(_.as[Json]).unsafeRunSync
```

Like sending raw JSON, this is useful to a point, but we typically
want to get to a typed model as quickly as we can.

## Decoding JSON to a case class

To get from an HTTP entity to `Json`, we use an `EntityDecoder[Json]`.
To get from `Json` to any type `A`, we need an `io.circe.Decoder[A]`.
http4s-circe provides the `jsonOf` function to make the connection all
the way from HTTP to your type `A`.  Specifically, `jsonOf[A]` takes
an implicit `Decoder[A]` and makes a `EntityDecoder[A]`:

```tut:book
implicit val userDecoder = jsonOf[IO, User]
Ok("""{"name":"Alice"}""").flatMap(_.as[User]).unsafeRunSync

POST("""{"name":"Bob"}""", Uri.uri("/hello")).flatMap(_.as[User]).unsafeRunSync
```

If we are always decoding from JSON to a typed model, we can use
the following import:

```tut:silent
import org.http4s.circe.CirceEntityDecoder._
```

This creates an `EntityDecoder[A]` for every `A` that has a `Decoder` instance.

However, be cautious when using this. Having this implicit
in scope does mean that we would always try to decode HTTP entities
from JSON (even if it is XML or plain text, for instance).

For more convenience there is import combining both encoding 
and decoding derivation: 

```tut:silent
import org.http4s.circe.CirceEntityCodec._
```

## Putting it all together

### A Hello world service

Our hello world service will parse a `User` from a request and offer a
proper greeting.

```tut:silent:reset
import cats.effect._

import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

import scala.concurrent.ExecutionContext.Implicits.global

case class User(name: String)
case class Hello(greeting: String)

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

// Needed by `BlazeServerBuilder`. Provided by `IOApp`.
implicit val cs: ContextShift[IO] = IO.contextShift(global)
implicit val timer: Timer[IO] = IO.timer(global)

import org.http4s.server.blaze._
val server = BlazeServerBuilder[IO].bindHttp(8080).withHttpApp(jsonApp).resource
val fiber = server.use(_ => IO.never).start.unsafeRunSync()
```

## A Hello world client

Now let's make a client for the service above:

```tut:silent
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.client.blaze._
import cats.effect.IO
import io.circe.generic.auto._
import fs2.Stream

// Decode the Hello response
def helloClient(name: String): Stream[IO, Hello] = {
  // Encode a User request
  val req = POST(User(name).asJson, Uri.uri("http://localhost:8080/hello"))
  // Create a client
  BlazeClientBuilder[IO](global).stream.flatMap { httpClient =>
    // Decode a Hello response
    Stream.eval(httpClient.expect(req)(jsonOf[IO, Hello]))
  }
}
```

Finally, we post `User("Alice")` to our Hello service and expect
`Hello("Alice")` back:

```tut:book
val helloAlice = helloClient("Alice")
helloAlice.compile.last.unsafeRunSync
```

Finally, shut down our example server.

```tut:silent
fiber.cancel.unsafeRunSync()
```

[argonaut-shapeless]: https://github.com/alexarchambault/argonaut-shapeless
[circe-generic]: https://github.com/travisbrown/circe#codec-derivation
[jsonExtract]: https://github.com/http4s/http4s/blob/master/json4s/src/main/scala/org/http4s/json4s/Json4sInstances.scala#L29
[`as` syntax]: ../api/org/http4s/MessageOps.html#as[T](implicitF:cats.FlatMap[F],implicitdecoder:org.http4s.EntityDecoder[F,T]):F[T]

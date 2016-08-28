---
layout: default
title: JSON handling
---

## Pick a library

### Argonaut

Argonaut-shapeless for automatic codec derivation.

```scala
libraryDependencies += Seq(
  "org.http4s" %% "http4s-argonaut" % "0.15.0a-SNAPSHOT",
  "com.github.alexarchambault" %% "argonaut-shapeless_6.1" % "1.1.1" // or 1.1.0 for non-a versions
)
```

### Circe

Circe-generic for automatic codec derivation.

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-circe" % "0.15.0a-SNAPSHOT",
  "io.circe" %% "circe-generic" % "0.4.1"
)
```

### Json4s

```scala
libraryDependencies += "org.http4s" %% "http4s-json4s" % "0.15.0a-SNAPSHOT"
```

And one of

```scala
libraryDependencies += "org.http4s" %% "http4s-json4s-native" % "0.15.0a-SNAPSHOT"
libraryDependencies += "org.http4s" %% "http4s-json4s-jackson" % "0.15.0a-SNAPSHOT"
```

## Import it

The import statement is one of the following:

```scala
import org.http4s.argonaut._
import org.http4s.circe._
import org.http4s.json4s._
```

## Deriving codecs

To use the functions provided by http4s, the corresponding implicit instances
need to be in scope. For argonaut, there is [argonaut-shapeless], circe has the
[circe-generic] module. With json4s, there's [jsonExtract], with the
corresponding drawbacks.

## Sending JSON

The usage is the same for client and server, both points use an
`EntityEncoder[T]` to transform the outgoing data into a scala class. So
whenever you see a `EntityEncoder[T]` in the http4s scaladocs, you can plugin in
a `jsonEncoderOf[T]` and it will convert it into json for you.

```tut:book
import argonaut._, Argonaut._, ArgonautShapeless._
import org.http4s.argonaut._

import org.http4s._, org.http4s.dsl._

case class User(name: String)
case class Hello(greeting: String)

val jsonService = HttpService {
  case r @ POST -> Root / "hello" =>
    r.decode[User](user =>
      Ok(Hello(s"Hello, ${user.name}"))(jsonEncoderOf)
    )(jsonOf)
}

import org.http4s.server.syntax._
import org.http4s.server.blaze._
val builder = BlazeBuilder.bindHttp(8080, "localhost").mountService(jsonService, "/")
val server = builder.run
```

## Receiving JSON

The `EntityDecoder[T]` instances work similarly to the `EntityEncoder[T]`
instances, except it's `jsonOf` to create the instances.

Let's talk to the server defined above.

```tut:book
import org.http4s.client.blaze._
import org.http4s.Uri

val httpClient = PooledHttp1Client()
val req = Request(uri = Uri.uri("http://localhost:8080/hello"), method = Method.POST).withBody(User("Anabelle"))(jsonEncoderOf)
httpClient.expect(req)(jsonOf[Hello]).run
```

And clean everything up.

```tut:book
httpClient.shutdownNow()
server.shutdownNow()
```

## Talking to the Github API

As an example, we'll use the [github-orgs] endpoint. The data is shortened a bit
for clarity.

```json
[
  {
    "id": 3692188,
    "name": "http4s",
    "full_name": "http4s/http4s",
    "owner": {
      "login": "http4s",
      "id": 1527492
      // ...
    },
    "private": false,
    "html_url": "https://github.com/http4s/http4s",
    "description": "A minimal, idiomatic Scala interface for HTTP",
    "size": 21365,
    "stargazers_count": 533,
    "language": "Scala",
    "forks_count": 118,
    "open_issues_count": 69,
    "forks": 118,
    "open_issues": 69,
    "watchers": 533
    // ...
  }
  // ...
]
```

To capture this structure, we'll need corresponding case classes.

```tut:book
case class User(login: String, id: Long)
case class Repo(id: Long, name: String, full_name: String, owner: User, `private`: Boolean, html_url: String, description: String, size: Int, stargazers_count: Int, language: String, forks_count: Int, open_issues_count: Int, forks: Int, open_issues: Int, watchers: Int)
```

This parts skips over the [client] explanation. We'll use argonaut, with
[argonaut-shapeless] for codec derivation. The json decoder is provided by
`ArgonautShapeless`, and passed to the `client.expect` method via the second
argument, which is usually an implicit `EntityDecoder`, but in this case, we
want to be more explicit.

<!-- For more information about the uri templating, visit [uri]. -->

```tut:book
import scalaz.concurrent.Task

import org.http4s.util.CaseInsensitiveString.ToCaseInsensitiveStringSyntax
import org.http4s.UriTemplate
import org.http4s.UriTemplate._

val httpClient = PooledHttp1Client()

def repos(org: String): Task[List[Repo]] = {
  val uri = UriTemplate(
    scheme = Some("https".ci),
    authority = Some(Uri.Authority(host = Uri.RegName("api.github.com"))),
    path = List(PathElm("orgs"), PathElm(org), PathElm("repos"))
  ).toUriIfPossible.get
  httpClient.expect(uri)(jsonOf[List[Repo]])
}

val http4s = repos("http4s")
http4s.map(_.map(_.stargazers_count)).run.mkString("\n")
httpClient.shutdownNow()
```


[argonaut-shapeless]: https://github.com/alexarchambault/argonaut-shapeless
[circe-generic]: https://github.com/travisbrown/circe#codec-derivation
[jsonExtract]: https://github.com/http4s/http4s/blob/master/json4s/src/main/scala/org/http4s/json4s/Json4sInstances.scala#L29
[client]: client.html
[github-orgs]: https://developer.github.com/v3/repos/#list-organization-repositories
[uri]: url.html

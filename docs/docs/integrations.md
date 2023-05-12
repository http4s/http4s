{%
laika.title: Integrations
%}

# Integrations

Http4s provides a standard interface for defining services and clients. This enables an ecosystem of interchangeable server and client backends.

There are also integrations for entities, metrics, and more. Check out the "Related Projects" section of the navigation menu for the complete list of integrations.

## Ember

Http4s Ember is a server and client backend developed in the core repository.

- Implements HTTP/1 and HTTP/2
- Runs on JDK 8+, Node.js 16+, and Scala Native
- Pure FP, built with Cats Effect and FS2

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % "@VERSION@",
  "org.http4s" %% "http4s-ember-client" % "@VERSION@",
)
```

## Backend Integrations

| Backend                                                          | Platform                      | Http Client | Http Server | Websocket Client | Websocket Server | Proxy support (Client) |
|------------------------------------------------------------------|-------------------------------|:-----------:|:-----------:|:----------------:|:----------------:|:----------------------:|
| [Ember](#ember)                                                  | JDK 8+ / Node.js 16+ / Native | ✅           | ✅           | ❌                | ✅                | ❌                      |
| [Blaze](https://github.com/http4s/blaze)                         | JDK 8+                        | ✅           | ✅           | ❌                | ✅                | ❌                      |
| [Netty](https://github.com/http4s/http4s-netty)                  | JDK 8+                        | ✅           | ✅           | ✅                | ✅                | ✅                      |
| [JDK Http Client](https://jdk-http-client.http4s.org)            | JDK 11+                       | ✅           | ❌           | ✅                | ❌                | ✅                      |
| [Servlet](https://github.com/http4s/http4s-servlet)              | JDK 8+                        | ❌           | ✅           | ❌                | ❌                | ❌                      |
| [DOM](https://http4s.github.io/http4s-dom)                       | Browsers                      | ✅           | ❌           | ✅                | ❌                | ❌                      |
| [Feral](https://github.com/typelevel/feral)                      | Serverless                    | ❌           | ✅           | ❌                | ❌                | ❌                      |

## Entity Integrations

Http4s has multiple smaller modules for Entity encoding and decoding support of common types.

- [Circe](json.md)
- [Scalatags](https://github.com/http4s/http4s-scalatags)
- [Scala XML](https://github.com/http4s/http4s-scala-xml)
- [fs2-data](https://github.com/http4s/http4s-fs2-data)

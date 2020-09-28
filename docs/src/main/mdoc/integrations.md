---
title: Integrations
menu: main
weight: 2
---

## Http Backend integrations

Http4s provides a frontend interface compatible with cats-effect, but it supports many different backend implementations

| Backend                                                                   | Jdk Support | Http4s Version(s) | 100% Nonblocking               | Http Client                | Http Server           | Websocket Client   | Websocket Server |
|---------------------------------------------------------------------------|-------------|-------------------|--------------------------------|----------------------------|-----------------------|--------------------|------------------|
| [Blaze](https://github.com/http4s/blaze)                                  | 8+          | 0.15+             | :x: Blocking                   | `http4s-blaze-client`      | `http4s-blaze-server` | :x:                | :x:              |
| [Async Http Client](https://github.com/AsyncHttpClient/async-http-client) | 8+          | 0.18+             | :white_check_mark: Nonblocking | `http4s-async-http-client` | :x:                   | :x:                | :x:              |
| [Ember](https://github.com/http4s/http4s)                                 | 8+          | 0.21+             | :white_check_mark: Nonblocking | `http4s-ember-client`      | `http4s-ember-server` | ?                  | ?                |
| [Jdk11 Http Client](https://jdk-http-client.http4s.org/stable/)           | 11+         | 0.21+             | :white_check_mark: Nonblocking | `http4s-jdk-http-client`   | :x:                   | :white_check_mark: | :x:              |
| [Jetty](https://www.eclipse.org/jetty/)                                   | 8+          | All (0.2+)        | ?                              | :x:                        | `http4s-jetty`        |                    |                  |
| [OkHttp](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/)| 8+       | 0.18+             | :x: Blocking                   | `http4s-okhttp-client`     | :x:                   | :x:                | :x:              |

## Entity integrations

Http4s has multiple smaller modules for Entity encoding and Decoding support of common types.

- Circe: `http4s-circe`. See the [json] docs for more.
- Scalatags: `http4s-scalatags`
- Scala-Xml: `http4s-scala-xml`

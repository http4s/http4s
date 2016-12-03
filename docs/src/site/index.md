---
layout: default
title: http4s
---

http4s is a typeful, purely functional HTTP library for client and
server applications written in Scala.

## Principles

* *Typeful*: http4s uses Scala's type system to increase
self-documentation and compile-time verification.  Standard headers
are lazily parsed to semantically meaningful types, and typeclasses
are provided to encode and decode bodies to several common formats.

* *Purely functional*: The pure functional side of Scala is favored to
promote composability and easy reasoning about your code.  The core is
built on an immutable case class model of HTTP requests and responses,
shared by the client and the server.

* *Asynchronous*: Much of the API is built around a
`scalaz.concurrent.Task`.  Bodies are modeled as
[scalaz-streams](scalaz-stream) for performant chunking of large
messages in constant memory.

* *Modular*: http4s has a lightweight core with multiple deployment
options.  Server applications can be deployed to [blaze], the native
platform, or as a servlet application.  Client applications run on
either blaze or an async-http-client backend.  Several libraries
useful in everyday HTTP programming, such as [circe] and [argonaut],
are integrated via optional modules.

* *Community-oriented*: http4s is a community-driven project, and aims
to provide a welcoming environment for all users.  We are proud to be
a [Typelevel](http://typelevel.org) incubator project.  Please see our
[community] page to learn how you can participate.

## Getting started ##

Please pick a version that suits your needs, and proceed to its tutorial:

{:.table}
| http4s Version   | Status      | Tutorial    | scala          | scalaz | scalaz-stream | java |
| ---              | ---         | ----        | ---            | ---    | ---           | --   | 
| 0.15.0a-SNAPSHOT | Development | [docs/0.15] | 2.11.x, 2.10.x | 7.2.x  | 0.8.4a        | 1.8+ |
| 0.15.0-SNAPSHOT  | Development | [docs/0.15] | 2.11.x, 2.10.x | 7.1.x  | 0.8.4         | 1.8+ |
| 0.14.11a         | Stable      | [docs/0.14] | 2.11.x, 2.10.x | 7.2.x  | 0.8.4a        | 1.8+ |
| 0.14.11          | Stable      | [docs/0.14] | 2.11.x, 2.10.x | 7.1.x  | 0.8.4         | 1.8+ |
| 0.13.3a          | Stable      | [docs/0.13] | 2.11.x, 2.10.x | 7.2.x  | 0.8.4a        | 1.8+ |
| 0.13.3           | Stable      | [docs/0.13] | 2.11.x, 2.10.x | 7.1.x  | 0.8.4         | 1.8+ |
| 0.12.4           | EOL         |             | 2.11.x, 2.10.x | 7.1.x  | 0.8           | 1.8+ |
| 0.11.3           | EOL         |             | 2.11.x, 2.10.x | 7.1.x  | 0.8           | 1.8+ |
| 0.10.1           | EOL         |             | 2.11.x, 2.10.x | 7.1.x  | 0.7a          | 1.8+ |
| 0.9.3            | EOL         |             | 2.11.x, 2.10.x | 7.1.x  | 0.7a          | 1.8+ |
| 0.8.6            | EOL         |             | 2.11.x, 2.10.x | 7.1.x  | 0.7a          | 1.7+ |

* _Stable_ releases are recommended for production use, and receive
backward, binary-compatible bugfixes from the http4s team.

* _Development_ releases are published as snapshots to Sonatype by CI.

* _EOL_ releases are not actively maintained by the http4s team, but
patches will be considered.

[scalaz-stream]: https://github.com/functional-streams-for-scala/fs2
[blaze]: https://github.com/http4s/blaze
[circe]: https://github.com/travisbrown/circe
[argonaut]: https://github.com/argonaut-io/argonaut
[community]: community
[code of conduct]: community/conduct.html
[docs/0.13]: docs/0.13
[docs/0.14]: docs/0.14
[docs/0.15]: docs/0.15

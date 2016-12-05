---
title: Home
type: index
weight: 0
---

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

## Code of Conduct

http4s is proud to be a [Typelevel](http://typelevel.org/) incubator
project.  We are dedicated to providing a harassment-free community
for everyone, and ask that the community adhere to the
[code of conduct](http://typelevel.org/conduct.html).

## License

Copyright 2013-2016 [http4s.org]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at [http://www.apache.org/licenses/LICENSE-2.0]

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[http4s.org]: http://http4s.org/
[http://www.apache.org/licenses/LICENSE-2.0]: http://www.apache.org/licenses/LICENSE-2.0
[scalaz-stream]: https://github.com/functional-streams-for-scala/fs2
[blaze]: https://github.com/http4s/blaze
[circe]: https://github.com/travisbrown/circe
[argonaut]: https://github.com/argonaut-io/argonaut
[quick start]: http://http4s.org/docs
[community]: http://http4s.org/community

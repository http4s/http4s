---
menu: tut
weight: 115
title: Task[T]
---

As you might have noticed, a few methods, e.g. `withBody` lift a `Response` into a `Task` context, so you end up with a `Task[Response]`. Coincidentially, all the methods which relate to the body of the `Request`/`Response`. Why does this happen? http4s is built on [Streaming], so directly materializing the body of a `Response` defeats any streaming benefits. Instead, the result is wrapped in a `Task` which preserves the asynchronous nature.

## Methods on `Task[Response]`

As with every other context, you have to map/flatMap it to do useful things with it. To avoid some of this, there's [Message], which puts quite a few methods on `Task[Request]`/`Task[Response]`. There's some more methods on [TaskRequestOps] and [TaskResponseOps].

## Why not `Response(body: Task[Body])`?

The `Content-Length` header is set dynamically depending on the body size, so it needs access to the body. So the whole `Response` gets sucked into `Task`.

[Streaming]: ../streaming
[TaskRequestOps]: ../api/index.html#org.http4s.TaskRequestOps
[TaskResponseOps]: ../api/index.html#org.http4s.TaskResponseOps
[Message]: ../api/index.html#org.http4s.Message

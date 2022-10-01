# Testing

This document implements a simple `org.http4s.HttpRoutes` and then
walks through the results (i.e. `org.http4s.Response`) of applying inputs (i.e. `org.http4s.Request`) to it.

After reading this doc, the reader should feel comfortable writing a unit test using his/her favorite Scala testing library.

Now, let's define an `org.http4s.HttpRoutes`.

```scala mdoc:silent
import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats.effect._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
```

If you're in a REPL, we also need a runtime:

```scala mdoc:silent
import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
```

```scala mdoc:silent
case class User(name: String, age: Int)
implicit val UserEncoder: Encoder[User] = deriveEncoder[User]

trait UserRepo[F[_]] {
  def find(userId: String): F[Option[User]]
}

def httpRoutes[F[_]](repo: UserRepo[F])(
      implicit F: Async[F]
): HttpRoutes[F] = HttpRoutes.of[F] {
  case GET -> Root / "user" / id =>
    repo.find(id).map {
      case Some(user) => Response(status = Status.Ok).withEntity(user.asJson)
      case None       => Response(status = Status.NotFound)
    }
}
```

For testing, let's define a `check` function:

```scala mdoc:silent
// Return true if match succeeds; otherwise false
def check[A](actual:        IO[Response[IO]],
            expectedStatus: Status,
            expectedBody:   Option[A])(
    implicit ev: EntityDecoder[IO, A]
): Boolean =  {
   val actualResp         = actual.unsafeRunSync()
   val statusCheck        = actualResp.status == expectedStatus
   val bodyCheck          = expectedBody.fold[Boolean](
       // Verify Response's body is empty.
       actualResp.body.compile.toVector.unsafeRunSync().isEmpty)(
       expected => actualResp.as[A].unsafeRunSync() == expected
   )
   statusCheck && bodyCheck
}

```

Let's define service by passing a `UserRepo` that returns `Ok(user)`.

```scala mdoc:silent
val success: UserRepo[IO] = new UserRepo[IO] {
  def find(id: String): IO[Option[User]] = IO.pure(Some(User("johndoe", 42)))
}

val response: IO[Response[IO]] = httpRoutes[IO](success).orNotFound.run(
  Request(method = Method.GET, uri = uri"/user/not-used" )
)

```

```scala mdoc
val expectedJson = Json.obj(
      "name" := "johndoe",
      "age" := 42
)

check[Json](response, Status.Ok, Some(expectedJson))
```

Next, let's define a service with a `userRepo` that returns `None` to any input.

```scala mdoc:silent
val foundNone: UserRepo[IO] = new UserRepo[IO] {
  def find(id: String): IO[Option[User]] = IO.pure(None)
}

val respFoundNone: IO[Response[IO]] = httpRoutes[IO](foundNone).orNotFound.run(
  Request(method = Method.GET, uri = uri"/user/not-used" )
)
```

```scala mdoc
check[Json](respFoundNone, Status.NotFound, None)
```

Finally, let's pass a `Request` which our service does not handle.

```scala mdoc:silent
val doesNotMatter: UserRepo[IO] = new UserRepo[IO] {
  def find(id: String): IO[Option[User]] =
    IO.raiseError(new RuntimeException("Should not get called!"))
}

val respNotFound: IO[Response[IO]] = httpRoutes[IO](doesNotMatter).orNotFound.run(
  Request(method = Method.GET, uri = uri"/not-a-matching-path" )
)
```

```scala mdoc
check[String](respNotFound, Status.NotFound, Some("Not found"))
```

## Using client

Having HttpApp you can build a client for testing purposes. Following the example above we could define our HttpApp like this:

```scala mdoc:silent
val httpApp: HttpApp[IO] = httpRoutes[IO](success).orNotFound
```

From this, we can obtain the `Client` instance using `Client.fromHttpApp` and then use it to test our sever/app.

```scala mdoc:silent
import org.http4s.client.Client

val request: Request[IO] = Request(method = Method.GET, uri = uri"/user/not-used")
val client: Client[IO] = Client.fromHttpApp(httpApp)

val resp: IO[Json]     = client.expect[Json](request)
assert(resp.unsafeRunSync() == expectedJson)
```

## Conclusion

The above documentation demonstrated how to define an `HttpRoutes[F]`, pass `Request`'s, and then
test the expected `Response`.

To add unit tests in your chosen Scala Testing Framework, please follow the above examples.

## References

* [Ross Baker's NEScala 2018 Presentation](https://rossabaker.github.io/boston-http4s/#2)
* [Kleisli](https://typelevel.org/cats/datatypes/kleisli.html)
* [OptionT](https://typelevel.org/cats/datatypes/optiont.html)


---
menu: main
weight: 350
title: Testing
---

## Introduction

Testing http4s `org.http4s.HttpService`'s is straightforward given that it's:

```scala
    type HttpService[F[_]] = Kleisli[OptionT[F, ?], Request[F], Response[F]]
```

The documentation explains:

```
  /**
    * A [[Kleisli]] that produces an effect to compute an [[OptionT[F]]]] from a
    * [[Request[F]]]. In case an [[OptionT.none]] is computed the server backend
    * should respond with a 404.
    * An HttpService can be run on any supported http4s
    * server backend, such as Blaze, Jetty, or Tomcat.
    */
```

So, since the above is simply a function, we can test it just like any other function.

## Example

Given the following service, which we can define through the following [Swagger](https://swagger.io/) contract:

```yaml
swagger: "2.0"
info:
  description: "Example Swagger Doc"
  version: "1.0.0"
  title: "Http4s Testing Demo"
host: "localhost"
basePath: "/api"
schemes:
- "http"
paths:
  /user/{id}:
    get:
      parameters:
      - name: "id"
        in: "path"
        description: "ID of User."
        required: true
        type: "string"
        format: "uuid"    
      summary: "Get a user by id."
      produces:
      - "application/json"
      responses:
        200:
          description: "Got representation of User."
          schema:
            $ref: "#/definitions/User"
        400:
          description: "Invalid User ID, i.e. not a UUID."
        404: 
          description: "User does not exist."
        500:
          description: "Server-side error."  
definitions:
  User:
    type: "object"
    required:
    - "name"
    - "age"
    properties:
      name:
        type: "string"
      age:
        type: "integer"
``` 

Now, let's define the `org.http4s.HttpService`. It will implement the
above contract.

```tut:book
import cats.effect._, org.http4s._, org.http4s.dsl.io._
import java.util.UUID
import scala.util.Try
import io.circe.generic._

object UserId {
  def unapply(value: String): Option[UUID] = 
    Try(UUID.fromString(value)).toOption
} 

final case class User(name: String, age: Int)
object User {
  implicit val UserEncoder: Encoder[User] = deriveEncoder[User]
}

trait UserRepo {
  def find(userId: UUID): IO[Option[User]]
}

def service(repo: UserRepo): HttpService = HttpService[IO] {
  case GET -> Root / "user" / UserId(uuid) =>
    repo.find(uuid).flatMap {
      case Some(user) => Ok(user)
      case None       => NotFound
    }
}
```

Let's define service by passing a `UserRepo` that returns `Ok(user)`. 

```tut:book
val success: UserRepo = new UserRepo {
  def find(userId: UUID): IO.pure(Some(User("johndoe", 42)))
} 

val testUuid = UUID.randomUUID

service(success).run(
  Request(method = Method.GET, uri = Uri.uri(s"/user/$testUuid") )
).unsafeRunSync
```

Next, let's check the HTTP Response's Status if the URI's Path does
not consist of a UUID:

```tut:book
service(success).run(
  Request(method = Method.GET, uri = Uri.uri(s"/user/not-a-uuid") )
).unsafeRunSync
```

## References

* [Kleisli](https://typelevel.org/cats/datatypes/kleisli.html)
* [OptionT](https://typelevel.org/cats/datatypes/optiont.html)


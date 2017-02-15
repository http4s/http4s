package org.http4s.server.middleware.authentication

import cats.data.Kleisli
import fs2.Task
import org.http4s.server.AuthMiddleware
import org.http4s._
import org.http4s.dsl._

class AuthMiddlewareSpec extends Http4sSpec {

  "AuthMiddleware" should {
    "work" in {

      type User = Long
      val userId: User = 42

      val authUser: Service[Request, Either[String, User]] =
        Kleisli(
          r =>
            if (r.pathInfo.contains("pass"))
              Task.now(Right(userId))
            else
              Task.now(Left("Unauthorized")))

      val onAuthFailure: AuthedService[String] =
        Kleisli(req => Forbidden(req.authInfo))

      val authedService: AuthedService[User] =
        AuthedService {
          case GET -> Root / path as user => Ok(user.toString)
        }

      val middleWare = AuthMiddleware(authUser, onAuthFailure)

      val service = middleWare(authedService)

      val authedResponse: Task[MaybeResponse] = service.run(Request(uri = Uri(path = "/pass")))
      val resp = authedResponse.unsafeValue().get.orNotFound.status.code
      resp must be_==(200)

      val unauthedResponse: Task[MaybeResponse] = service.run(Request(uri = Uri(path = "/")))
      val unauthResponse = unauthedResponse.unsafeValue().get.orNotFound.status.code
      unauthResponse must be_==(403)

    }
  }

}

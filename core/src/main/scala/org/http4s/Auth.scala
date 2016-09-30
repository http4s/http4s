package org.http4s
import scalaz._, Scalaz._
import scalaz.concurrent.Task

case class AuthedRequest[A](authInfo: A, req: Request)

object AuthedRequest {
  def apply[T](getUser: Request => Task[T]): Kleisli[Task, Request, AuthedRequest[T]] = Kleisli({ request =>
    getUser(request).map(user => AuthedRequest(user, request))
  })
}

package org.http4s

import cats.arrow.Choice
import cats.data._
import fs2._
import org.http4s.batteries._

package object server {
  /**
   * A middleware is a function of one [[Service]] to another, possibly of a
   * different [[Request]] and [[Response]] type.  http4s comes with several
   * middlewares for composing common functionality into services.
   *
   * @tparam A the request type of the original service
   * @tparam B the response type of the original service
   * @tparam C the request type of the resulting service
   * @tparam D the response type of the resulting service
   */
  type Middleware[A, B, C, D] = Service[A, B] => Service[C, D]

  object Middleware {
    def apply[A, B, C, D](f: (C, Service[A, B]) => Task[D]): Middleware[A, B, C, D] = {
      service => Service.lift {
        req => f(req, service)
      }
    }
  }

  /**
   * An HTTP middleware converts an [[HttpService]] to another.
   */
  type HttpMiddleware = Middleware[Request, MaybeResponse, Request, MaybeResponse]

  /**
   * An HTTP middleware that authenticates users.
   */
  type AuthMiddleware[T] = Middleware[AuthedRequest[T], MaybeResponse, Request, MaybeResponse]

  /**
    * Old name for SSLConfig
    */
  @deprecated("Use SSLConfig", "2016-12-31")
  type SSLBits = SSLConfig

  object AuthMiddleware {
    def apply[T](authUser: Service[Request, T]): AuthMiddleware[T] = {
      service => service.compose(AuthedRequest(authUser.run))
    }

    implicit def serviceChoice: Choice[Service] = new Choice[Service] {
      override def choice[A, B, C](
        f: Service[A, C],
        g: Service[B, C]): Service[Either[A, B], C] = Kleisli(
                                                               (a: Either[A, B]) =>
                                                                 a match {
                                                                   case Right(r) => g(r)
                                                                   case Left(l) => f(l)
                                                                 }
                                                             )
      override def id[A]: Service[A, A] = Kleisli[Task, A, A](Task.now)
      override def compose[A, B, C](f: Service[B, C],
        g: Service[A, B]): Service[A, C] =
        Kleisli((r: A) => g(r).flatMap(x => f(x)))
    }

    def apply[Err, T](authUser: Service[Request, Either[Err, T]],
      onFailure: Service[AuthedRequest[Err], MaybeResponse])
    : AuthMiddleware[T] = {
      service: Service[AuthedRequest[T], MaybeResponse] =>
        Choice[Service]
          .choice(onFailure, service)
          .local({ authed: AuthedRequest[Either[Err, T]] =>
            authed.authInfo.bimap(err => AuthedRequest(err, authed.req),
                                  suc => AuthedRequest(suc, authed.req)
                                 )
                 }
                )
          .compose(AuthedRequest(authUser.run))
    }
  }
}

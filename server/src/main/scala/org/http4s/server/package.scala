package org.http4s

import cats._
import scala.util.control.NonFatal

import cats.arrow.Choice
import cats.implicits._
import fs2._
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.syntax.string._
import org.log4s.getLogger

package object server {
  /**
   * A middleware is a function of one [[Service]] to another, possibly of a
   * different [[Request]] and [[Response]] type.  http4s comes with several
   * middlewares for composing common functionality into services.
   *
   * @tparam F the effect type of the services
   * @tparam A the request type of the original service
   * @tparam B the response type of the original service
   * @tparam C the request type of the resulting service
   * @tparam D the response type of the resulting service
   */
  type Middleware[F[_], A, B, C, D] = Service[F, A, B] => Service[F, C, D]

  object Middleware {
    def apply[F[_], A, B, C, D](f: (C, Service[F, A, B]) => F[D]): Middleware[F, A, B, C, D] = {
      service => Service.lift {
        req => f(req, service)
      }
    }
  }

  /**
   * An HTTP middleware converts an [[HttpService]] to another.
   */
  type HttpMiddleware[F[_]] = Middleware[F, Request[F], MaybeResponse[F], Request[F], MaybeResponse[F]]

  /**
   * An HTTP middleware that authenticates users.
   */
  type AuthMiddleware[F[_], T] = Middleware[F, AuthedRequest[F, T], MaybeResponse[F], Request[F], MaybeResponse[F]]

  /**
    * Old name for SSLConfig
    */
  @deprecated("Use SSLConfig", "2016-12-31")
  type SSLBits = SSLConfig

  object AuthMiddleware {
    def apply[F[_]: Functor: FlatMap, T](authUser: Service[F, Request[F], T]): AuthMiddleware[F, T] = {
      service => service.compose(AuthedRequest(authUser.run))
    }

    def apply[F[_], Err, T](
      authUser: Service[F, Request[F], Either[Err, T]],
      onFailure: Service[F, AuthedRequest[F, Err], MaybeResponse[F]]
    )(implicit F: Monad[F], C: Choice[Service[F, ?, ?]]): AuthMiddleware[F, T] = {
      service: Service[F, AuthedRequest[F, T], MaybeResponse[F]] =>
        C.choice(onFailure, service)
          .local { authed: AuthedRequest[F, Either[Err, T]] =>
            authed.authInfo.bimap(
                                   err => AuthedRequest(err, authed.req),
                                   suc => AuthedRequest(suc, authed.req)
                                 )
                 }
          .compose(AuthedRequest(authUser.run))
    }

  }

  private[this] val messageFailureLogger = getLogger("org.http4s.server.message-failures")
  private[this] val serviceErrorLogger = getLogger("org.http4s.server.service-errors")

  type ServiceErrorHandler[F[_]] = Request[F] => PartialFunction[Throwable, F[Response[F]]]

  def DefaultServiceErrorHandler[F[_]](implicit F: Applicative[F]): ServiceErrorHandler[F] = req => {
    case mf: MessageFailure =>
      messageFailureLogger.debug(mf)(s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr.getOrElse("<unknown>")}""")
      mf.toHttpResponse(req.httpVersion)
    case NonFatal(t) =>
      serviceErrorLogger.error(t)(s"""Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr.getOrElse("<unknown>")}""")
      F.pure(Response(Status.InternalServerError, req.httpVersion,
        Headers(
          Connection("close".ci),
          `Content-Length`.zero
        )))
  }
}

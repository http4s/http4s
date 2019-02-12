package org.http4s

import cats._
import cats.arrow.Choice
import cats.data.{Kleisli, OptionT}
import cats.implicits._
import cats.effect._
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.syntax.string._
import org.log4s.getLogger
import scala.concurrent.duration._
import scala.util.control.NonFatal
import io.chrisdavenport.vault._
import java.net.{InetAddress, InetSocketAddress}

package object server {

  object defaults {
    val AsyncTimeout: Duration = 30.seconds
    val Banner =
      """|  _   _   _        _ _
         | | |_| |_| |_ _ __| | | ___
         | | ' \  _|  _| '_ \_  _(_-<
         | |_||_\__|\__| .__/ |_|/__/
         |             |_|""".stripMargin.split("\n").toList
    val Host = InetAddress.getLoopbackAddress.getHostAddress
    val HttpPort = 8080
    val IdleTimeout: Duration = 30.seconds

    /** The time to wait for a graceful shutdown */
    val ShutdownTimeout: Duration = 30.seconds
    val SocketAddress = InetSocketAddress.createUnresolved(Host, HttpPort)
  }

  object ServerRequestKeys {
    val SecureSession: Key[Option[SecureSession]] =
      Key.newKey[IO, Option[SecureSession]].unsafeRunSync
  }

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
  type Middleware[F[_], A, B, C, D] = Kleisli[F, A, B] => Kleisli[F, C, D]

  object Middleware {
    @deprecated("Construct manually instead", "0.18")
    def apply[F[_], A, B, C, D](f: (C, Kleisli[F, A, B]) => F[D]): Middleware[F, A, B, C, D] =
      service => Kleisli(req => f(req, service))
  }

  /**
    * An HTTP middleware converts an [[HttpRoutes]] to another.
    */
  type HttpMiddleware[F[_]] =
    Middleware[OptionT[F, ?], Request[F], Response[F], Request[F], Response[F]]

  /**
    * An HTTP middleware that authenticates users.
    */
  type AuthMiddleware[F[_], T] =
    Middleware[OptionT[F, ?], AuthedRequest[F, T], Response[F], Request[F], Response[F]]

  /**
    * Old name for SSLConfig
    */
  @deprecated("Use SSLConfig", "2016-12-31")
  type SSLBits = SSLConfig

  object AuthMiddleware {

    def apply[F[_]: Monad, T](
        authUser: Kleisli[OptionT[F, ?], Request[F], T]
    ): AuthMiddleware[F, T] =
      noSpider[F, T](authUser, defaultAuthFailure[F])

    def withFallThrough[F[_]: Monad, T](
        authUser: Kleisli[OptionT[F, ?], Request[F], T]): AuthMiddleware[F, T] =
      _.compose(Kleisli((r: Request[F]) => authUser(r).map(AuthedRequest(_, r))))

    def noSpider[F[_]: Monad, T](
        authUser: Kleisli[OptionT[F, ?], Request[F], T],
        onAuthFailure: Request[F] => F[Response[F]]
    ): AuthMiddleware[F, T] = { service =>
      Kleisli { r: Request[F] =>
        authUser
          .map(AuthedRequest(_, r))
          .andThen(service.mapF(o => OptionT.liftF(o.getOrElse(Response[F](Status.NotFound)))))
          .mapF(o => OptionT.liftF(o.getOrElseF(onAuthFailure(r))))
          .run(r)
      }
    }

    def defaultAuthFailure[F[_]](implicit F: Applicative[F]): Request[F] => F[Response[F]] =
      _ => F.pure(Response[F](Status.Unauthorized))

    def apply[F[_], Err, T](
        authUser: Kleisli[F, Request[F], Either[Err, T]],
        onFailure: AuthedService[Err, F]
    )(implicit F: Monad[F], C: Choice[Kleisli[OptionT[F, ?], ?, ?]]): AuthMiddleware[F, T] = {
      service: AuthedService[T, F] =>
        C.choice(onFailure, service)
          .local { authed: AuthedRequest[F, Either[Err, T]] =>
            authed.authInfo.bimap(
              err => AuthedRequest(err, authed.req),
              suc => AuthedRequest(suc, authed.req)
            )
          }
          .compose(AuthedRequest(authUser.run).mapF(OptionT.liftF(_)))
    }
  }

  private[this] val messageFailureLogger = getLogger("org.http4s.server.message-failures")
  private[this] val serviceErrorLogger = getLogger("org.http4s.server.service-errors")

  type ServiceErrorHandler[F[_]] = Request[F] => PartialFunction[Throwable, F[Response[F]]]

  def DefaultServiceErrorHandler[F[_]](implicit F: Monad[F]): ServiceErrorHandler[F] = req => {
    case mf: MessageFailure =>
      messageFailureLogger.debug(mf)(
        s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
          .getOrElse("<unknown>")}""")
      mf.toHttpResponse(req.httpVersion)
    case NonFatal(t) =>
      serviceErrorLogger.error(t)(
        s"""Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr.getOrElse(
          "<unknown>")}""")
      F.pure(
        Response(
          Status.InternalServerError,
          req.httpVersion,
          Headers(
            Connection("close".ci),
            `Content-Length`.zero
          )))
  }
}

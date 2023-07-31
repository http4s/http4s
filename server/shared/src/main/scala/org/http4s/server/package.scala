/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.Applicative
import cats.Monad
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.SyncIO
import cats.syntax.all._
import com.comcast.ip4s
import org.http4s.headers.Connection
import org.http4s.headers.`Content-Length`
import org.typelevel.vault._

import java.net.InetAddress
import java.net.InetSocketAddress
import scala.concurrent.duration._
import scala.util.control.NonFatal

package object server {
  object defaults {
    val Banner: List[String] =
      """|  _   _   _        _ _
         | | |_| |_| |_ _ __| | | ___
         | | ' \  _|  _| '_ \_  _(_-<
         | |_||_\__|\__| .__/ |_|/__/
         |             |_|""".stripMargin.split("\n").toList

    val IPv4Host: String =
      if (Platform.isJvm || Platform.isNative)
        InetAddress.getByAddress("localhost", Array[Byte](127, 0, 0, 1)).getHostAddress
      else
        "127.0.0.1"
    val IPv6Host: String =
      if (Platform.isJvm || Platform.isNative)
        InetAddress
          .getByAddress("localhost", Array(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))
          .getHostAddress
      else "0:0:0:0:0:0:0:1"

    @deprecated(
      message =
        "Please use IPv4Host or IPv6Host. This value can change depending on Platform specific settings and can be either the canonical IPv4 or IPv6 address. If you require this behavior please call `InetAddress.getLoopbackAddress` directly.",
      since = "0.21.23",
    )
    def Host = InetAddress.getLoopbackAddress.getHostAddress
    val HttpPort = 8080

    def IPv4SocketAddress: InetSocketAddress =
      InetSocketAddress.createUnresolved(IPv4Host, HttpPort)
    def IPv6SocketAddress: InetSocketAddress =
      InetSocketAddress.createUnresolved(IPv6Host, HttpPort)

    val IPv4SocketAddressIp4s: ip4s.SocketAddress[ip4s.Ipv4Address] =
      ip4s.SocketAddress(ip4s.Ipv4Address.fromString(IPv4Host).get, ip4s.Port.fromInt(HttpPort).get)
    val IPv6SocketAddressIp4s: ip4s.SocketAddress[ip4s.Ipv6Address] =
      ip4s.SocketAddress(ip4s.Ipv6Address.fromString(IPv6Host).get, ip4s.Port.fromInt(HttpPort).get)

    @deprecated(
      message =
        "Please use IPv4SocketAddress or IPv6SocketAddress. This value can change depending on Platform specific settings and can be either the canonical IPv4 or IPv6 address. If you require this behavior please call `InetAddress.getLoopbackAddress` directly.",
      since = "0.21.23",
    )
    def SocketAddress: InetSocketAddress = InetSocketAddress.createUnresolved(Host, HttpPort)

    @deprecated("Renamed to ResponseTimeout", "0.21.0-M3")
    def AsyncTimeout: Duration = ResponseTimeout
    val ResponseTimeout: Duration = 30.seconds
    val IdleTimeout: Duration = 60.seconds

    /** The time to wait for a graceful shutdown */
    val ShutdownTimeout: Duration = 30.seconds

    /** Default max size of all headers. */
    val MaxHeadersSize: Int = 40 * 1024

    /** Default max connections */
    val MaxConnections: Int = 1024
  }

  object ServerRequestKeys {
    val SecureSession: Key[Option[SecureSession]] =
      Key.newKey[SyncIO, Option[SecureSession]].unsafeRunSync()
  }

  /** A middleware is a function of one [[cats.data.Kleisli]] to another, possibly of a
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

  /** An HTTP middleware converts an [[HttpRoutes]] to another.
    */
  type HttpMiddleware[F[_]] =
    Middleware[OptionT[F, *], Request[F], Response[F], Request[F], Response[F]]

  /** An HTTP middleware that authenticates users.
    */
  type AuthMiddleware[F[_], T] =
    Middleware[OptionT[F, *], AuthedRequest[F, T], Response[F], Request[F], Response[F]]

  /** An HTTP middleware that adds a context.
    */
  type ContextMiddleware[F[_], T] =
    Middleware[OptionT[F, *], ContextRequest[F, T], Response[F], Request[F], Response[F]]

  object AuthMiddleware {
    def apply[F[_]: Monad, T](
        authUser: Kleisli[OptionT[F, *], Request[F], T]
    ): AuthMiddleware[F, T] =
      noSpider[F, T](authUser, defaultAuthFailure[F])

    def withFallThrough[F[_]: Monad, T](
        authUser: Kleisli[OptionT[F, *], Request[F], T]
    ): AuthMiddleware[F, T] =
      _.compose(Kleisli((r: Request[F]) => authUser(r).map(AuthedRequest(_, r))))

    def noSpider[F[_]: Monad, T](
        authUser: Kleisli[OptionT[F, *], Request[F], T],
        onAuthFailure: Request[F] => F[Response[F]],
    ): AuthMiddleware[F, T] = { service =>
      Kleisli { (r: Request[F]) =>
        val resp = authUser(r).value.flatMap {
          case Some(authReq) =>
            service(AuthedRequest(authReq, r)).getOrElse(Response[F](Status.NotFound))
          case None => onAuthFailure(r)
        }
        OptionT.liftF(resp)
      }
    }

    def defaultAuthFailure[F[_]](implicit F: Applicative[F]): Request[F] => F[Response[F]] =
      _ => F.pure(Response[F](Status.Unauthorized))

    def apply[F[_], Err, T](
        authUser: Kleisli[F, Request[F], Either[Err, T]],
        onFailure: AuthedRoutes[Err, F],
    )(implicit F: Monad[F]): AuthMiddleware[F, T] =
      (routes: AuthedRoutes[T, F]) =>
        Kleisli { (req: Request[F]) =>
          OptionT {
            authUser(req).flatMap {
              case Left(err) => onFailure(AuthedRequest(err, req)).value
              case Right(suc) => routes(AuthedRequest(suc, req)).value
            }
          }
        }
  }

  private[this] val messageFailureLogger =
    Platform.loggerFactory.getLoggerFromName("org.http4s.server.message-failures")
  private[this] val serviceErrorLogger =
    Platform.loggerFactory.getLoggerFromName("org.http4s.server.service-errors")

  type ServiceErrorHandler[F[_]] = Request[F] => PartialFunction[Throwable, F[Response[F]]]

  def DefaultServiceErrorHandler[F[_]](implicit
      F: Monad[F]
  ): Request[F] => PartialFunction[Throwable, F[Response[F]]] =
    inDefaultServiceErrorHandler[F, F]

  def inDefaultServiceErrorHandler[F[_], G[_]](implicit
      F: Monad[F]
  ): Request[G] => PartialFunction[Throwable, F[Response[G]]] =
    req => {
      case mf: MessageFailure =>
        messageFailureLogger
          .debug(mf)(
            s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
                .getOrElse("<unknown>")}"""
          )
          .unsafeRunSync()
        mf.toHttpResponse[G](req.httpVersion).pure[F]
      case NonFatal(t) =>
        serviceErrorLogger
          .error(t)(
            s"""Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
                .getOrElse("<unknown>")}"""
          )
          .unsafeRunSync()
        F.pure(
          Response(
            Status.InternalServerError,
            req.httpVersion,
            Headers(
              Connection.close,
              `Content-Length`.zero,
            ),
          )
        )
    }
}

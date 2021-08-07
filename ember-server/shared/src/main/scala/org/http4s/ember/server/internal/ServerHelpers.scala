/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server.internal

import cats._
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.kernel.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import fs2.io.net._
import fs2.io.net.tls._
import org.http4s._
import org.http4s.ember.core.Drain
import org.http4s.ember.core.EmptyStreamError
import org.http4s.ember.core.Encoder
import org.http4s.ember.core.Parser
import org.http4s.ember.core.Read
import org.http4s.ember.core.Util.timeoutMaybe
import org.http4s.ember.core.Util.timeoutToMaybe
import org.http4s.headers.Connection
import org.http4s.headers.Date
import org.http4s.server.ServerRequestKeys
import org.typelevel.ci._
import org.typelevel.log4cats.Logger
import org.typelevel.vault.Vault

import java.util.Locale
import scala.concurrent.duration._

private[server] object ServerHelpers extends ServerHelpersPlatform {

  private[this] val closeCi = ci"close"
  private[this] val keepAliveCi = ci"keep-alive"
  private[this] val connectionCi = ci"connection"
  private[this] val close = Connection(NonEmptyList.of(closeCi))
  private[this] val keepAlive = Connection(NonEmptyList.one(keepAliveCi))

  private val serverFailure =
    Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)

  def server[F[_]](
      host: Option[Host],
      port: Port,
      additionalSocketOptions: List[SocketOption],
      sg: SocketGroup[F],
      httpApp: HttpApp[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      ready: Deferred[F, Either[Throwable, SocketAddress[IpAddress]]],
      shutdown: Shutdown[F],
      // Defaults
      errorHandler: Throwable => F[Response[F]],
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      maxConcurrency: Int,
      receiveBufferSize: Int,
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      idleTimeout: Duration,
      logger: Logger[F]
  )(implicit F: Async[F]): Stream[F, Nothing] = {
    val server: Stream[F, Socket[F]] =
      Stream
        .resource(sg.serverResource(host, Some(port), additionalSocketOptions))
        .attempt
        .evalTap(e => ready.complete(e.map(_._1)))
        .rethrow
        .flatMap(_._2)

    val streams: Stream[F, Stream[F, Nothing]] = server
      .interruptWhen(shutdown.signal.attempt)
      .map { connect =>
        val handler = shutdown.trackConnection >>
          Stream
            .resource(upgradeSocket(connect, tlsInfoOpt, logger))
            .flatMap(
              runConnection(
                _,
                logger,
                idleTimeout,
                receiveBufferSize,
                maxHeaderSize,
                requestHeaderReceiveTimeout,
                httpApp,
                errorHandler,
                onWriteFailure
              ))

        handler.handleErrorWith { t =>
          Stream.eval(logger.error(t)("Request handler failed with exception")).drain
        }
      }

    streams.parJoin(
      maxConcurrency
    ) // TODO: replace with forking after we fix serverResource upstream
    // StreamForking.forking(streams, maxConcurrency)
  }

  // private[internal] def reachedEndError[F[_]: Sync](
  //     socket: Socket[F],
  //     idleTimeout: Duration,
  //     receiveBufferSize: Int): Stream[F, Byte] =
  //   Stream.repeatEval(socket.read(receiveBufferSize, durationToFinite(idleTimeout))).flatMap {
  //     case None =>
  //       Stream.raiseError(new EOFException("Unexpected EOF - socket.read returned None") with NoStackTrace)
  //     case Some(value) => Stream.chunk(value)
  //   }

  private[internal] def upgradeSocket[F[_]: Monad](
      socketInit: Socket[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      logger: Logger[F]
  ): Resource[F, Socket[F]] =
    tlsInfoOpt.fold(socketInit.pure[Resource[F, *]]) { case (context, params) =>
      context
        .serverBuilder(socketInit)
        .withParameters(params)
        .withLogger(TLSLogger.Enabled(logger.trace))
        .build
        .widen[Socket[F]]
    }

  private[internal] def runApp[F[_]](
      head: Array[Byte],
      read: Read[F],
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      httpApp: HttpApp[F],
      errorHandler: Throwable => F[Response[F]],
      requestVault: Vault)(implicit F: Temporal[F]): F[(Request[F], Response[F], Drain[F])] = {

    val parse = Parser.Request.parser(maxHeaderSize)(head, read)
    val parseWithHeaderTimeout = timeoutToMaybe(
      parse,
      requestHeaderReceiveTimeout,
      F.raiseError[(Request[F], F[Option[Array[Byte]]])](new java.util.concurrent.TimeoutException(
        s"Timed Out on EmberServer Header Receive Timeout: $requestHeaderReceiveTimeout"))
    )

    for {
      tmp <- parseWithHeaderTimeout
      (req, drain) = tmp
      resp <- httpApp
        .run(req.withAttributes(requestVault))
        .handleErrorWith(errorHandler)
        .handleError(_ => serverFailure.covary[F])
    } yield (req, resp, drain)
  }

  private[internal] def send[F[_]: Temporal](socket: Socket[F])(
      request: Option[Request[F]],
      resp: Response[F],
      idleTimeout: Duration,
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit]): F[Unit] =
    Encoder
      .respToBytes[F](resp)
      .through(_.chunks.foreach(c => timeoutMaybe(socket.write(c), idleTimeout)))
      .compile
      .drain
      .attempt
      .flatMap {
        case Left(err) => onWriteFailure(request, resp, err)
        case Right(()) => Applicative[F].unit
      }

  private[internal] def postProcessResponse[F[_]: Concurrent: Clock](
      req: Request[F],
      resp: Response[F]): F[Response[F]] = {
    val connection: Connection =
      if (isKeepAlive(req.httpVersion, req.headers)) keepAlive
      else close
    for {
      date <- HttpDate.current[F].map(Date(_))
    } yield resp.withHeaders(Headers(date, connection) ++ resp.headers)
  }

  private[internal] def isKeepAlive(httpVersion: HttpVersion, headers: Headers): Boolean = {
    // We know this is raw because we have not parsed any headers in the underlying alg.
    // If Headers are being parsed into processed for in ParseHeaders this is incorrect.
    // TODO: the problem is that any string that contains `expected` is admissible
    def hasConnection(expected: String): Boolean =
      headers.headers.exists {
        case Header.Raw(name, value) =>
          name == connectionCi && value.toLowerCase(Locale.ROOT).contains(expected)
        case _ => false
      }

    httpVersion match {
      case HttpVersion.`HTTP/1.0` => hasConnection(keepAliveCi.toString)
      case HttpVersion.`HTTP/1.1` => !hasConnection(closeCi.toString)
      case _ => false
    }
  }

  private[internal] def runConnection[F[_]: Async](
      socket: Socket[F],
      logger: Logger[F],
      idleTimeout: Duration,
      receiveBufferSize: Int,
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      httpApp: HttpApp[F],
      errorHandler: Throwable => F[org.http4s.Response[F]],
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit]
  ): Stream[F, Nothing] = {
    type State = (Array[Byte], Boolean)
    val _ = logger
    val read: Read[F] = timeoutMaybe(socket.read(receiveBufferSize), idleTimeout)
    Stream.eval(mkRequestVault(socket)).flatMap { requestVault =>
      Stream
        .unfoldEval[F, State, (Request[F], Response[F])](Array.emptyByteArray -> false) {
          case (buffer, reuse) =>
            val initRead: F[Array[Byte]] = if (buffer.nonEmpty) {
              // next request has already been (partially) received
              buffer.pure[F]
            } else if (reuse) {
              // the connection is keep-alive, but we don't have any bytes.
              // we want to be on the idle timeout until the next request is received.
              read.flatMap {
                case Some(chunk) => chunk.toArray.pure[F]
                case None => Concurrent[F].raiseError(EmptyStreamError())
              }
            } else {
              // first request begins immediately
              Array.emptyByteArray.pure[F]
            }

            val result = initRead.flatMap { initBuffer =>
              runApp(
                initBuffer,
                read,
                maxHeaderSize,
                requestHeaderReceiveTimeout,
                httpApp,
                errorHandler,
                requestVault)
            }

            result.attempt.flatMap {
              case Right((req, resp, drain)) =>
                // TODO: Should we pay this cost for every HTTP request?
                // Intercept the response for various upgrade paths
                resp.attributes.lookup(org.http4s.server.websocket.websocketKey[F]) match {
                  case Some(ctx) =>
                    drain.flatMap {
                      case Some(buffer) =>
                        WebSocketHelpers
                          .upgrade(
                            socket,
                            req,
                            ctx,
                            buffer,
                            receiveBufferSize,
                            idleTimeout,
                            onWriteFailure,
                            errorHandler,
                            logger)
                          .as(None)
                      case None =>
                        Concurrent[F].pure(None)
                    }
                  case None =>
                    for {
                      nextResp <- postProcessResponse(req, resp)
                      _ <- send(socket)(Some(req), nextResp, idleTimeout, onWriteFailure)
                      nextBuffer <- drain
                    } yield nextBuffer.map(buffer => ((req, nextResp), (buffer, true)))
                }
              case Left(err) =>
                err match {
                  case EmptyStreamError() =>
                    Applicative[F].pure(None)
                  case err =>
                    errorHandler(err)
                      .handleError(_ => serverFailure.covary[F])
                      .flatMap(send(socket)(None, _, idleTimeout, onWriteFailure))
                      .as(None)
                }
            }
        }
        .takeWhile { case (_, resp) =>
          resp.headers.get[Connection].exists(_.hasKeepAlive)
        }
        .drain
    }
  }

  private def mkRequestVault[F[_]: Applicative](socket: Socket[F]) =
    (mkConnectionInfo(socket), mkSecureSession(socket)).mapN(_ ++ _)

  private def mkConnectionInfo[F[_]: Apply](socket: Socket[F]) =
    (socket.localAddress, socket.remoteAddress).mapN {
      case (local, remote) =>
        Vault.empty.insert(
          Request.Keys.ConnectionInfo,
          Request.Connection(
            local = local,
            remote = remote,
            secure = socket.isInstanceOf[TLSSocket[F]]
          )
        )
      case _ =>
        Vault.empty
    }

  private def mkSecureSession[F[_]: Applicative](socket: Socket[F]) =
    socket match {
      case socket: TLSSocket[F] =>
        socket.session
          .map(parseSSLSession)
          .map(Vault.empty.insert(ServerRequestKeys.SecureSession, _))
      case _ =>
        Vault.empty.pure[F]
    }
}

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
import fs2.{Chunk, Stream}
import fs2.io.net._
import fs2.io.net.tls._
import org.http4s._
import org.http4s.ember.core.Util.timeoutMaybe
import org.http4s.ember.core.{Encoder, Parser}
import org.http4s.headers.{Connection, Date}
import org.http4s.internal.tls.{deduceKeyLength, getCertChain}
import org.http4s.server.{SecureSession, ServerRequestKeys}
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import org.typelevel.vault.Vault

import scala.concurrent.duration._
import scodec.bits.ByteVector
import java.net.InetSocketAddress

private[server] object ServerHelpers {

  private val closeCi = CIString("close")

  private val connectionCi = CIString("connection")
  private val close = Connection(NonEmptyList.of(closeCi))
  private val keepAlive = Connection(NonEmptyList.one(CIString("keep-alive")))

  private val serverFailure =
    Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)

  def server[F[_]](
      host: Option[Host],
      port: Port,
      additionalSocketOptions: List[SocketOption],
      sg: SocketGroup[F],
      httpApp: HttpApp[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      ready: Deferred[F, Either[Throwable, InetSocketAddress]],
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
  )(implicit F: Temporal[F]): Stream[F, Nothing] = {
    val server: Stream[F, Socket[F]] =
      Stream
        .resource(sg.serverResource(host, Some(port), additionalSocketOptions))
        .attempt
        .evalTap(e => ready.complete(e.map(_._1.toInetSocketAddress)))
        .rethrow
        .flatMap(_._2)

    val streams: Stream[F, Stream[F, Nothing]] = server
      .interruptWhen(shutdown.signal.attempt)
      .map { connect =>
        shutdown.trackConnection >>
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
        .server(socketInit, params, { (s: String) => logger.trace(s) }.some)
        .widen[Socket[F]]
    }

  private[internal] def runApp[F[_]: Temporal](
      head: Array[Byte],
      read: F[Option[Chunk[Byte]]],
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      httpApp: HttpApp[F],
      errorHandler: Throwable => F[Response[F]],
      requestVault: Vault): F[(Request[F], Response[F], Option[Array[Byte]])] = {

    val parse = Parser.Request.parser(maxHeaderSize)(head, read)
    val parseWithHeaderTimeout = timeoutMaybe(parse, requestHeaderReceiveTimeout)

    for {
      tmp <- parseWithHeaderTimeout
      (req, drain) = tmp
      resp <- httpApp
        .run(req.withAttributes(requestVault))
        .handleErrorWith(errorHandler)
        .handleError(_ => serverFailure.covary[F])
      rest <- drain // TODO: handle errors?
    } yield (req, resp, rest)
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
    val reqHasClose = req.headers.headers.exists {
      case v2.Header.Raw(name, values) =>
        // TODO This will do weird shit in the odd case that close is
        // not a single, lowercase word
        name == connectionCi && values.contains(closeCi.toString)
      case _ => false
    }
    val connection: Connection =
      if (reqHasClose) close
      else keepAlive
    for {
      date <- HttpDate.current[F].map(Date(_))
    } yield resp.withHeaders(v2.Headers(date, connection) ++ resp.headers)
  }

  private[internal] def runConnection[F[_]: Temporal](
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
    val _ = logger
    val read: F[Option[Chunk[Byte]]] = timeoutMaybe(socket.read(receiveBufferSize), idleTimeout)
    Stream.eval(mkRequestVault(socket)).flatMap { requestVault =>
      Stream
        .unfoldLoopEval(Array.emptyByteArray)(incoming =>
          runApp(
            incoming,
            read,
            maxHeaderSize,
            requestHeaderReceiveTimeout,
            httpApp,
            errorHandler,
            requestVault).attempt.map {
            case Right((req, resp, rest)) => (Right((req, resp)), rest)
            case Left(e) => (Left(e), None)
          })
        .evalMap {
          case Right((req, resp)) =>
            postProcessResponse(req, resp).map(resp => (req, resp).asRight[Throwable])
          case other => other.pure[F]
        }
        .evalTap {
          case Right((request, response)) =>
            send(socket)(Some(request), response, idleTimeout, onWriteFailure)
          case Left(err) =>
            err match {
              case req: Parser.Request.ReqPrelude.ParsePreludeError
                  if req == Parser.Request.ReqPrelude.emptyStreamError =>
                Applicative[F].unit
              case err =>
                errorHandler(err)
                  .handleError(_ => serverFailure.covary[F])
                  .flatMap(send(socket)(None, _, idleTimeout, onWriteFailure))
            }
        }
        .takeWhile {
          case Left(_) => false
          case Right((req, resp)) =>
            !(
              req.headers.get[Connection].exists(_.hasClose) ||
                resp.headers.get[Connection].exists(_.hasClose)
            )
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
          .map { session =>
            (
              Option(session.getId).map(ByteVector(_).toHex),
              Option(session.getCipherSuite),
              Option(session.getCipherSuite).map(deduceKeyLength),
              Some(getCertChain(session))
            ).mapN(SecureSession.apply)
          }
          .map(Vault.empty.insert(ServerRequestKeys.SecureSession, _))
      case _ =>
        Vault.empty.pure[F]
    }
}

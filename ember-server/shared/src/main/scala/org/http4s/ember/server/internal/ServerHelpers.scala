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
import cats.effect._
import cats.effect.kernel.Resource
import cats.effect.std.Semaphore
import cats.effect.std.Supervisor
import cats.effect.syntax.all._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Pull
import fs2.Stream
import fs2.io.net._
import fs2.io.net.tls._
import org.http4s._
import org.http4s.ember.core.Drain
import org.http4s.ember.core.EmberException
import org.http4s.ember.core.Encoder
import org.http4s.ember.core.Parser
import org.http4s.ember.core.Read
import org.http4s.ember.core.Util._
import org.http4s.headers.Connection
import org.http4s.headers.Date
import org.http4s.server.ServerRequestKeys
import org.http4s.websocket.WebSocketContext
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.SelfAwareLogger
import org.typelevel.vault.Key
import org.typelevel.vault.Vault
import scodec.bits.ByteVector

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

private[server] object ServerHelpers extends ServerHelpersPlatform {

  private val serverFailure =
    Response(Status.InternalServerError).putHeaders(org.http4s.headers.`Content-Length`.zero)

  private val badRequest =
    Response(Status.BadRequest).putHeaders(org.http4s.headers.`Content-Length`.zero)

  def server[F[_]](
      host: Option[Host],
      port: Port,
      additionalSocketOptions: List[SocketOption],
      httpApp: HttpApp[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      ready: Deferred[F, Either[Throwable, SocketAddress[IpAddress]]],
      shutdown: Shutdown[F],
      // Defaults
      connectionErrorHandler: PartialFunction[Throwable, F[Unit]],
      errorHandler: Throwable => F[Response[F]],
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      maxConnections: Int,
      receiveBufferSize: Int,
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      idleTimeout: Duration,
      logger: Logger[F],
      webSocketKey: Key[WebSocketContext[F]],
      http2: Option[Http2Support[F]],
      requestLineParseErrorHandler: Throwable => F[Response[F]],
      maxHeaderSizeErrorHandler: EmberException.MessageTooLong => F[Response[F]],
      maxWebSocketFrameSize: Int,
  )(implicit F: Async[F], F2: Network[F]): Stream[F, Nothing] = {
    val server: Stream[F, Socket[F]] =
      Stream
        .resource(
          Network[F].bind(
            SocketAddress(host.getOrElse(Ipv4Address.Wildcard), port),
            additionalSocketOptions,
          )
        )
        .attempt
        .evalTap(e => ready.complete(e.map(_.address.asIpUnsafe)))
        .rethrow
        .flatMap(_.accept)
    serverInternal(
      server,
      httpApp: HttpApp[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      shutdown: Shutdown[F],
      // Defaults
      connectionErrorHandler: PartialFunction[Throwable, F[Unit]],
      errorHandler: Throwable => F[Response[F]],
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      maxConnections: Int,
      receiveBufferSize: Int,
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      idleTimeout: Duration,
      logger: Logger[F],
      createRequestVault = true,
      webSocketKey,
      http2 = http2,
      requestLineParseErrorHandler,
      maxHeaderSizeErrorHandler,
      new WebSocketHelpers(maxWebSocketFrameSize),
    )
  }

  def unixSocketServer[F[_]: Network: Async](
      unixSocketAddress: UnixSocketAddress,
      deleteIfExists: Boolean,
      deleteOnClose: Boolean,
      additionalSocketOptions: List[SocketOption],
      httpApp: HttpApp[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      ready: Deferred[F, Either[Throwable, SocketAddress[IpAddress]]],
      shutdown: Shutdown[F],
      // Defaults
      connectionErrorHandler: PartialFunction[Throwable, F[Unit]],
      errorHandler: Throwable => F[Response[F]],
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      maxConnections: Int,
      receiveBufferSize: Int,
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      idleTimeout: Duration,
      logger: Logger[F],
      webSocketKey: Key[WebSocketContext[F]],
      http2: Option[Http2Support[F]],
      requestLineParseErrorHandler: Throwable => F[Response[F]],
      maxHeaderSizeErrorHandler: EmberException.MessageTooLong => F[Response[F]],
      maxWebSocketFrameSize: Int,
  ): Stream[F, Nothing] = {
    val server =
      Stream
        .resource(
          Network[F].bind(
            unixSocketAddress,
            List(
              SocketOption.unixSocketDeleteIfExists(deleteIfExists),
              SocketOption.unixSocketDeleteOnClose(deleteOnClose),
            ) ++ additionalSocketOptions,
          )
        )
        .attempt
        .evalTap(e =>
          ready.complete(
            e.as(SocketAddress(Ipv4Address.Wildcard, port"0"))
          )
        )
        .rethrow
        .flatMap(_.accept)

    serverInternal(
      server,
      httpApp: HttpApp[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      shutdown: Shutdown[F],
      // Defaults
      connectionErrorHandler: PartialFunction[Throwable, F[Unit]],
      errorHandler: Throwable => F[Response[F]],
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      maxConnections: Int,
      receiveBufferSize: Int,
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      idleTimeout: Duration,
      logger: Logger[F],
      createRequestVault = false,
      webSocketKey,
      http2 = http2,
      requestLineParseErrorHandler,
      maxHeaderSizeErrorHandler,
      new WebSocketHelpers(maxWebSocketFrameSize),
    )
  }

  /** @param connectionErrorHandler called when an error occurs while attempting to read a connection. For example on JVM
    *                               `javax.net.ssl.SSLException` maybe be thrown if the client doesn't speak SSL. By
    *                               default this just logs the error.
    */
  def serverInternal[F[_]](
      server: Stream[F, Socket[F]],
      httpApp: HttpApp[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      shutdown: Shutdown[F],
      // Defaults
      connectionErrorHandler: PartialFunction[Throwable, F[Unit]],
      errorHandler: Throwable => F[Response[F]],
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      maxConnections: Int,
      receiveBufferSize: Int,
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      idleTimeout: Duration,
      logger: Logger[F],
      createRequestVault: Boolean,
      webSocketKey: Key[WebSocketContext[F]],
      http2: Option[Http2Support[F]],
      requestLineParseErrorHandler: Throwable => F[Response[F]],
      maxHeaderSizeErrorHandler: EmberException.MessageTooLong => F[Response[F]],
      webSocketHelpers: WebSocketHelpers,
  )(implicit F: Async[F]): Stream[F, Nothing] = {
    assert(maxConnections > 0, s"maxConnections must be > 0, was: $maxConnections")

    def runHttp1(socket: Socket[F], initialBuffer: ByteVector): F[Unit] =
      runConnection(
        socket,
        logger,
        idleTimeout,
        receiveBufferSize,
        maxHeaderSize,
        requestHeaderReceiveTimeout,
        httpApp,
        errorHandler,
        onWriteFailure,
        createRequestVault,
        webSocketKey,
        initialBuffer,
        requestLineParseErrorHandler,
        maxHeaderSizeErrorHandler,
        webSocketHelpers,
      )

    def runHttp2(socket: Socket[F], h2: Http2Support[F]): F[Unit] =
      h2.fromSocket(
        socket,
        httpApp,
        requestHeaderReceiveTimeout,
        idleTimeout,
        maxHeaderSize,
        logger,
      ).use_

    def serveSocket(socket: Socket[F], alpn: Option[String]): F[Unit] =
      (alpn, http2) match {
        case (Some("h2"), Some(h2)) =>
          h2.requireConnectionPreface(socket, idleTimeout) >> runHttp2(socket, h2)
        case (Some(_), _) =>
          // SSL Connection, not h2, will be http/1.1 but thats not how types align
          // Prior Knowledge is only allowed over clear where application
          // protocol has not been agreed via handshake
          runHttp1(socket, ByteVector.empty)
        case (None, Some(h2)) => // Cleartext Protocol
          // Http2 Prior Knowledge Check, if prelude is first bytes received tread as http2
          // Otherwise this is now http1
          h2.checkConnectionPreface(socket, idleTimeout).flatMap {
            case Left(bv) =>
              runHttp1(socket, bv) // Pass read bytes we thought might be the prelude
            case Right(_) =>
              runHttp2(socket, h2)
          }
        // Since its not enabled, run connection normally.
        case (None, None) =>
          runHttp1(socket, ByteVector.empty)
      }

    def fullConnectionErrorHandler(t: Throwable): F[Unit] =
      connectionErrorHandler.applyOrElse(
        t,
        {
          case e: EmberException.ReadTimeout =>
            logger.debug(e)("Closing connection idle past the idle timeout")
          case t: Throwable => logger.error(t)("Request handler failed with exception")
        }: Throwable => F[Unit],
      )

    // Each connection is a plain `F[Unit]` rather than an inner `Stream`, so that an idle
    // connection retains a single fiber and no fs2 scopes or interrupt contexts.
    def handleConnection(connect: Socket[F]): F[Unit] =
      F.bracket(shutdown.newConnection) { _ =>
        upgradeSocket(connect, tlsInfoOpt, logger, http2).attempt.use {
          case Right((socket, alpn)) => serveSocket(socket, alpn)
          case Left(err) => logger.warn(err)("Failed to upgrade socket to TLS")
        }
      }(_ => shutdown.removeConnection)
        .handleErrorWith(fullConnectionErrorHandler)

    // Like `parJoin`: after accepting a socket, we wait for a permit before accepting the next
    // one, each connection runs in its own fiber, and a failing connection fails the server.
    def connections(
        supervisor: Supervisor[F],
        available: Semaphore[F],
        failed: Deferred[F, Either[Throwable, Unit]],
    ): Stream[F, Nothing] = {
      def startConnection(socket: Socket[F], releaseSocket: F[Unit]): F[Unit] =
        F.uncancelable { poll =>
          poll(available.acquire).onCancel(releaseSocket) >>
            supervisor
              .supervise(
                handleConnection(socket)
                  .guarantee(releaseSocket)
                  .onError { case t => failed.complete(Left(t)).void }
                  .guarantee(available.release)
              )
              .onError { case _ => available.release >> releaseSocket }
              .void
        }

      val acceptConnections: Stream[F, Nothing] = server
        .interruptWhen(shutdown.signal.attempt)
        .flatMap { socket =>
          // The accepted socket is a resource of the outer stream's scope,
          // so we lease it until its connection is done.
          Pull
            .extendScopeTo(Stream.empty.covaryAll[F, Nothing])
            .flatMap(lease => Pull.eval(startConnection(socket, lease.compile.drain)))
            .streamNoScope
        }

      // Once we stop accepting, wait for the running connections to finish.
      val awaitConnections: Stream[F, Nothing] =
        Stream.exec(available.acquireN(maxConnections.toLong))

      (acceptConnections ++ awaitConnections).interruptWhen(failed)
    }

    // The connections lease the resources of all the scopes enclosing the accepted socket,
    // so the supervisor, which cancels the connections, must not be one of them.
    Stream.exec(
      Supervisor[F](await = false).use { supervisor =>
        (Semaphore[F](maxConnections.toLong), F.deferred[Either[Throwable, Unit]]).flatMapN {
          (available, failed) => connections(supervisor, available, failed).compile.drain
        }
      }
    )
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

  private[internal] def upgradeSocket[F[_]](
      socketInit: Socket[F],
      tlsInfoOpt: Option[(TLSContext[F], TLSParameters)],
      logger: Logger[F],
      http2: Option[Http2Support[F]],
  )(implicit F: MonadError[F, Throwable]): Resource[F, (Socket[F], Option[String])] =
    tlsInfoOpt.fold((socketInit, Option.empty[String]).pure[Resource[F, *]]) {
      case (context, params) =>
        // TODO for JS perhaps TLSParameters => TLSParameters is a platform specific way
        // As this is the only JVM specific code
        val newParams = http2.fold(params)(_.transform(params))

        Resource
          .eval {
            logger match {
              case l: SelfAwareLogger[F] =>
                l.isTraceEnabled.ifF(TLSLogger.Enabled(s => logger.trace(s)), TLSLogger.Disabled)
              case _ => TLSLogger.Enabled(s => logger.trace(s)).pure[F]
            }
          }
          .flatMap { tlsLogger =>
            context
              .serverBuilder(socketInit)
              .withParameters(newParams)
              .withLogger(tlsLogger)
              .build
              .evalMap(tlsSocket =>
                tlsSocket.write(fs2.Chunk.empty) >>
                  tlsSocket.applicationProtocol
                    .map(protocol => (tlsSocket: Socket[F], protocol.some))
                    .recover { case _: NoSuchElementException =>
                      (tlsSocket, Option.empty)
                    }
              )
          }
    }

  private[internal] def runApp[F[_]](
      head: Array[Byte],
      read: Read[F],
      maxHeaderSize: Int,
      requestHeaderReceiveTimeout: Duration,
      httpApp: HttpApp[F],
      errorHandler: Throwable => F[Response[F]],
      socket: Socket[F],
      createRequestVault: Boolean,
  )(implicit F: Temporal[F], D: Defer[F]): F[(Request[F], Response[F], Drain[F])] = {

    val parse = Parser.Request.parser(maxHeaderSize)(head, read)
    val parseWithHeaderTimeout = timeoutToMaybe(
      parse,
      requestHeaderReceiveTimeout,
      D.defer(
        F.raiseError[(Request[F], F[Option[Array[Byte]]])](
          EmberException.RequestHeadersTimeout(requestHeaderReceiveTimeout)
        )
      ),
    )

    for {
      tmp <- parseWithHeaderTimeout
      (req, drain) = tmp
      requestVault <- if (createRequestVault) mkRequestVault(socket) else Vault.empty.pure[F]
      resp <- httpApp
        .run(req.withAttributes(requestVault))
        .recover { case Parser.HeaderP.ParseHeadersError(_) =>
          badRequest.covary[F]
        }
        .handleErrorWith(errorHandler)
        .handleError(_ => serverFailure.covary[F])
    } yield (req, resp, drain)
  }

  private[internal] def send[F[_]: Temporal](socket: Socket[F])(
      request: Option[Request[F]],
      resp: Response[F],
      idleTimeout: Duration,
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
  ): F[Unit] =
    Encoder
      .respToBytes[F](request, resp)
      .through(_.chunks.foreach(c => timeoutMaybe(socket.write(c), idleTimeout)))
      .compile
      .drain
      .onError { case err =>
        onWriteFailure(request, resp, err)
      }

  private[internal] def postProcessResponse[F[_]: Concurrent: Clock](
      req: Request[F],
      resp: Response[F],
  ): F[Response[F]] = {
    val connection = connectionFor(req.httpVersion, req.headers)
    for {
      date <- HttpDate.current[F].map(Date(_))
    } yield resp.withHeaders(Headers(date, connection) ++ resp.headers)
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
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      createRequestVault: Boolean,
      webSocketKey: Key[WebSocketContext[F]],
      initialBuffer: ByteVector,
      requestLineParseErrorHandler: Throwable => F[Response[F]],
      maxHeaderSizeErrorHandler: EmberException.MessageTooLong => F[Response[F]],
      webSocketHelpers: WebSocketHelpers,
  ): F[Unit] = {
    type State = (Array[Byte], Boolean)
    val read: Read[F] = timeoutMaybe(socket.read(receiveBufferSize), idleTimeout)
      .adaptError {
        // TODO MERGE: Replace with TimeoutException on series/0.23+.
        case _: TimeoutException => EmberException.ReadTimeout(idleTimeout)
      }

    def step(state: State): F[Option[(Response[F], State)]] = {
      val (buffer, reuse) = state
      val initRead: F[Array[Byte]] = if (buffer.nonEmpty) {
        // next request has already been (partially) received
        buffer.pure[F]
      } else if (reuse) {
        // the connection is keep-alive, but we don't have any bytes.
        // we want to be on the idle timeout until the next request is received.
        read
          .flatMap {
            case Some(chunk) => chunk.toArray.pure[F]
            case None => Concurrent[F].raiseError(EmberException.EmptyStream())
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
          socket,
          createRequestVault,
        )
      }

      result.attempt.flatMap {
        case Right((req, resp, drain)) =>
          // TODO: Should we pay this cost for every HTTP request?
          // Intercept the response for various upgrade paths
          resp.attributes.lookup(webSocketKey) match {
            case Some(ctx) =>
              drain.flatMap {
                case Some(buffer) =>
                  webSocketHelpers
                    .upgrade(
                      socket,
                      req,
                      ctx,
                      buffer,
                      receiveBufferSize,
                      idleTimeout,
                      onWriteFailure,
                      errorHandler,
                      logger,
                    )
                    .as(None)
                case None =>
                  Applicative[F].pure(None)
              }
            case None =>
              for {
                nextResp <- postProcessResponse(req, resp)
                _ <- send(socket)(Some(req), nextResp, idleTimeout, onWriteFailure)
                nextBuffer <- drain
              } yield nextBuffer.map(buffer => (nextResp, (buffer, true)))
          }
        case Left(err) =>
          err match {
            case EmberException.EmptyStream() | EmberException.RequestHeadersTimeout(_) |
                EmberException.ReadTimeout(_) =>
              Applicative[F].pure(None)
            case err =>
              (err match {
                case err: Parser.HeaderP.ParseHeadersError => requestLineParseErrorHandler(err)
                case err: Parser.Request.ReqPrelude.ParsePreludeError =>
                  requestLineParseErrorHandler(err)
                case err: EmberException.MessageTooLong =>
                  maxHeaderSizeErrorHandler(err)
                case err =>
                  errorHandler(err)
              }).handleError(_ => serverFailure.covary[F])
                .flatMap(send(socket)(None, _, idleTimeout, onWriteFailure))
                .as(None)
          }
      }
    }

    // Serve requests while the connection is kept alive.
    def loop(state: State): F[Unit] =
      step(state).flatMap {
        case Some((resp, nextState)) if resp.headers.get[Connection].exists(_.hasKeepAlive) =>
          loop(nextState)
        case _ => Applicative[F].unit
      }

    loop(initialBuffer.toArray -> false).handleError(_ => ())
  }

  private def mkRequestVault[F[_]: Applicative](socket: Socket[F]): F[Vault] =
    mkSecureSession(socket).map(mkConnectionInfo(socket) ++ _)

  private def mkConnectionInfo[F[_]](socket: Socket[F]) =
    Vault.empty.insert(
      Request.Keys.ConnectionInfo,
      Request.Connection(
        local = socket.address.asIpUnsafe, // mkConnectionInfo is only used with TCP sockets
        remote = socket.peerAddress.asIpUnsafe,
        secure = socket.isInstanceOf[TLSSocket[F]],
      ),
    )

  private def mkSecureSession[F[_]: Applicative](socket: Socket[F]) =
    socket match {
      case socket: TLSSocket[F] =>
        socket.session
          .map(parseSSLSession(_))
          .map(Vault.empty.insert(ServerRequestKeys.SecureSession, _))
      case _ =>
        Vault.empty.pure[F]
    }
}

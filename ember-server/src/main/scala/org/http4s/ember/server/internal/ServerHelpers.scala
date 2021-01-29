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

import fs2._
import fs2.concurrent._
import fs2.io.tcp._
import fs2.io.tls._
import cats.effect._
import cats.effect.concurrent._
import cats.syntax.all._
import scala.concurrent.duration._
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.implicits._
import org.http4s.headers.{Connection}
import _root_.org.http4s.ember.core.{Encoder, Parser}
import _root_.org.http4s.ember.core.Util.readWithTimeout
import _root_.io.chrisdavenport.log4cats.Logger
import cats.data.NonEmptyList

private[server] object ServerHelpers {

  private val closeCi = "close".ci

  private val connectionCi = "connection".ci
  private val close = Connection(NonEmptyList.of(closeCi))
  private val keepAlive = Connection(NonEmptyList.one("keep-alive".ci))

  def server[F[_]: ContextShift](
      bindAddress: InetSocketAddress,
      httpApp: HttpApp[F],
      sg: SocketGroup,
      tlsInfoOpt: Option[(TLSContext, TLSParameters)],
      ready: Deferred[F, Either[Throwable, Unit]],
      shutdown: Shutdown[F],
      dateCaching: DateCaching[F],
      // Defaults
      onError: Throwable => Response[F] = { (_: Throwable) =>
        Response[F](Status.InternalServerError)
      },
      onWriteFailure: (Option[Request[F]], Response[F], Throwable) => F[Unit],
      maxConcurrency: Int = Int.MaxValue,
      receiveBufferSize: Int = 256 * 1024,
      maxHeaderSize: Int = 10 * 1024,
      requestHeaderReceiveTimeout: Duration = 5.seconds,
      idleTimeout: Duration = 60.seconds,
      additionalSocketOptions: List[SocketOptionMapping[_]] = List.empty,
      logger: Logger[F]
  )(implicit F: Concurrent[F], C: Clock[F]): Stream[F, Nothing] = {
    def socketReadRequest(
        socket: Socket[F],
        requestHeaderReceiveTimeout: Duration,
        receiveBufferSize: Int,
        isReused: Boolean
    ): F[Request[F]] = {
      val (initial, readDuration) = (requestHeaderReceiveTimeout, idleTimeout, isReused) match {
        case (fin: FiniteDuration, idle: FiniteDuration, true) => (true, idle + fin)
        case (fin: FiniteDuration, _, false) => (true, fin)
        case _ => (false, Duration.Zero)
      }

      SignallingRef[F, Boolean](initial).flatMap { timeoutSignal =>
        C.realTime(MILLISECONDS)
          .flatMap(now =>
            Parser.Request
              .parser(maxHeaderSize)(
                readWithTimeout[F](socket, now, readDuration, timeoutSignal.get, receiveBufferSize)
              )
              .flatMap { req =>
                timeoutSignal.set(false).as(req)
              })
      }
    }

    def upgradeSocket(
        socketInit: Socket[F],
        tlsInfoOpt: Option[(TLSContext, TLSParameters)]): Resource[F, Socket[F]] =
      tlsInfoOpt.fold(socketInit.pure[Resource[F, *]]) { case (context, params) =>
        context
          .server(socketInit, params, { (s: String) => logger.trace(s) }.some)
          .widen[Socket[F]]
      }

    def runApp(socket: Socket[F], isReused: Boolean): F[(Request[F], Response[F])] =
      for {
        req <- socketReadRequest(socket, requestHeaderReceiveTimeout, receiveBufferSize, isReused)
        resp <- httpApp.run(req).handleError(onError)
      } yield (req, resp)

    def send(socket: Socket[F])(request: Option[Request[F]], resp: Response[F]): F[Unit] =
      Encoder
        .respToBytes[F](resp)
        .through(socket.writes())
        .compile
        .drain
        .attempt
        .flatMap {
          case Left(err) => onWriteFailure(request, resp, err)
          case Right(()) => Sync[F].pure(())
        }

    def postProcessResponse(req: Request[F], resp: Response[F]): F[Response[F]] = {
      val reqHasClose = req.headers.exists {
        // We know this is raw because we have not parsed any headers in the underlying alg.
        // If Headers are being parsed into processed for in ParseHeaders this is incorrect.
        case Header.Raw(name, values) => name == connectionCi && values.contains(closeCi.value)
        case _ => false
      }
      val connection: Connection =
        if (reqHasClose) close
        else keepAlive
      for {
        date <- dateCaching.getDate
      } yield resp.withHeaders(Headers.of(date, connection) ++ resp.headers)
    }

    def withUpgradedSocket(socket: Socket[F]): Stream[F, Nothing] =
      (Stream(false) ++ Stream(true).repeat)
        .flatMap { isReused =>
          Stream
            .eval(runApp(socket, isReused).attempt)
            .evalMap {
              case Right((req, resp)) =>
                postProcessResponse(req, resp).map(resp => (req, resp).asRight[Throwable])
              case other => other.pure[F]
            }
            .evalTap {
              case Right((request, response)) => send(socket)(Some(request), response)
              case Left(err) => send(socket)(None, onError(err))
            }
        }
        .takeWhile {
          case Left(_) => false
          case Right((req, resp)) =>
            !(
              req.headers.get(Connection).exists(_.hasClose) ||
                resp.headers.get(Connection).exists(_.hasClose)
            )
        }
        .drain

    val server: Stream[F, Resource[F, Socket[F]]] =
      Stream
        .resource(
          sg.serverResource[F](bindAddress, additionalSocketOptions = additionalSocketOptions))
        .attempt
        .evalTap(e => ready.complete(e.void))
        .rethrow
        .flatMap { case (_, clients) => clients }

    val streams: Stream[F, Stream[F, Nothing]] = server
      .interruptWhen(shutdown.signal.attempt)
      .map { connect =>
        shutdown.trackConnection >>
          Stream
            .resource(connect.flatMap(upgradeSocket(_, tlsInfoOpt)))
            .flatMap(withUpgradedSocket(_))
      }

    StreamForking.forking(streams, maxConcurrency)
  }
}

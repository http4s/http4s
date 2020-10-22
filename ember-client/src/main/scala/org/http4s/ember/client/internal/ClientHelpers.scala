/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.client.internal

import org.http4s.ember.client._
import fs2.concurrent._
import fs2.io.tcp._
import cats._
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import scala.concurrent.duration._
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.implicits._
import org.http4s.client.RequestKey
import _root_.org.http4s.ember.core.{Encoder, Parser}
import _root_.org.http4s.ember.core.Util.readWithTimeout
import _root_.fs2.io.tcp.SocketGroup
import _root_.fs2.io.tls._
import _root_.io.chrisdavenport.keypool.Reusable
import javax.net.ssl.SNIHostName
import org.http4s.headers.{Connection, Date, `User-Agent`}

private[client] object ClientHelpers {
  def requestToSocketWithKey[F[_]: Concurrent: Timer: ContextShift](
      request: Request[F],
      tlsContextOpt: Option[TLSContext],
      sg: SocketGroup,
      additionalSocketOptions: List[SocketOptionMapping[_]]
  ): Resource[F, RequestKeySocket[F]] = {
    val requestKey = RequestKey.fromRequest(request)
    requestKeyToSocketWithKey[F](
      requestKey,
      tlsContextOpt,
      sg,
      additionalSocketOptions
    )
  }

  def requestKeyToSocketWithKey[F[_]: Concurrent: Timer: ContextShift](
      requestKey: RequestKey,
      tlsContextOpt: Option[TLSContext],
      sg: SocketGroup,
      additionalSocketOptions: List[SocketOptionMapping[_]]
  ): Resource[F, RequestKeySocket[F]] =
    for {
      address <- Resource.liftF(getAddress(requestKey))
      initSocket <- sg.client[F](address, additionalSocketOptions = additionalSocketOptions)
      socket <- {
        if (requestKey.scheme === Uri.Scheme.https)
          tlsContextOpt.fold[Resource[F, Socket[F]]] {
            ApplicativeError[Resource[F, *], Throwable].raiseError(
              new Throwable("EmberClient Not Configured for Https")
            )
          } { tlsContext =>
            tlsContext
              .client(
                initSocket,
                TLSParameters(serverNames = Some(List(new SNIHostName(address.getHostName)))))
              .widen[Socket[F]]
          }
        else initSocket.pure[Resource[F, *]]
      }
    } yield RequestKeySocket(socket, requestKey)

  def request[F[_]: Concurrent: ContextShift: Timer](
      request: Request[F],
      requestKeySocket: RequestKeySocket[F],
      reuseable: Ref[F, Reusable],
      chunkSize: Int,
      maxResponseHeaderSize: Int,
      timeout: Duration,
      userAgent: Option[`User-Agent`]
  ): Resource[F, Response[F]] = {
    val RT: Timer[Resource[F, *]] = Timer[F].mapK(Resource.liftK[F])

    def writeRequestToSocket(
        req: Request[F],
        socket: Socket[F],
        timeout: Option[FiniteDuration]): Resource[F, Unit] =
      Encoder
        .reqToBytes(req)
        .through(socket.writes(timeout))
        .compile
        .resource
        .drain

    def onNoTimeout(req: Request[F], socket: Socket[F]): Resource[F, Response[F]] =
      writeRequestToSocket(req, socket, None) >>
        Parser.Response.parser(maxResponseHeaderSize)(
          socket.reads(chunkSize, None)
        )

    def onTimeout(
        req: Request[F],
        socket: Socket[F],
        fin: FiniteDuration): Resource[F, Response[F]] =
      for {
        start <- RT.clock.realTime(MILLISECONDS)
        _ <- writeRequestToSocket(req, socket, Option(fin))
        timeoutSignal <- Resource.liftF(SignallingRef[F, Boolean](true))
        sent <- RT.clock.realTime(MILLISECONDS)
        remains = fin - (sent - start).millis
        resp <- Parser.Response.parser[F](maxResponseHeaderSize)(
          readWithTimeout(socket, start, remains, timeoutSignal.get, chunkSize)
        )
        _ <- Resource.liftF(timeoutSignal.set(false).void)
      } yield resp

    def writeRead(req: Request[F]) =
      timeout match {
        case t: FiniteDuration => onTimeout(req, requestKeySocket.socket, t)
        case _ => onNoTimeout(req, requestKeySocket.socket)
      }

    for {
      processedReq <- Resource.liftF(preprocessRequest(request, userAgent))
      resp <- writeRead(processedReq)
      processedResp <- postProcessResponse(processedReq, resp, reuseable)
    } yield processedResp
  }

  private[internal] def preprocessRequest[F[_]: Monad: Clock](
      req: Request[F],
      userAgent: Option[`User-Agent`]): F[Request[F]] = {
    val connection = req.headers
      .get(Connection)
      .fold(Connection(NonEmptyList.of("keep-alive".ci)))(identity)
    val userAgentHeader: Option[`User-Agent`] = req.headers.get(`User-Agent`).orElse(userAgent)
    for {
      date <- req.headers.get(Date).fold(HttpDate.current[F].map(Date(_)))(_.pure[F])
    } yield req
      .putHeaders(date, connection)
      .putHeaders(userAgentHeader.toSeq: _*)
  }

  private[internal] def postProcessResponse[F[_]: Concurrent](
      req: Request[F],
      resp: Response[F],
      canBeReused: Ref[F, Reusable]): Resource[F, Response[F]] = {
    val out = resp.copy(
      body = resp.body.onFinalizeCaseWeak {
        case ExitCase.Completed =>
          val requestClose = req.headers.get(Connection).exists(_.hasClose)
          val responseClose = resp.headers.get(Connection).exists(_.hasClose)

          if (requestClose || responseClose) Applicative[F].unit
          else canBeReused.set(Reusable.Reuse)
        case ExitCase.Canceled => Applicative[F].unit
        case ExitCase.Error(_) => Applicative[F].unit
      }
    )
    Resource.pure[F, Response[F]](out)
  }

  // https://github.com/http4s/http4s/blob/main/blaze-client/src/main/scala/org/http4s/client/blaze/Http1Support.scala#L86
  private def getAddress[F[_]: Sync](requestKey: RequestKey): F[InetSocketAddress] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse(if (s == Uri.Scheme.https) 443 else 80)
        val host = auth.host.value
        Sync[F].delay(new InetSocketAddress(host, port))
    }
}

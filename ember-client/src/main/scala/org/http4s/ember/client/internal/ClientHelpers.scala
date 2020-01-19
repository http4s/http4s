package org.http4s.ember.client.internal

import org.http4s.ember.client._
import fs2.concurrent._
import fs2.io.tcp._
import cats._
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import scala.concurrent.duration._
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.client.RequestKey
import _root_.org.http4s.ember.core.{Encoder, Parser}
import _root_.org.http4s.ember.core.Util.readWithTimeout
import _root_.fs2.io.tcp.SocketGroup
import _root_.fs2.io.tls._
import _root_.io.chrisdavenport.log4cats.Logger
import javax.net.ssl.SNIHostName

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
          } else initSocket.pure[Resource[F, *]]
      }
    } yield RequestKeySocket(socket, requestKey)

  def request[F[_]: Concurrent: ContextShift](
      request: Request[F],
      requestKeySocket: RequestKeySocket[F],
      chunkSize: Int,
      maxResponseHeaderSize: Int,
      timeout: Duration
  )(logger: Logger[F])(implicit T: Timer[F]): F[Response[F]] = {

    def onNoTimeout(socket: Socket[F]): F[Response[F]] =
      Parser.Response.parser(maxResponseHeaderSize)(
        socket
          .reads(chunkSize, None)
          .concurrently(
            Encoder
              .reqToBytes(request)
              .through(socket.writes(None))
              .drain
          )
      )(logger)

    def onTimeout(socket: Socket[F], fin: FiniteDuration): F[Response[F]] =
      for {
        start <- T.clock.realTime(MILLISECONDS)

        _ <- (
          Encoder
            .reqToBytes(request)
            .through(socket.writes(Some(fin)))
            .compile
            .drain
          )
          .start
        timeoutSignal <- SignallingRef[F, Boolean](true)
        sent <- T.clock.realTime(MILLISECONDS)
        remains = fin - (sent - start).millis
        resp <- Parser.Response.parser[F](maxResponseHeaderSize)(
          readWithTimeout(socket, start, remains, timeoutSignal.get, chunkSize)
        )(logger)
        _ <- timeoutSignal.set(false).void
      } yield resp

    timeout match {
      case t: FiniteDuration => onTimeout(requestKeySocket.socket, t)
      case _ => onNoTimeout(requestKeySocket.socket)
    }
  }

  // https://github.com/http4s/http4s/blob/master/blaze-client/src/main/scala/org/http4s/client/blaze/Http1Support.scala#L86
  private def getAddress[F[_]: Sync](requestKey: RequestKey): F[InetSocketAddress] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse { if (s == Uri.Scheme.https) 443 else 80 }
        val host = auth.host.value
        Sync[F].delay(new InetSocketAddress(host, port))
    }

}

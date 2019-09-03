package org.http4s.ember.client.internal

import org.http4s.ember.client._
import fs2.concurrent._
import fs2.io.tcp._
import cats._
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import org.http4s._
import org.http4s.client.RequestKey
import _root_.org.http4s.ember.core.{Encoder, Parser}
import _root_.org.http4s.ember.core.Util.readWithTimeout
import spinoco.fs2.crypto.io.tcp.TLSSocket
import scala.concurrent.ExecutionContext
import _root_.fs2.io.tcp.SocketGroup
import _root_.io.chrisdavenport.log4cats.Logger

private[client] object ClientHelpers {

  def requestToSocketWithKey[F[_]: Concurrent: Timer: ContextShift](
      request: Request[F],
      sslContext: Option[(ExecutionContext, SSLContext)],
      sg: SocketGroup,
      additionalSocketOptions: List[SocketOptionMapping[_]]
  ): Resource[F, RequestKeySocket[F]] = {
    val requestKey = RequestKey.fromRequest(request)
    requestKeyToSocketWithKey[F](
      requestKey,
      sslContext,
      sg,
      additionalSocketOptions
    )
  }

  def requestKeyToSocketWithKey[F[_]: Concurrent: Timer: ContextShift](
      requestKey: RequestKey,
      sslContext: Option[(ExecutionContext, SSLContext)],
      sg: SocketGroup,
      additionalSocketOptions: List[SocketOptionMapping[_]]
  ): Resource[F, RequestKeySocket[F]] =
    for {
      address <- Resource.liftF(getAddress(requestKey))
      initSocket <- sg.client[F](address, additionalSocketOptions = additionalSocketOptions)
      socket <- Resource.liftF {
        if (requestKey.scheme === Uri.Scheme.https)
          sslContext.fold[F[Socket[F]]](
            ApplicativeError[F, Throwable].raiseError(
              new Throwable("EmberClient Not Configured for Https"))
          ) {
            case (sslExecutionContext, sslContext) =>
              liftToSecure[F](
                sslExecutionContext,
                sslContext
              )(
                initSocket,
                true
              )(
                requestKey.authority.host.value,
                requestKey.authority.port.getOrElse(443)
              )
          } else Applicative[F].pure(initSocket)
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

  /** function that lifts supplied socket to secure socket **/
  def liftToSecure[F[_]: Concurrent: ContextShift](
      sslES: ExecutionContext,
      sslContext: SSLContext
  )(socket: Socket[F], clientMode: Boolean)(host: String, port: Int): F[Socket[F]] = {
    for {
      sslEngine <- Concurrent[F].delay(sslContext.createSSLEngine(host, port))
      _ <- Concurrent[F].delay(sslEngine.setUseClientMode(clientMode))
      secureSocket <- TLSSocket.instance[F](socket, sslEngine, sslES)
      _ <- secureSocket.startHandshake
    } yield secureSocket
  }.widen

  // https://github.com/http4s/http4s/blob/master/blaze-client/src/main/scala/org/http4s/client/blaze/Http1Support.scala#L86
  private def getAddress[F[_]: Concurrent](requestKey: RequestKey): F[InetSocketAddress] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse { if (s == Uri.Scheme.https) 443 else 80 }
        val host = auth.host.value
        Concurrent[F].delay(new InetSocketAddress(host, port))
    }

}

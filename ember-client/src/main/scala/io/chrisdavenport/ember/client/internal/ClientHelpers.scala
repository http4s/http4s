package org.http4s.ember.client.internal

import fs2._
import fs2.concurrent._
import fs2.io.tcp._
import cats._
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.effect.implicits._
import cats.implicits._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import javax.net.ssl.SSLContext
import org.http4s._
import org.http4s.client.RequestKey
import org.http4s.ember.core.{Encoder,Parser}
import org.http4s.ember.core.Util.readWithTimeout
import spinoco.fs2.crypto.io.tcp.TLSSocket
import scala.concurrent.ExecutionContext


object ClientHelpers {

  // lock is a semaphore for this socket. You should use a permit
  // to do anything with this.
  case class RequestKeySocket[F[_]](
    socket: Socket[F],
    requestKey: RequestKey,
    lock: Semaphore[F]
  )

  def requestToSocketWithKey[F[_]: ConcurrentEffect: Timer: ContextShift](
    request: Request[F],
    sslExecutionContext : ExecutionContext,
    sslContext: SSLContext,
    acg: AsynchronousChannelGroup
  ) = {
    implicit val ACG: AsynchronousChannelGroup = acg
    val requestKey = RequestKey.fromRequest(request)
    for {
      address <- Resource.liftF(getAddress(requestKey))
      initSocket <- io.tcp.Socket.client[F](address)
      socket <- Resource.liftF{
        if (request.uri.scheme.exists(_ === Uri.Scheme.https)) 
          // Sync[F].delay(println("Elevating Socket to ssl")) *>
          liftToSecure[F](
            sslExecutionContext, sslContext
          )(
            initSocket, true
          )(
            requestKey.authority.host.value,
            requestKey.authority.port.getOrElse(443)
          )
        else Applicative[F].pure(initSocket)
      }
      lock <- Resource.liftF(Semaphore(1))
    } yield RequestKeySocket(socket, RequestKey.fromRequest(request), lock)
  }


  def request[F[_]: ConcurrentEffect: ContextShift](
    request: Request[F]
    , requestKeySocket: RequestKeySocket[F]
    , chunkSize: Int = 32*1024
    , maxResponseHeaderSize: Int = 4096
    , timeout: Duration = 5.seconds
  )(implicit T: Timer[F]): F[Response[F]] = {

    def onNoTimeout(socket: Socket[F]): F[Response[F]] = 
      Parser.Response.parser(maxResponseHeaderSize)(
        Encoder.reqToBytes(request)
        .through(socket.writes(None))
        .last
        .onFinalize(socket.endOfOutput)
        .flatMap { _ => socket.reads(chunkSize, None)}
      )

    def onTimeout(socket: Socket[F], fin: FiniteDuration): F[Response[F]] = for {
      start <- T.clock.realTime(MILLISECONDS)
      // _ <- Sync[F].delay(println(s"Attempting to write Request $request"))
      _ <- (
        Encoder.reqToBytes(request)
        .through(socket.writes(Some(fin)))
        .compile
        .drain //>>
        // Sync[F].delay(println("Finished Writing Request"))
      ).start
      timeoutSignal <- SignallingRef[F, Boolean](true)
      sent <- T.clock.realTime(MILLISECONDS)
      remains = fin - (sent - start).millis
      resp <- Parser.Response.parser[F](maxResponseHeaderSize)(
          readWithTimeout(socket, start, remains, timeoutSignal.get, chunkSize)
      )
      _ <- timeoutSignal.set(false).void
    } yield resp

    requestKeySocket.lock.withPermit(
      timeout match {
        case t: FiniteDuration => onTimeout(requestKeySocket.socket, t)
        case _ => onNoTimeout(requestKeySocket.socket)
      }
    )
  }

  /** function that lifts supplied socket to secure socket **/
  def liftToSecure[F[_] : Concurrent : ContextShift](
    sslES: ExecutionContext, sslContext: SSLContext
  )(socket: Socket[F], clientMode: Boolean)(host: String, port: Int): F[Socket[F]] = {
    for {
      sslEngine <- Sync[F].delay(sslContext.createSSLEngine(host, port))
      _ <- Sync[F].delay(sslEngine.setUseClientMode(clientMode))
      secureSocket <- TLSSocket.instance[F](socket, sslEngine, sslES)
      _ <- secureSocket.startHandshake
    } yield secureSocket
  }.widen

  // https://github.com/http4s/http4s/blob/master/blaze-client/src/main/scala/org/http4s/client/blaze/Http1Support.scala#L86
  private def getAddress[F[_]: Sync](requestKey: RequestKey): F[InetSocketAddress] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse { if (s == Uri.Scheme.https) 443 else 80 }
        val host = auth.host.value
        Sync[F].delay(new InetSocketAddress(host, port))
    }

}
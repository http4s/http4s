package org.http4s.ember.server

import cats.implicits._
import org.http4s.server.Server
import cats.effect._
import fs2.concurrent._
import org.http4s._
import scala.concurrent.duration._
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import org.http4s.ember.server.internal.ServerHelpers

object EmberServer {

  def impl[F[_]: ConcurrentEffect: Clock: ContextShift](
      host: String,
      port: Int,
      httpApp: HttpApp[F],
      onError: Throwable => Response[F] = { _: Throwable =>
        Response[F](Status.InternalServerError)
      },
      onWriteFailure: Option[(Option[Request[F]], Response[F], Throwable) => F[Unit]] = None,
      maxConcurrency: Int = Int.MaxValue,
      receiveBufferSize: Int = 256 * 1024,
      maxHeaderSize: Int = 10 * 1024,
      requestHeaderReceiveTimeout: Duration = 5.seconds
  ): Resource[F, Server[F]] =
    for {
      socket <- Resource.liftF(Sync[F].delay(new InetSocketAddress(host, port)))
      acg <- Resource.make(
        Sync[F].delay(
          AsynchronousChannelGroup.withFixedThreadPool(100, Executors.defaultThreadFactory)
        )
      )(acg => Sync[F].delay(acg.shutdown))
      out <- unopinionated(
        socket,
        httpApp,
        acg,
        onError,
        onWriteFailure,
        maxConcurrency,
        receiveBufferSize,
        maxHeaderSize,
        requestHeaderReceiveTimeout
      )
    } yield out

  def unopinionated[F[_]: ConcurrentEffect: Clock: ContextShift](
      bindAddress: InetSocketAddress,
      httpApp: HttpApp[F],
      ag: AsynchronousChannelGroup,
      // Defaults
      onError: Throwable => Response[F] = { _: Throwable =>
        Response[F](Status.InternalServerError)
      },
      onWriteFailure: Option[(Option[Request[F]], Response[F], Throwable) => F[Unit]] = None,
      maxConcurrency: Int = Int.MaxValue,
      receiveBufferSize: Int = 256 * 1024,
      maxHeaderSize: Int = 10 * 1024,
      requestHeaderReceiveTimeout: Duration = 5.seconds
  ): Resource[F, Server[F]] =
    for {
      shutdownSignal <- Resource.liftF(SignallingRef[F, Boolean](false))
      out <- Resource.make(
        Concurrent[F]
          .start(
            ServerHelpers
              .server(
                bindAddress,
                httpApp,
                ag,
                onError,
                onWriteFailure,
                shutdownSignal.some,
                maxConcurrency,
                receiveBufferSize,
                maxHeaderSize,
                requestHeaderReceiveTimeout
              )
              .compile
              .drain
          )
          .as(
            new Server[F] {
              def address: InetSocketAddress = bindAddress
              def isSecure: Boolean = false
            }
          )
      )(_ => shutdownSignal.set(true))
    } yield out
}

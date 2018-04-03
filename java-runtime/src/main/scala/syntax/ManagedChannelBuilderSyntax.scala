package org.lyranthe.fs2_grpc.java_runtime.syntax

import cats.effect._
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import java.util.concurrent.TimeUnit
import fs2._
import scala.concurrent._

trait ManagedChannelBuilderSyntax {
  implicit final def fs2GrpcClientSyntaxManagedChannelBuilder[A <: ManagedChannelBuilder[A]](
      builder: ManagedChannelBuilder[A]): ManagedChannelBuilderOps[A] = new ManagedChannelBuilderOps[A](builder)
}

final class ManagedChannelBuilderOps[A <: ManagedChannelBuilder[A]](val builder: ManagedChannelBuilder[A])
    extends AnyVal {

  /**
    * Builds a `ManagedChannel` into a bracketed stream. The managed channel is
    * shut down when the stream is complete.  Shutdown is as follows:
    *
    * 1. We request an orderly shutdown, allowing preexisting calls to continue
    *    without accepting new calls.
    * 2. We block for up to 30 seconds on termination, using the blocking context
    * 3. If the channel is not yet terminated, we trigger a forceful shutdown
    *
    * For different tradeoffs in shutdown behavior, see {{streamWithShutdown}}.
    */
  def stream[F[_]](implicit F: Sync[F]): Stream[F, ManagedChannel] =
    streamWithShutdown { ch =>
      F.delay {
        ch.shutdown()
        if (!blocking(ch.awaitTermination(10, TimeUnit.SECONDS))) {
          ch.shutdownNow()
          ()
        }
      }
    }

  /**
    * Builds a `ManagedChannel` into a bracketed stream. The managed channel is
    * shut down when the stream is complete.
    *
    * @param shutdown Determines the behavior of the cleanup of the managed
    * channel, with respect to forceful vs. graceful shutdown and how to poll
    * or block for termination.
    */
  def streamWithShutdown[F[_]](shutdown: ManagedChannel => F[Unit])(implicit F: Sync[F]): Stream[F, ManagedChannel] =
    Stream.bracket(F.delay(builder.build()))(Stream.emit[ManagedChannel], channel => shutdown(channel))
}

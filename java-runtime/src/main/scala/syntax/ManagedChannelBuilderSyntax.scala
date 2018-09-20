package org.lyranthe.fs2_grpc
package java_runtime
package syntax

import cats.effect._
import fs2.Stream
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import java.util.concurrent.TimeUnit
import scala.concurrent._

trait ManagedChannelBuilderSyntax {
  implicit final def fs2GrpcSyntaxManagedChannelBuilder(builder: ManagedChannelBuilder[_]): ManagedChannelBuilderOps =
    new ManagedChannelBuilderOps(builder)
}

final class ManagedChannelBuilderOps(val builder: ManagedChannelBuilder[_]) extends AnyVal {

  /**
    * Builds a `ManagedChannel` into a bracketed stream. The managed channel is
    * shut down when the stream is complete.  Shutdown is as follows:
    *
    * 1. We request an orderly shutdown, allowing preexisting calls to continue
    *    without accepting new calls.
    * 2. We block for up to 30 seconds on termination, using the blocking context
    * 3. If the channel is not yet terminated, we trigger a forceful shutdown
    *
    * For different tradeoffs in shutdown behavior, see {{resourceWithShutdown}}.
    */
  def resource[F[_]](implicit F: Sync[F]): Resource[F, ManagedChannel] =
    resourceWithShutdown { ch =>
      F.delay {
        ch.shutdown()
        if (!blocking(ch.awaitTermination(30, TimeUnit.SECONDS))) {
          ch.shutdownNow()
          ()
        }
      }
    }

  /**
    * Builds a `ManagedChannel` into a bracketed resource. The managed channel is
    * shut down when the resource is released.
    *
    * @param shutdown Determines the behavior of the cleanup of the managed
    * channel, with respect to forceful vs. graceful shutdown and how to poll
    * or block for termination.
    */
  def resourceWithShutdown[F[_]](shutdown: ManagedChannel => F[Unit])(
      implicit F: Sync[F]): Resource[F, ManagedChannel] =
    Resource.make(F.delay(builder.build()))(shutdown)

  /**
    * Builds a `ManagedChannel` into a bracketed stream. The managed channel is
    * shut down when the resource is released.  Shutdown is as follows:
    *
    * 1. We request an orderly shutdown, allowing preexisting calls to continue
    *    without accepting new calls.
    * 2. We block for up to 30 seconds on termination, using the blocking context
    * 3. If the channel is not yet terminated, we trigger a forceful shutdown
    *
    * For different tradeoffs in shutdown behavior, see {{streamWithShutdown}}.
    */
  def stream[F[_]](implicit F: Sync[F]): Stream[F, ManagedChannel] =
    Stream.resource(resource[F])

  /**
    * Builds a `ManagedChannel` into a bracketed stream. The managed channel is
    * shut down when the stream is complete.
    *
    * @param shutdown Determines the behavior of the cleanup of the managed
    * channel, with respect to forceful vs. graceful shutdown and how to poll
    * or block for termination.
    */
  def streamWithShutdown[F[_]](shutdown: ManagedChannel => F[Unit])(implicit F: Sync[F]): Stream[F, ManagedChannel] =
    Stream.resource(resourceWithShutdown(shutdown))
}
package org.lyranthe.fs2_grpc
package java_runtime
package syntax

import cats.effect._
import fs2.Stream
import io.grpc.{Server, ServerBuilder}
import java.util.concurrent.TimeUnit
import scala.concurrent._

trait ServerBuilderSyntax {
  implicit final def fs2GrpcSyntaxServerBuilder(builder: ServerBuilder[_]): ServerBuilderOps =
    new ServerBuilderOps(builder)
}

final class ServerBuilderOps(val builder: ServerBuilder[_]) extends AnyVal {

  /**
    * Builds a `Server` into a bracketed resource. The server is shut
    * down when the resource is released. Shutdown is as follows:
    *
    * 1. We request an orderly shutdown, allowing preexisting calls to continue
    *    without accepting new calls.
    * 2. We block for up to 30 seconds on termination, using the blocking context
    * 3. If the server is not yet terminated, we trigger a forceful shutdown
    *
    * For different tradeoffs in shutdown behavior, see {{resourceWithShutdown}}.
    */
  def resource[F[_]](implicit F: Sync[F]): Resource[F, Server] =
    resourceWithShutdown { server =>
      F.delay {
        server.shutdown()
        if (!blocking(server.awaitTermination(30, TimeUnit.SECONDS))) {
          server.shutdownNow()
          ()
        }
      }
    }

  /**
    * Builds a `Server` into a bracketed resource. The server is shut
    * down when the resource is released.
    *
    * @param shutdown Determines the behavior of the cleanup of the
    * server, with respect to forceful vs. graceful shutdown and how
    * to poll or block for termination.
    */
  def resourceWithShutdown[F[_]](shutdown: Server => F[Unit])(implicit F: Sync[F]): Resource[F, Server] =
    Resource.make(F.delay(builder.build()))(shutdown)

  /**
    * Builds a `Server` into a bracketed stream. The server is shut
    * down when the stream is complete.  Shutdown is as follows:
    *
    * 1. We request an orderly shutdown, allowing preexisting calls to continue
    *    without accepting new calls.
    * 2. We block for up to 30 seconds on termination, using the blocking context
    * 3. If the server is not yet terminated, we trigger a forceful shutdown
    *
    * For different tradeoffs in shutdown behavior, see {{streamWithShutdown}}.
    */
  def stream[F[_]](implicit F: Sync[F]): Stream[F, Server] =
    Stream.resource(resource[F])

  /**
    * Builds a `Server` into a bracketed stream. The server is shut
    * down when the stream is complete.
    *
    * @param shutdown Determines the behavior of the cleanup of the
    * server, with respect to forceful vs. graceful shutdown and how
    * to poll or block for termination.
    */
  def streamWithShutdown[F[_]](shutdown: Server => F[Unit])(implicit F: Sync[F]): Stream[F, Server] =
    Stream.resource(resourceWithShutdown(shutdown))
}
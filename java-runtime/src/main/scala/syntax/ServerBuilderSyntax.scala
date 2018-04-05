package org.lyranthe.fs2_grpc.java_runtime.syntax

import cats.effect._
import io.grpc.{Server, ServerBuilder}
import java.util.concurrent.TimeUnit
import fs2._
import scala.concurrent._

trait ServerBuilderSyntax {
  implicit final def fs2GrpcSyntaxServerBuilder[A <: ServerBuilder[A]](
      builder: ServerBuilder[A]): ServerBuilderOps[A] = new ServerBuilderOps[A](builder)
}

final class ServerBuilderOps[A <: ServerBuilder[A]](val builder: ServerBuilder[A])
    extends AnyVal {

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
    streamWithShutdown { server =>
      F.delay {
        server.shutdown()
        if (!blocking(server.awaitTermination(30, TimeUnit.SECONDS))) {
          server.shutdownNow()
          ()
        }
      }
    }

  /**
    * Builds a `Server` into a bracketed stream. The server is shut
    * down when the stream is complete.
    *
    * @param shutdown Determines the behavior of the cleanup of the
    * server, with respect to forceful vs. graceful shutdown and how
    * to poll or block for termination.
    */
  def streamWithShutdown[F[_]](shutdown: Server => F[Unit])(implicit F: Sync[F]): Stream[F, Server] =
    Stream.bracket(F.delay(builder.build()))(Stream.emit[Server], server => shutdown(server))
}

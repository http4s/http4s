package org.http4s
package server

import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s.util.StreamApp

/**
  * Starts a server and gracefully terminates at shutdown.  The server
  * is terminated and the shutdown task is run either by a JVM shutdown
  * hook.
  *
  * More robust resource management is possible through `ProcessApp` or
  * `StreamApp`, which are introduced in http4s-0.16 and http4s-0.17,
  * respectively.
  */
@deprecated(
  "Prefer org.http4s.util.StreamApp, where main returns a Stream. You can return a Stream that runs forever from a ServerBuilder with `.serve`. Use `Stream.bracket` to compose resources in a simpler way than overriding `shutdown`.",
  "0.16"
)
trait ServerApp[F[_]] extends StreamApp[F] {
  implicit def F: Effect[F]

  /** Return a server to run */
  def server(args: List[String]): F[Server[F]]

  /** Return a task to shutdown the application.
    *
    *  This task is run as a JVM shutdown hook.
    *
    *  The default implementation shuts down the server, and waits for
    *  it to finish.  Other resources may shutdown by flatMapping this
    *  task.
    */
  def shutdown(server: Server[F]): F[Unit] =
    server.shutdown

  override final def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, Nothing] =
    Stream.bracket(server(args))(_.shutdown) *> Stream.empty
}

package org.http4s
package server

import org.http4s.util.StreamApp

import fs2.{Strategy, Stream, Task}

/**
 * Starts a server and gracefully terminates at shutdown.  The server
 * is terminated and the shutdown task is run either by a JVM shutdown
 * hook or an invocation of `requestShutdown()`.
 *
 * More robust resource management is possible through `ProcessApp` or
 * `StreamApp`, which are introduced in http4s-0.16 and http4s-0.17,
 * respectively.
 */
@deprecated("Prefer org.http4s.util.StreamApp, where main returns a Stream. You can return a Stream that runs forever from a ServerBuilder with `.serve`. Use `Stream.bracket` to compose resources in a simpler way than overriding `shutdown`.", "0.16")
trait ServerApp extends StreamApp {
  private[this] val logger = org.log4s.getLogger

  /** Return a server to run */
  def server(args: List[String]): Task[Server]

  /** Return a task to shutdown the application.
   *
   *  This task is run as a JVM shutdown hook, or when
   *  [[org.http4s.server.ServerApp.requestShutdown]] is explicitly called.
   *
   *  The default implementation shuts down the server, and waits for
   *  it to finish.  Other resources may shutdown by flatMapping this
   *  task.
   */
  def shutdown(server: Server): Task[Unit] =
    server.shutdown

  override final def stream(args: List[String]): Stream[Task, Nothing] =
    Stream.bracket(server(args))({ s =>
      Stream.eval_(Task.async[Nothing](_ => ())(Strategy.sequential))
    }, _.shutdown)
}

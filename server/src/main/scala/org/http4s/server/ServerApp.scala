package org.http4s
package server

import org.http4s.util.ProcessApp

import scalaz.concurrent.Task
import scalaz.stream.Process

/**
 * Starts a server and gracefully terminates at shutdown.  The server
 * is terminated and the shutdown task is run either by a JVM shutdown
 * hook or an invocation of `requestShutdown()`.
 *
 * More robust resource management is possible through `ProcessApp` or
 * `StreamApp`, which are introduced in http4s-0.16 and http4s-0.17,
 * respectively.
 */
@deprecated("Prefer org.http4s.util.ProcessApp, where main returns a Process. You can return a Process that runs forever from a ServerBuilder with `.serve`. Use `Process.bracket` to compose resources in a simpler way than overriding `shutdown`.", "0.16")
trait ServerApp extends ProcessApp {
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

  final def process(args: List[String]): Process[Task, Nothing] =
    Process.bracket(server(args))(s => Process.eval_(s.shutdown)) { s =>
      Process.eval_(Task.async[Nothing](_ => ()))
    }
}

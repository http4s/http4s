package org.http4s
package server

import scalaz.concurrent.Task
import scala.concurrent.duration.Duration

trait Server {
  def start: Task[this.type]

  def run(): this.type = start.run

  def shutdown: Task[this.type]

  def shutdownNow(): this.type = shutdown.run

  def onShutdown(f: => Unit): this.type
}

trait ServerBuilder { self =>
  type To <: Server

  def mountService(service: HttpService, prefix: String = ""): this.type

  def build: To

  def withPort(port: Int): this.type

  def start: Task[To] = build.start

  def run(): To = start.run
}

/** [[ServerBuilder]]s that implement this trait can set an idle timeout */
trait HasIdleTimeout { this: ServerBuilder =>
  /** Add timeout for idle connections
    * @param timeout Duration to wait for an idle connection before terminating the connection
    * @return this [[server.ServerBuilder]]
    */
  def withIdleTimeout(timeout: Duration): this.type
}

/** [[ServerBuilder]]s that implement this trait can set a connection timeout */
trait HasConnectionTimeout { this: ServerBuilder =>
  /** Add timeout for the reception of the status line
    * @param timeout Duration to wait for the reception of the status line before terminating the connection
    * @return this [[server.ServerBuilder]]
    */
  def withConnectionTimeout(timeout: Duration): this.type
}

/** [[ServerBuilder]]s that implement this trait can set a Servlet asynchronous timeout */
trait HasAsyncTimeout { this: ServerBuilder =>
  /** Add timeout for asynchronous operations
    * @param timeout Duration to wait for asynchronous operations to complete
    * @return this [[server.ServerBuilder]]
    */
  def withAsyncTimeout(timeout: Duration): this.type
}

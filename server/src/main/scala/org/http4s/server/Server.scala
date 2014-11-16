package org.http4s
package server

import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService

import scalaz.concurrent.Task
import scala.concurrent.duration._

trait Server {
  def shutdown: Task[this.type]

  def shutdownNow(): this.type = shutdown.run

  def onShutdown(f: => Unit): this.type
}

trait ServerBuilder[Self <: ServerBuilder[Self]]
  extends HasSocketAddress[Self]
{ self: Self =>
  def withExecutor(executorService: ExecutorService): Self

  def withIdleTimeout(idleTimeout: Duration): Self

  def mountService(service: HttpService, prefix: String): Self

  def start: Task[Server]

  final def run: Server = start.run
}

/**
 * Bind a server to a port.  Method names inspired by Unfiltered.
 */
trait HasSocketAddress[Self <: ServerBuilder[Self]] { self: ServerBuilder[Self] =>
  def withSocketAddress(socketAddress: InetSocketAddress): Self

  def withHttp(port: Int = 80, host: String = "0.0.0.0") =
    withSocketAddress(InetSocketAddress.createUnresolved(host, port))

  def withLocal(port: Int) = withHttp(port, "127.0.0.1")

  def withAnyLocal = withLocal(0)
}

object ServerBuilder {
  case class ServiceMount(service: HttpService, prefix: String)
}


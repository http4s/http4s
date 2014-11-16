package org.http4s
package server

import java.util.concurrent.ExecutorService

import scalaz.concurrent.Task
import scala.concurrent.duration._

trait Server {
  def shutdown: Task[this.type]

  def shutdownNow(): this.type = shutdown.run

  def onShutdown(f: => Unit): this.type
}

trait ServerBuilder[Self <: ServerBuilder[Self]] { self: Self =>
  def configure(f: Self => Self): Self = f(this)

  def withHost(host: String): Self

  def withPort(port: Int): Self

  def withExecutor(executorService: ExecutorService): Self

  def withIdleTimeout(idleTimeout: Duration): Self

  def mountService(service: HttpService, prefix: String): Self

  def start: Task[Server]

  final def run: Server = start.run
}

object ServerBuilder {
  case class ServiceMount(service: HttpService, prefix: String)
}


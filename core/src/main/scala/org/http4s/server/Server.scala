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

abstract class ServerBuilder { self =>
  type To <: Server

  protected var timeout: Duration = Duration.Inf

  def mountService(service: HttpService, prefix: String = ""): this.type

  def timeout(duration: Duration): this.type = { timeout = duration; this }

  def build: To

  def withPort(port: Int): this.type

  def start: Task[To] = build.start

  def run(): To = start.run
}


package org.http4s.server

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.ExecutorService

import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}

trait ServerBuilder {
  import ServerBuilder._

  type Self <: ServerBuilder

  def bindSocketAddress(socketAddress: InetSocketAddress): Self

  final def bindHttp(port: Int = DefaultHttpPort, host: String = DefaultHost) =
    bindSocketAddress(InetSocketAddress.createUnresolved(host, port))

  final def bindLocal(port: Int) = bindHttp(port, DefaultHost)

  final def bindAny(host: String = DefaultHost) = bindHttp(0, host)

  def withServiceExecutor(executorService: ExecutorService): Self

  def mountService(service: HttpService, prefix: String = "/"): Self

  def start: Task[Server]

  final def run: Server = start.run
}

object ServerBuilder {
  case class ServiceMount(service: HttpService, prefix: String)

  // Defaults for core server builder functionality
  val LoopbackAddress = InetAddress.getLoopbackAddress.getHostAddress
  val DefaultHost = LoopbackAddress
  val DefaultHttpPort = 8080
  val DefaultSocketAddress = InetSocketAddress.createUnresolved(DefaultHost, DefaultHttpPort)
  val DefaultServiceExecutor = Strategy.DefaultExecutorService
}

trait IdleTimeoutSupport { this: ServerBuilder =>
  def withIdleTimeout(idleTimeout: Duration): Self
}
object IdleTimeoutSupport {
  val DefaultIdleTimeout = 30.seconds
}

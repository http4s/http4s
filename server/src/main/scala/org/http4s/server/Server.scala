package org.http4s
package server

import java.util.concurrent.ExecutorService

import org.http4s.server.ServerConfig.ServiceMount

import scalaz.concurrent.{Strategy, Task}
import scala.concurrent.duration._

trait Server {
  def shutdown: Task[this.type]

  def shutdownNow(): this.type = shutdown.run

  def onShutdown(f: => Unit): this.type
}

sealed case class ServerConfig private (val attributes: AttributeMap) {
  def map(f: AttributeMap => AttributeMap) = new ServerConfig(f(attributes))

  def get[T](key: AttributeKey[T]): Option[T] = attributes.get(key)

  def getOrElse[T](key: AttributeKey[T], default: => T) = get(key).getOrElse(default)

  def put[T](key: AttributeKey[T], value: T) = map(_.put(key, value))

  def add[T](key: AttributeKey[Seq[T]], value: T) = {
    val oldValue = attributes.get(key).getOrElse(Seq.empty)
    val newValue = oldValue :+ value
    put(key, newValue)
  }

  import ServerConfig.keys

  def host = getOrElse(keys.host, "0.0.0.0")
  def withHost(host: String) = put(keys.host, host)

  def port = getOrElse(keys.port, 8080)
  def withPort(port: Int) = put(keys.port, port)

  def executor = getOrElse(keys.executor, Strategy.DefaultExecutorService)
  def withExecutor(executorService: ExecutorService) = put(keys.executor, executorService)

  def idleTimeout = getOrElse(keys.idleTimeout, 30.seconds)
  def withIdleTimeout(idleTimeout: Duration) = put(keys.idleTimeout, idleTimeout)

  def serviceMounts: Seq[ServiceMount] = getOrElse(keys.serviceMounts, Seq.empty)
  def mountService(service: HttpService, prefix: String) = add(keys.serviceMounts, ServiceMount(service, prefix))
}

object ServerConfig extends ServerConfig(AttributeMap.empty) {
  private def mkKey[T](name: String)(implicit manifest: Manifest[T]): AttributeKey[T] =
    AttributeKey.http4s("server.config.${name}")

  object keys {
    val host = AttributeKey[String]("host")
    val port = AttributeKey[Int]("port")
    val executor = AttributeKey[ExecutorService]("executor")

    /** Timeout until an idle connection is closed. */
    val idleTimeout = AttributeKey[Duration]("idle-timeout")

    val serviceMounts = AttributeKey[Seq[ServiceMount]]("service-mounts")
  }

  case class ServiceMount(service: HttpService, prefix: String)

  import keys._
}


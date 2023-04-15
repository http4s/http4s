package org.http4s.ember.client

import cats.effect._
import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder.Defaults
import org.http4s.headers.`User-Agent`

import scala.annotation.nowarn
import scala.concurrent.duration.Duration
import scala.util.chaining._

final case class Config private (
    maxTotal: Int = Defaults.maxTotal,
    idleTimeInPool: Duration = Defaults.idleTimeInPool,
    chunkSize: Int = Defaults.chunkSize,
    maxResponseHeaderSize: Int = Defaults.maxResponseHeaderSize,
    idleConnectionTime: Duration = Defaults.idleConnectionTime,
    timeout: Duration = Defaults.timeout,
    userAgent: Option[`User-Agent`] = Defaults.userAgent,
    checkEndpointIdentification: Boolean = Defaults.checkEndpointIdentification,
    serverNameIndication: Boolean = Defaults.serverNameIndication,
    enableHttp2: Boolean = Defaults.enableHttp2,
) {

  def toBuilder[F[_]: Async: Network]: EmberClientBuilder[F] =
    EmberClientBuilder.default
      .withMaxTotal(maxTotal)
      .withIdleTimeInPool(idleTimeInPool)
      .withChunkSize(chunkSize)
      .withMaxResponseHeaderSize(maxResponseHeaderSize)
      .withIdleConnectionTime(idleConnectionTime)
      .withTimeout(timeout)
      .pipe { builder =>
        userAgent match {
          case Some(value) => builder.withUserAgent(value)
          case None => builder.withoutUserAgent
        }
      }
      .withCheckEndpointAuthentication(checkEndpointIdentification)
      .withServerNameIndication(serverNameIndication)
      .pipe(builder => if (enableHttp2) builder.withHttp2 else builder.withoutHttp2)

  @nowarn("msg=never used")
  private def copy(
      maxTotal: Int,
      idleTimeInPool: Duration,
      chunkSize: Int,
      maxResponseHeaderSize: Int,
      idleConnectionTime: Duration,
      timeout: Duration,
      userAgent: Option[`User-Agent`],
      checkEndpointIdentification: Boolean,
      serverNameIndication: Boolean,
      enableHttp2: Boolean,
  ): Any = this
}

object Config {
  def apply(
      maxTotal: Int = Defaults.maxTotal,
      idleTimeInPool: Duration = Defaults.idleTimeInPool,
      chunkSize: Int = Defaults.chunkSize,
      maxResponseHeaderSize: Int = Defaults.maxResponseHeaderSize,
      idleConnectionTime: Duration = Defaults.idleConnectionTime,
      timeout: Duration = Defaults.timeout,
      userAgent: Option[`User-Agent`] = Defaults.userAgent,
      checkEndpointIdentification: Boolean = Defaults.checkEndpointIdentification,
      serverNameIndication: Boolean = Defaults.serverNameIndication,
      enableHttp2: Boolean = Defaults.enableHttp2,
  ): Config = new Config(
    maxTotal,
    idleTimeInPool,
    chunkSize,
    maxResponseHeaderSize,
    idleConnectionTime,
    timeout,
    userAgent,
    checkEndpointIdentification,
    serverNameIndication,
    enableHttp2,
  )

  @nowarn("msg=never used")
  private def unapply(c: Config): Any = this
}

/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.client

import cats.effect._
import fs2.io.net.Network
import org.http4s.ember.client.Config._
import org.http4s.ember.client.EmberClientBuilder.Defaults
import org.http4s.headers.`User-Agent`

import scala.annotation.nowarn
import scala.concurrent.duration.Duration

@SuppressWarnings(Array("scalafix:Http4sGeneralLinters.nonValidatingCopyConstructor"))
// Whenever you add a new field to maintain backward compatibility:
//  * add the field to `copy`
//  * create a new `apply`
//  * add a new case to `fromProduct`
//
// The default values in the constructor are not actually applied (defaults from `apply` are).
// But they still need to be present to enable tools like PureConfig.
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

  def fromProduct(p: Product): Config = p.productArity match {
    case 10 =>
      Config(
        p.productElement(0).asInstanceOf[Int],
        p.productElement(1).asInstanceOf[Duration],
        p.productElement(2).asInstanceOf[Int],
        p.productElement(3).asInstanceOf[Int],
        p.productElement(4).asInstanceOf[Duration],
        p.productElement(5).asInstanceOf[Duration],
        p.productElement(6).asInstanceOf[Option[`User-Agent`]],
        p.productElement(7).asInstanceOf[Boolean],
        p.productElement(8).asInstanceOf[Boolean],
        p.productElement(9).asInstanceOf[Boolean],
      )
  }

  @nowarn("msg=never used")
  private def unapply(c: Config): Any = this

  implicit private[client] final class ChainingOps[A](private val self: A) extends AnyVal {
    def pipe[B](f: A => B): B = f(self)
  }
}

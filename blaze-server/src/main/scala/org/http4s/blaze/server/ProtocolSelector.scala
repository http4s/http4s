/*
 * Copyright 2014 http4s.org
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

package org.http4s
package blaze
package server

import cats.effect.ConcurrentEffect
import cats.effect.Timer
import org.http4s.blaze.http.http2.DefaultFlowStrategy
import org.http4s.blaze.http.http2.Http2Settings
import org.http4s.blaze.http.http2.server.ALPNServerSelector
import org.http4s.blaze.http.http2.server.ServerPriorKnowledgeHandshaker
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.TailStage
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.server.ServiceErrorHandler
import org.typelevel.vault._

import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/** Facilitates the use of ALPN when using blaze http2 support */
private[http4s] object ProtocolSelector {
  def apply[F[_]](
      engine: SSLEngine,
      httpApp: HttpApp[F],
      maxRequestLineLen: Int,
      maxHeadersLen: Int,
      chunkBufferMaxSize: Int,
      requestAttributes: () => Vault,
      executionContext: ExecutionContext,
      serviceErrorHandler: ServiceErrorHandler[F],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      scheduler: TickWheelExecutor,
      maxWebSocketBufferSize: Option[Int],
  )(implicit F: ConcurrentEffect[F], timer: Timer[F]): ALPNServerSelector = {
    def http2Stage(): TailStage[ByteBuffer] = {
      val newNode = { (streamId: Int) =>
        LeafBuilder(
          new Http2NodeStage(
            streamId,
            Duration.Inf,
            executionContext,
            requestAttributes,
            httpApp,
            serviceErrorHandler,
            responseHeaderTimeout,
            idleTimeout,
            scheduler,
          )
        )
      }

      val localSettings =
        Http2Settings.default.copy(
          maxConcurrentStreams = 100, // TODO: configurable?
          maxHeaderListSize = maxHeadersLen,
        )

      new ServerPriorKnowledgeHandshaker(
        localSettings = localSettings,
        flowStrategy = new DefaultFlowStrategy(localSettings),
        nodeBuilder = newNode,
      )
    }

    def http1Stage(): TailStage[ByteBuffer] =
      Http1ServerStage[F](
        httpApp,
        requestAttributes,
        executionContext,
        enableWebSockets = false,
        maxRequestLineLen,
        maxHeadersLen,
        chunkBufferMaxSize,
        serviceErrorHandler,
        responseHeaderTimeout,
        idleTimeout,
        scheduler,
        maxWebSocketBufferSize,
      )

    def preference(protos: Set[String]): String =
      protos
        .find {
          case "h2" | "h2-14" | "h2-15" => true
          case _ => false
        }
        .getOrElse("undefined")

    def select(s: String): LeafBuilder[ByteBuffer] =
      LeafBuilder(s match {
        case "h2" | "h2-14" | "h2-15" => http2Stage()
        case _ => http1Stage()
      })

    new ALPNServerSelector(engine, preference, select)
  }
}

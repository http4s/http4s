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
package server
package blaze

import cats.effect.Async
import cats.effect.std.Dispatcher
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import org.http4s.blaze.http.http2.{DefaultFlowStrategy, Http2Settings}
import org.http4s.blaze.http.http2.server.{ALPNServerSelector, ServerPriorKnowledgeHandshaker}
import org.http4s.blaze.pipeline.{LeafBuilder, TailStage}
import org.http4s.blaze.util.TickWheelExecutor
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import io.chrisdavenport.vault._

/** Facilitates the use of ALPN when using blaze http2 support */
private[blaze] object ProtocolSelector {
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
      D: Dispatcher[F])(implicit F: Async[F]): ALPNServerSelector = {
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
            D
          ))
      }

      val localSettings =
        Http2Settings.default.copy(
          maxConcurrentStreams = 100, // TODO: configurable?
          maxHeaderListSize = maxHeadersLen)

      new ServerPriorKnowledgeHandshaker(
        localSettings = localSettings,
        flowStrategy = new DefaultFlowStrategy(localSettings),
        nodeBuilder = newNode)
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
        D
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

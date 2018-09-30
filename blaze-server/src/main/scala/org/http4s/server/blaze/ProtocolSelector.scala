package org.http4s
package server
package blaze

import cats.effect.ConcurrentEffect
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import org.http4s.blaze.http.http2.{DefaultFlowStrategy, Http2Settings}
import org.http4s.blaze.http.http2.server.{ALPNServerSelector, ServerPriorKnowledgeHandshaker}
import org.http4s.blaze.pipeline.{LeafBuilder, TailStage}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/** Facilitates the use of ALPN when using blaze http2 support */
private[blaze] object ProtocolSelector {
  def apply[F[_]: ConcurrentEffect](
      engine: SSLEngine,
      httpApp: HttpApp[F],
      maxRequestLineLen: Int,
      maxHeadersLen: Int,
      requestAttributes: AttributeMap,
      executionContext: ExecutionContext,
      serviceErrorHandler: ServiceErrorHandler[F]): ALPNServerSelector = {

    def http2Stage(): TailStage[ByteBuffer] = {
      val newNode = { streamId: Int =>
        LeafBuilder(
          new Http2NodeStage(
            streamId,
            Duration.Inf,
            executionContext,
            requestAttributes,
            httpApp,
            serviceErrorHandler))
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
        serviceErrorHandler
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

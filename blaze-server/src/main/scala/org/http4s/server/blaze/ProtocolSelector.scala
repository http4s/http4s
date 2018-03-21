package org.http4s
package server
package blaze

import cats.effect.Effect
import java.nio.ByteBuffer

import javax.net.ssl.SSLEngine
import org.http4s.blaze.http.http20._
import org.http4s.blaze.pipeline.{LeafBuilder, TailStage}
import org.http4s.server.logging.ServerLogging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/** Facilitates the use of ALPN when using blaze http2 support */
private[blaze] object ProtocolSelector {
  def apply[F[_]: Effect](
      engine: SSLEngine,
      service: HttpService[F],
      maxRequestLineLen: Int,
      maxHeadersLen: Int,
      requestAttributes: AttributeMap,
      executionContext: ExecutionContext,
      serviceErrorHandler: ServiceErrorHandler[F],
      serverLogging: ServerLogging[F]): ALPNSelector = {

    def http2Stage(): TailStage[ByteBuffer] = {

      val newNode = { streamId: Int =>
        LeafBuilder(
          new Http2NodeStage(
            streamId,
            Duration.Inf,
            executionContext,
            requestAttributes,
            service,
            serviceErrorHandler,
            serverLogging))
      }

      Http2Stage(
        nodeBuilder = newNode,
        timeout = Duration.Inf,
        ec = executionContext,
        // since the request line is a header, the limits are bundled in the header limits
        maxHeadersLength = maxHeadersLen,
        maxInboundStreams = 256 // TODO: this is arbitrary...
      )
    }

    def http1Stage(): TailStage[ByteBuffer] =
      Http1ServerStage[F](
        service,
        requestAttributes,
        executionContext,
        enableWebSockets = false,
        maxRequestLineLen,
        maxHeadersLen,
        serviceErrorHandler,
        serverLogging
      )

    def preference(protos: Seq[String]): String =
      protos
        .find {
          case "h2" | "h2-14" | "h2-15" => true
          case _ => false
        }
        .getOrElse("http1.1")

    def select(s: String): LeafBuilder[ByteBuffer] =
      LeafBuilder(s match {
        case "h2" | "h2-14" | "h2-15" => http2Stage()
        case _ => http1Stage()
      })

    new ALPNSelector(engine, preference, select)
  }
}

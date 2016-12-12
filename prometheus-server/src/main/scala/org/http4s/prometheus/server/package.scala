package org.http4s
package prometheus

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.http4s.Http4s._
import org.http4s.util.io.captureWriter
import scalaz.concurrent.{Strategy, Task}

package object server {
  def exportResponse(registry: CollectorRegistry = CollectorRegistry.defaultRegistry)(implicit S: Strategy): Task[Response] = {
    Response(Status.Ok)
      .withBody(captureWriter { w =>
        TextFormat.write004(w, registry.metricFamilySamples)
      })
      .putHeaders(Header("Content-Type", TextFormat.CONTENT_TYPE_004))
  }

  def exportService(registry: CollectorRegistry = CollectorRegistry.defaultRegistry)(implicit S: Strategy): HttpService =
    HttpService {
      case req if req.method == Method.GET && req.pathInfo == "/" =>
        exportResponse(registry)
    }
}

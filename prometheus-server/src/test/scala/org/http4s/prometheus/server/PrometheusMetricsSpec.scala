package org.http4s
package prometheus
package server

import scalaz.concurrent.Task

import io.prometheus.client._
import org.http4s.dsl._
import org.http4s.headers._
import org.http4s.Http4sSpec
import org.http4s.Uri.uri

class PrometheusMetricsSpec extends Http4sSpec {
  "exportResponse" should {
    "have media type text/plain" in {
      exportResponse().run.contentType.map(_.mediaType) must beSome(MediaType.`text/plain`.withExtensions(Map("version" -> "0.0.4")))
    }

    "render metrics" in {
      val registry = new CollectorRegistry
      val service = Metrics(registry = registry)(HttpService.empty)
      exportResponse(registry).as[String].run must contain ("# TYPE http_request_time_seconds histogram")
    }
  }

  "exportService" should {
    "respond to GET at /" in {
      exportService().run(Request(GET, uri("/"))).run.status must_== (Status.Ok)
    }
  }
}

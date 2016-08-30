package org.http4s.zipkin

import java.time.Instant

import org.http4s.{HttpService, Request}
import org.http4s.client.Client
import org.http4s.headers._
import org.http4s.zipkin.core._
import org.http4s.zipkin.middleware.client.ClientRequirements
import org.http4s.zipkin.middleware.server.ServerRequirements

import scalaz._
import scalaz.concurrent.Task

package object middleware {



  def buildZipkinInfo(
    debug: Boolean,
    name: String,
    instant: Instant,
    host: Endpoint,
    traceId: `X-B3-TraceId`,
    spanId: `X-B3-SpanId`,
    parentId: Option[`X-B3-ParentSpanId`]
  ): AnnotationType => ZipkinInfo = { annotationType =>
    val annotation = Annotation(
      timestamp = instant,
      value = annotationType,
      endpoint = host
    )
    ZipkinInfo(
      name,
      debug,
      traceId,
      spanId,
      parentId,
      timestamp = None,
      duration = None,
      List(annotation),
      // TODO: Stubbed
      List.empty
    )
  }

  def endpointFromRequest(request: Request)(serviceName: String): Endpoint = {
    Endpoint(
      ipv4 = request.uri.host.get.toString, // YOLO
      port = request.uri.port.get, // YOLO
      serviceName = serviceName,
      ipv6 = None
    )
  }
}

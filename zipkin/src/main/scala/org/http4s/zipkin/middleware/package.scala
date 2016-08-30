package org.http4s.zipkin

import java.time.Instant

import org.http4s.{HttpService, Request}
import org.http4s.client.Client
import org.http4s.headers._
import org.http4s.zipkin.algebras.CollectorOp
import org.http4s.zipkin.models._

import scalaz._
import scalaz.concurrent.Task

package object middleware {
  type ServiceName = String

  type ZipkinClient = Reader[ClientRequirements, Client]
  type ZipkinService = Reader[ServerRequirements, HttpService]

  def sendToCollector(zipkinInfo: ZipkinInfo)(collectorInterpreter: CollectorOp ~> Task): Task[Unit] =
    CollectorOp.send(zipkinInfo).foldMap(Coyoneda.liftTF(collectorInterpreter))

  def nameFromRequest(request: Request): String =
    s"${request.method} ${request.uri.path}"

  def buildZipkinInfo(name: String,
    instant: Instant, host: Endpoint, traceId: `X-B3-TraceId`, spanId: `X-B3-SpanId`, parentId: Option[`X-B3-ParentSpanId`]
  ): AnnotationType => ZipkinInfo = { annotationType =>
    val annotation = Annotation(
      timestamp = instant,
      value = annotationType,
      endpoint = host
    )
    ZipkinInfo(
      name,
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
      iPv6 = None
    )
  }
}

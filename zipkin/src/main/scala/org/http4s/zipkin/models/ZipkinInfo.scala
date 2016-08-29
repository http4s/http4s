package org.http4s.zipkin.models

import java.time.Instant

import org.http4s.headers.{`X-B3-ParentSpanId`, `X-B3-SpanId`, `X-B3-TraceId`}

final case class Endpoint(
  ipv4: String,
  port: Int,
  serviceName: String
)

final case class Annotation(
  timestamp: Instant,
  value: AnnotationType,
  endpoint: Endpoint
)

// TODO: This can contain additional info we want to send to the collector.
final case class BinaryAnnotation(/* stuff here */)

sealed trait AnnotationType
object AnnotationType {
  case object ClientSend extends AnnotationType
  case object ServerReceive extends AnnotationType
  case object ServerSend extends AnnotationType
  case object ClientReceive extends AnnotationType
}

final case class ZipkinInfo(
  name: String,
  traceId: `X-B3-TraceId`, spanId: `X-B3-SpanId`, parentSpanId: Option[`X-B3-ParentSpanId`],
  annotations: List[Annotation],
  binaryAnnotations: List[BinaryAnnotation]
)

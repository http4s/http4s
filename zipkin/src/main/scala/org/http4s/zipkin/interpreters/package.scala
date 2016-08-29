package org.http4s.zipkin

import java.time.Instant

import argonaut.Argonaut._
import argonaut._
import org.http4s.util.StringWriter
import org.http4s.zipkin.models._

package object interpreters {
  implicit def EndpointCodecJson: EncodeJson[Endpoint] = {
    EncodeJson((e: Endpoint) =>
      ("ipv4" := e.ipv4) ->:
        ("port" := e.port) ->:
        //TODO: Stubbed to localhost for now
        ("ipv6" := "0:0:0:0:0:0:0:1") ->:
        ("serviceName" := e.serviceName) ->:
        jEmptyObject
    )

  }

  implicit def JavaTimeInstantEncodeJson: EncodeJson[Instant] = {
    EncodeJson((instant: java.time.Instant) =>
      jNumber(instant.toEpochMilli * 1000))
  }

  implicit def AnnotationEncodeJson: EncodeJson[Annotation] =
    EncodeJson((a: Annotation) =>
      ("timestamp" := a.timestamp) ->:
        ("value" := a.value) ->:
        ("endpoint" := a.endpoint) ->:
        jEmptyObject
    )

  implicit def BinaryAnnotationEncodeJson: EncodeJson[BinaryAnnotation] =
    EncodeJson((a: BinaryAnnotation) =>
        jEmptyObject
    )

  implicit def ZipkinInfoEncodeJson: EncodeJson[ZipkinInfo] =
    EncodeJson((a: ZipkinInfo) =>
      ("name" := a.name) ->:
        ("traceId" := a.traceId.renderValue(new StringWriter).result) ->:
        ("id" := a.spanId.renderValue(new StringWriter).result) ->:
        ("parentId" := a.parentSpanId.map(_.renderValue(new StringWriter).result)
          .getOrElse(a.spanId.renderValue(new StringWriter).result)) ->:
        ("annotations" := a.annotations) ->:
        ("binaryAnnotations" := a.binaryAnnotations) ->:
        //TODO: Stubs, for now.
        ("debug" := true) ->:
        //TODO: Stubs, for now.
//        ("duration" := 1234) ->:
        ("timestamp" := a.annotations.head.timestamp) ->: // YOLO
        jEmptyObject
    )

  implicit def AnnotationTypeEncodeJson: EncodeJson[AnnotationType] = {
    EncodeJson((at: AnnotationType) => at match {
      case AnnotationType.ClientReceive =>
        jString("cr")
      case AnnotationType.ClientSend =>
        jString("cs")
      case AnnotationType.ServerReceive =>
        jString("sr")
      case AnnotationType.ServerSend =>
        jString("ss")
    })
  }

}

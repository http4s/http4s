package org.http4s.zipkin.core.interpreters

import java.time.{Duration, Instant}

import argonaut.Argonaut._
import argonaut._
import org.http4s.util.StringWriter
import org.http4s.zipkin.core._


package object collector {
  implicit def EndpointCodecJson: EncodeJson[Endpoint] = {
    //TODO: Stubbed
//    def toIpv6(iPv4: String): String =  "0:0:0:0:0:0:0:1"
    EncodeJson((e: Endpoint) =>
      ("ipv4" := e.ipv4) ->:
        ("port" := e.port) ->:
        ("ipv6" := "0:0:0:0:0:0:0:1") ->:
        ("serviceName" := e.serviceName) ->:
        jEmptyObject
    )

  }

  implicit def JavaTimeInstantEncodeJson: EncodeJson[Instant] = {
    EncodeJson((instant: java.time.Instant) =>
      jNumber(instant.toEpochMilli * 1000))
  }

  implicit def JavaTimeDurationEncodeJson: EncodeJson[Duration] = {
    EncodeJson((duration: java.time.Duration) =>
      jNumber(duration.toMillis * 1000))
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
        ("duration" := a.duration) ->:
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

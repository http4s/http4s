package org.http4s.zipkin.core

import org.http4s.headers._

sealed trait ZipkinIds

final case class ServerIds(
  spanId: `X-B3-SpanId`, traceId: `X-B3-TraceId`, parentId: Option[`X-B3-ParentSpanId`]) extends ZipkinIds
final case class ClientIds(
  spanId: `X-B3-SpanId`, traceId: `X-B3-TraceId`, parentId: `X-B3-ParentSpanId`) extends ZipkinIds

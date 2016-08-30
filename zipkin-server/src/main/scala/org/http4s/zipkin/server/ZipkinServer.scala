package org.http4s.zipkin.server

import java.time.Instant

import org.http4s.headers.{`X-B3-ParentSpanId`, `X-B3-SpanId`, `X-B3-TraceId`}
import org.http4s.zipkin.core.algebras.{Clock, Collector, Randomness}
import org.http4s.zipkin.core.{AnnotationType, Endpoint, ServerIds}
import org.http4s.zipkin.middleware._
import org.http4s.{Service, _}
import org.http4s.zipkin.middleware.server.ZipkinService
import org.http4s.zipkin.core._

import scalaz.{Kleisli, Scalaz}

object ZipkinServer {

  def apply(
    collector: Collector,
    randomness: Randomness,
    clock: Clock,
    endpoint: Endpoint
  )(zipkinService: ZipkinService): HttpService = {

    Service.lift { request =>
      val name = nameFromRequest(request)
      for {
        freshSpanId <- Randomness.getRandomLong(randomness)
        serverIds = serverIdsFromHeaders(request.headers)(freshSpanId)

        srInfo <- Clock.getInstant(clock).map(serverReceiveInfo(true, name, endpoint, serverIds))
        _ <- Collector.send(srInfo)(collector)

        responseForOurClient <- zipkinService.run(
          ServerRequirements(serverIds))(request)

        ssInfo <- Clock.getInstant(clock).map(serverSendInfo(true, name, endpoint, serverIds))
        _ <- Collector.send(ssInfo)(collector)

      } yield responseForOurClient
    }
  }

  // When we are a server on the edge of the cluster,
  // we generate our own span id and trace id
  // (which we set to the span id out of convention).
  def serverIdsFromHeaders(headers: Headers)(freshSpanId: Long): ServerIds = {
    val generatedIds = ServerIds(
      spanId = `X-B3-SpanId`(freshSpanId),
      traceId = `X-B3-TraceId`(freshSpanId),
      parentId = None)
    maybeZipkinIds(headers).getOrElse(generatedIds)
  }

  def maybeZipkinIds(headers: Headers): Option[ServerIds] = {
    for {
      traceId <- headers.get(`X-B3-TraceId`)
      spanId <- headers.get(`X-B3-SpanId`)
    } yield ServerIds(spanId, traceId, headers.get(`X-B3-ParentSpanId`))

  }

  def serverSendInfo(debug: Boolean, name: String, host: Endpoint, ids: ServerIds)(instant: Instant) =
    buildZipkinInfo(
      debug,
      name,
      instant,host,ids.traceId, ids.spanId, ids.parentId
    )(AnnotationType.ServerSend)

  def serverReceiveInfo(debug: Boolean, name: String, host: Endpoint, ids: ServerIds)(instant: Instant) =
    buildZipkinInfo(
      debug,
      name,
      instant,host,ids.traceId, ids.spanId, ids.parentId
    )(AnnotationType.ServerReceive)


  def liftVanilla(service: HttpService): ZipkinService =
    lift(_ => service)

  def lift(serviceFn: ServerRequirements => HttpService): ZipkinService =
    Kleisli.kleisli[Scalaz.Id, ServerRequirements, HttpService](
      serviceFn)
}

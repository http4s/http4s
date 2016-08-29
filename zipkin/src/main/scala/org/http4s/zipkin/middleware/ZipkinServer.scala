package org.http4s.zipkin.middleware

import java.time.Instant

import org.http4s.headers.{`X-B3-ParentSpanId`, `X-B3-SpanId`, `X-B3-TraceId`}
import org.http4s.zipkin.algebras.{Clock, CollectorInterpreter, Randomness}
import org.http4s.zipkin.models.{AnnotationType, Endpoint, ServerIds}
import org.http4s.{Service, _}

import scalaz.concurrent.Task
import scalaz.{Kleisli, Scalaz}

object ZipkinServer {

  def apply(
    collectorInterpreter: CollectorInterpreter,
    randomness: Randomness,
    clock: Clock,
    endpoint: Endpoint
  )(zipkinService: ZipkinService): HttpService = {

    Service.lift { request =>
      val name = nameFromRequest(request)
      for {
        freshSpanId <- Randomness.getRandomLong(randomness)
        serverIds = serverIdsFromHeaders(request.headers)(freshSpanId)

        srInfo <- Clock.getInstant(clock).map(serverReceiveInfo(name, endpoint, serverIds))
        _ <- sendToCollector(srInfo)(collectorInterpreter)

        responseForOurClient <- zipkinService.run(
          ServerRequirements(serverIds))(request)

        ssInfo <- Clock.getInstant(clock).map(serverSendInfo(name, endpoint, serverIds))
        _ <- sendToCollector(ssInfo)(collectorInterpreter)

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

  def serverSendInfo(name: String, host: Endpoint, ids: ServerIds)(instant: Instant) =
    buildZipkinInfo(
      name,
      instant,host,ids.traceId, ids.spanId, ids.parentId
    )(AnnotationType.ServerSend)

  def serverReceiveInfo(name: String, host: Endpoint, ids: ServerIds)(instant: Instant) =
    buildZipkinInfo(
      name,
      instant,host,ids.traceId, ids.spanId, ids.parentId
    )(AnnotationType.ServerReceive)


  def liftVanilla(service: HttpService): ZipkinService =
    lift(_ => service)

  def lift(serviceFn: ServerRequirements => HttpService): ZipkinService =
    Kleisli.kleisli[Scalaz.Id, ServerRequirements, HttpService](
      serviceFn)
}

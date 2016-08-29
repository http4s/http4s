package org.http4s.zipkin.middleware

import java.time.Instant

import org.http4s.client.{Client, DisposableResponse}
import org.http4s.headers._
import org.http4s.zipkin.algebras.{Clock, CollectorInterpreter, Randomness}
import org.http4s.zipkin.models.{AnnotationType, ClientIds, Endpoint, ServerIds}
import org.http4s.{Request, Service}

import scalaz.concurrent.Task
import scalaz.{Kleisli, Scalaz}

object ZipkinClient {

  def apply(
    collectorInterpreter: CollectorInterpreter,
    randomness: Randomness,
    clock: Clock
  )(client: Client): ZipkinClient = {

    def updateOpen(
      open: Service[Request, DisposableResponse],
      clientRequirements: ClientRequirements
    ): Service[Request, DisposableResponse] = {

      Service.lift { request =>
        val endpoint = endpointFromRequest(request)(clientRequirements.serviceName)
        val name = nameFromRequest(request)
        for {
          freshSpanId <- Randomness.getRandomLong(randomness)
          clientIds = clientIdsFromServerIds(
            clientRequirements.serverIds, freshSpanId)

          csInfo <- Clock.getInstant(clock).map(clientSendInfo(name,endpoint, clientIds))
          _ <- sendToCollector(csInfo)(collectorInterpreter)

          requestWithIds = addZipkinHeaders(request, clientIds)
          response <- open.run(requestWithIds)

          crInfo <- Clock.getInstant(clock).map(clientReceiveInfo(name,endpoint, clientIds))
          _ <- sendToCollector(crInfo)(collectorInterpreter)
        } yield response
      }
    }

    Kleisli.kleisli[Scalaz.Id,ClientRequirements,Client] { clientReqs =>
      client.copy(
        open = updateOpen(client.open, clientReqs),
        shutdown = Task.now(())
      )
    }
  }

  def addZipkinHeaders(
    request: Request, clientZipkinIds: ClientIds
  ): Request = request.putHeaders(
    clientZipkinIds.parentId,
    clientZipkinIds.traceId,
    clientZipkinIds.spanId
  )

  def clientIdsFromServerIds(
    serverIds: ServerIds, freshSpanId: Long
  ): ClientIds = ClientIds(
    spanId = `X-B3-SpanId`(freshSpanId),
    traceId = serverIds.traceId,
    parentId = `X-B3-ParentSpanId`(serverIds.spanId.id)
  )

  def clientSendInfo(name: String, host: Endpoint, clientIds: ClientIds)(instant: Instant) =
    buildZipkinInfo(
      name,
      instant,host,clientIds.traceId, clientIds.spanId, Option(clientIds.parentId)
    )(AnnotationType.ClientSend)

  def clientReceiveInfo(name: String, host: Endpoint, clientIds: ClientIds)(instant: Instant) =
    buildZipkinInfo(
      name,
      instant,host,clientIds.traceId, clientIds.spanId, Option(clientIds.parentId)
    )(AnnotationType.ClientReceive)


}

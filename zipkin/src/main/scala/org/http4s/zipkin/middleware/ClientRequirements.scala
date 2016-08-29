package org.http4s.zipkin.middleware

import org.http4s.zipkin.models.ServerIds

final case class ClientRequirements(
  serverIds: ServerIds,
  serviceName: ServiceName)



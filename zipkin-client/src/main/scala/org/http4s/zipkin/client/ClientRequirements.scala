package org.http4s.zipkin.client

import org.http4s.zipkin.core.ServerIds

final case class ClientRequirements(
  serverIds: ServerIds,
  serviceName: ServiceName)



package org.http4s.zipkin.middleware.client

import org.http4s.zipkin.core.ServerIds
import org.http4s.zipkin.middleware._

final case class ClientRequirements(
  serverIds: ServerIds,
  serviceName: ServiceName)



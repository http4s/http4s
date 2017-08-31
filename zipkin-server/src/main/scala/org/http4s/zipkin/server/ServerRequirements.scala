package org.http4s.zipkin.server

import org.http4s.zipkin.core.ServerIds

final case class ServerRequirements(
  serverIds: ServerIds)

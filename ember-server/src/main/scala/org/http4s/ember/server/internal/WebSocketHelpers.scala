package org.http4s.ember.server.internal

import org.http4s.{Request, Response}
import org.http4s.websocket.WebSocketContext

object WebSocketHelpers {
  
  def upgrade[F[_]](req: Request[F], resp: Response[F], ctx: WebSocketContext[F]): F[Unit] = {
    // TODO: Craft a 101 Switching Protocols response here
    // Validate that the request needs to be upgrade?
    
    ???
  }

}

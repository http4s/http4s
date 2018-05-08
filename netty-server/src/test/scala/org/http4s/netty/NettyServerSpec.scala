package org.http4s.netty

import cats.effect.IO
import org.http4s.server.{ServerBuilder, ServerSpec}

class NettyServerSpec extends ServerSpec {
  def builder: ServerBuilder[IO] = NettyBuilder[IO]
}

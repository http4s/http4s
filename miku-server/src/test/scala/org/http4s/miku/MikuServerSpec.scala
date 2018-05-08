package org.http4s.miku

import cats.effect.IO
import org.http4s.server.{ServerBuilder, ServerSpec}

class MikuServerSpec extends ServerSpec {
  def builder: ServerBuilder[IO] = MikuBuilder[IO]
}

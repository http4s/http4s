package org.http4s.server.play

import cats.effect.IO
import org.http4s.server.ServerSpec

class PlayServerSpec extends ServerSpec {
  def builder: PlayTestServerBuilder[IO] = PlayTestServerBuilder[IO]
}

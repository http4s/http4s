package org.http4s.play

import cats.effect.Sync
import org.http4s.EntityDecoder
import play.api.libs.json.Reads

/**
  * Derive [[EntityDecoder]] if implicit [[Reads]] is in the scope without need to explicitly call `jsonOf`
  */
trait PlayEntityDecoder {
  implicit def playEntityDecoder[F[_]: Sync, A: Reads]: EntityDecoder[F, A] = jsonOf[F, A]
}

object PlayEntityDecoder extends PlayEntityDecoder

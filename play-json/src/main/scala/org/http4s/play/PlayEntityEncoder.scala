package org.http4s.play

import cats.Applicative
import play.api.libs.json.Writes
import org.http4s.EntityEncoder

/**
  * Derive [[EntityEncoder]] if implicit [[Writes]] is in the scope without need to explicitly call `jsonEncoderOf`
  */
trait PlayEntityEncoder {
  implicit def playEntityEncoder[F[_]: Applicative, A: Writes]: EntityEncoder[F, A] =
    jsonEncoderOf(EntityEncoder.stringEncoder[F], implicitly, implicitly)
}

object PlayEntityEncoder extends PlayEntityEncoder

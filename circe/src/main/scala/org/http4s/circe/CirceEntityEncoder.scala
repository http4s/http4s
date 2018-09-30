package org.http4s.circe

import cats.Applicative
import io.circe.Encoder
import org.http4s.EntityEncoder

/**
  * Derive [[EntityEncoder]] if implicit [[Encoder]] is in the scope without need to explicitly call `jsonEncoderOf`
  */
trait CirceEntityEncoder {
  implicit def circeEntityEncoder[F[_]: Applicative, A: Encoder]: EntityEncoder[F, A] =
    jsonEncoderOf[F, A]
}

object CirceEntityEncoder extends CirceEntityEncoder

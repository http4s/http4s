package org.http4s.circe

import cats.effect.Sync
import io.circe.Decoder
import org.http4s.EntityDecoder

/**
  * Derive [[EntityDecoder]] if implicit [[Decoder]] is in the scope without need to explicitly call `jsonOf`
  */
trait CirceEntityDecoder {
  implicit def circeEntityDecoder[F[_]: Sync, A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]
}

object CirceEntityDecoder extends CirceEntityDecoder

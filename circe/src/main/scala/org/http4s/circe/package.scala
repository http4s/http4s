package org.http4s

import cats.effect.Effect
import io.circe.{Json, Printer}

package object circe extends CirceInstances {
  override val defaultPrinter: Printer =
    Printer.noSpaces

  override def jsonDecoder[F[_]: Effect]: EntityDecoder[F, Json] =
    CirceInstances.defaultJsonDecoder
}

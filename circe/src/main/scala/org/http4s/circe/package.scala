package org.http4s

import fs2.util.Catchable
import io.circe.{Json, Printer}

package object circe extends CirceInstances {
  override val defaultPrinter: Printer =
    Printer.noSpaces

  override def jsonDecoder[F[_]: Catchable]: EntityDecoder[F, Json] =
    CirceInstances.defaultJsonDecoder
}

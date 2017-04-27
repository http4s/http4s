package org.http4s

import io.circe.{Json, Printer}

package object circe extends CirceInstances {
  override val defaultPrinter: Printer =
    Printer.noSpaces

  override val jsonDecoder: EntityDecoder[Json] =
    CirceInstances.defaultJsonDecoder
}

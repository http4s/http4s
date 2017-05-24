package org.http4s

import cats.MonadError
import io.circe.{Json, Printer}

package object circe extends CirceInstances {
  override val defaultPrinter: Printer =
    Printer.noSpaces

  override def jsonDecoder[F[_]: MonadError[?[_], Throwable]]: EntityDecoder[F, Json] =
    CirceInstances.defaultJsonDecoder
}

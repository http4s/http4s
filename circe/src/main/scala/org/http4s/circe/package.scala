package org.http4s

import io.circe.Printer

package object circe extends CirceInstances {
  protected def defaultPrinter: Printer =
    Printer.noSpaces
}

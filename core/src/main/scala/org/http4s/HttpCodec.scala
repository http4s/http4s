package org.http4s

import cats.implicits._
import org.http4s.util.Renderer

trait HttpCodec[A] extends Renderer[A] {
  def parse(s: String): ParseResult[A]

  /** Warning: partial method. Intended for tests and macros that have
    * assured that `s` can be parsed to a valid `A`.
    */
  final def parseOrThrow(s: String): A =
    parse(s).valueOr(throw _)
}

object HttpCodec {
  def apply[A](implicit A: HttpCodec[A]): HttpCodec[A] = A
}

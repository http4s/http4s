package org.http4s.codec

import cats.~>
import org.http4s.util.Renderer

object rendererEncoder extends (Http1Codec.Op ~> Renderer) {
  def apply[A](op: Http1Codec.Op[A]): Renderer[A] =
    op match {
      case Http1Codec.StringLiteral(s) => Renderer.stringLiteralRenderer(s)
      case Http1Codec.CharLiteral(c) => Renderer.charLiteralRenderer(c)
      case Http1Codec.Digit => Renderer.charRenderer
      case Http1Codec.ListOf(codec) =>
        Renderer.listRenderer(codec.foldMap(rendererEncoder))
    }
}

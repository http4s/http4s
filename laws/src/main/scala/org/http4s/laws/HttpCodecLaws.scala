package org.http4s
package laws

import cats.laws._
import org.http4s.util.Renderer

trait HttpCodecLaws[A] {
  implicit def C: HttpCodec[A]

  def httpCodecRoundTrip(a: A): IsEq[ParseResult[A]] =
    C.parse(Renderer.renderString(a)) <-> Right(a)
}

object HttpCodecLaws {
  def apply[A](implicit httpCodecA: HttpCodec[A]): HttpCodecLaws[A] = new HttpCodecLaws[A] {
    val C = httpCodecA
  }
}

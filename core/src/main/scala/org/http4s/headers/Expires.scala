package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}

/**
  * Constructs an `Expires` header.
  *
  * The HTTP RFCs indicate that Expires should be in the range of now to 1 year in the future.
  * However, it is a usual practice to set it to the past of far in the future
  * Thus any instant is in practice allowed
  *
  * @param expirationDate the date of expiration
  */
final case class Expires(expirationDate: java.time.Instant)
object Expires {
  implicit val expiresHeader: Header[Expires] =
      new Header[Expires] {
        val name = CaseInsensitiveString("Expires")
        def parse(s: String) = HttpHeaderParser.EXPIRES(s).toOption
        def toRawHeaders(a: Expires) = List(RawHeader(
          name, Renderer.renderString(a.expirationDate)))
        val isSingleton = true
      }
}
package org.http4s.parser

import java.nio.charset.StandardCharsets
import org.http4s.{Header, Uri}
import org.http4s.internal.parboiled2.Rule1

abstract class UriHeaderParser[A <: Header](value: String)
    extends Http4sHeaderParser[A](value)
    with Rfc3986Parser {
  override def charset: java.nio.charset.Charset = StandardCharsets.ISO_8859_1

  // Implementors should build a Header out of the uri
  def fromUri(uri: Uri): A

  def entry: Rule1[A] = rule {
    // https://tools.ietf.org/html/rfc3986#section-4.1
    (AbsoluteUri | RelativeRef) ~> { (a: Uri) =>
      fromUri(a)
    }
  }
}

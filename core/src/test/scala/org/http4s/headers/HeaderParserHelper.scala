package org.http4s
package headers


abstract class HeaderParserSpec[H <: HeaderKey](key: H) extends Http4sSpec {

  def hparse(value: String): Option[H#HeaderT] = key.matchHeader(Header.Raw(key.name, value))

  // Also checks to make sure whitespace doesn't effect the outcome
  protected def parse(value: String): H#HeaderT = {
    val a = hparse(value).getOrElse(sys.error(s"Couldn't parse: '$value'."))
    val b = hparse(value.replace(" ", "")).getOrElse(sys.error(s"Couldn't parse: $value"))
    assert(a == b, "Whitespace resulted in different headers")
    a
  }

}

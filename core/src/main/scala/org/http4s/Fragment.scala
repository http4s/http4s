package org.http4s

import cats.{Order, Show}
import org.http4s.internal.parboiled2.{Parser => PbParser}
import org.http4s.parser.{Http4sParser, Rfc3986Predicates, Rfc3986Rules}
import org.http4s.util.{UrlCodingUtils, Writer}
import scala.math.Ordered

/** The fragment identifier component of a [[org.http4s.Uri]] allows indirect
  * identification of a secondary resource by reference to a primary resource
  * and additional identifying information.
  *
  * @see https://www.ietf.org/rfc/rfc3986.txt, Section 3.5
  */
final class Fragment private (val value: String) extends AnyVal with Ordered[Fragment] {
  override def toString(): String = s"Fragment($value)"

  override def compare(other: Fragment): Int =
    value.compare(other.value)
}

object Fragment {
  val empty: Fragment = new Fragment("")

  /** Construct a fragment from its decoded value */
  def apply(s: String): Fragment = new Fragment(s)

  /** Parse a fragment from its encoded value */
  def parse(s: String): ParseResult[Fragment] =
    new Http4sParser[Fragment](s, "fragment Invalid") with Parser {
      def main = fragment
    }.parse

  private[http4s] trait Parser extends Rfc3986Rules { self: PbParser =>
    import Rfc3986Predicates._
    def fragment = rule {
      run(sb.setLength(0)) ~
        (oneOrMore(fragmentCharNoPct ~ appendSB() | `pct-encoded`) ~ push(
          Fragment(UrlCodingUtils.urlDecode(sb.toString))) |
          push(Fragment.empty))
    }
  }

  implicit val http4sInstancesForFragment
    : Show[Fragment] with HttpCodec[Fragment] with Order[Fragment] =
    new Show[Fragment] with HttpCodec[Fragment] with Order[Fragment] {
      def show(s: Fragment): String = s.toString

      def parse(s: String): ParseResult[Fragment] =
        Fragment.parse(s)

      def render(writer: Writer, fragment: Fragment): writer.type =
        writer << UrlCodingUtils.urlEncode(fragment.value)

      def compare(x: Fragment, y: Fragment) =
        x.compareTo(y)
    }
}

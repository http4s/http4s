package org.http4s

import cats.{Order, Show}
import org.http4s.internal.parboiled2.{Parser => PbParser}
import org.http4s.parser.{Http4sParser, Rfc3986Predicates}
import org.http4s.util.{Writer, hashLower}

/** Each [[org.http4s.Uri]] begins with a scheme name that refers to a
  * specification for assigning identifiers within that scheme.
  *
  * @see https://www.ietf.org/rfc/rfc3986.txt, Section 3.1
  */
final class Scheme private (val value: String) extends Comparable[Scheme] {
  override def equals(o: Any) = o match {
    case that: Scheme => this.value.equalsIgnoreCase(that.value)
    case _ => false
  }

  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0) {
      hash = hashLower(value)
    }
    hash
  }

  override def toString = s"Scheme($value)"

  override def compareTo(other: Scheme): Int =
    value.compareToIgnoreCase(other.value)
}

object Scheme {
  val http: Scheme = new Scheme("http")
  val https: Scheme = new Scheme("https")

  def parse(s: String): ParseResult[Scheme] =
    new Http4sParser[Scheme](s, "Invalid scheme") with Parser {
      def main = scheme
    }.parse

  private[http4s] trait Parser { self: PbParser =>
    import Rfc3986Predicates._
    def scheme = rule {
      "https" ~ push(https) |
        "http" ~ push(http) |
        capture(ALPHA ~ zeroOrMore(schemeChar)) ~> (new Scheme(_))
    }
  }

  implicit val http4sInstancesForScheme: Show[Scheme] with HttpCodec[Scheme] with Order[Scheme] =
    new Show[Scheme] with HttpCodec[Scheme] with Order[Scheme] {
      def show(s: Scheme): String = s.toString

      def parse(s: String): ParseResult[Scheme] =
        Scheme.parse(s)

      def render(writer: Writer, scheme: Scheme): writer.type =
        writer << scheme.value

      def compare(x: Scheme, y: Scheme) =
        x.compareTo(y)
    }
}

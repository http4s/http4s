package org.http4s

import cats.data.{Writer => _}
import cats.implicits._
import cats.{Order, Show}
import org.http4s.internal.parboiled2._
import org.http4s.parser._
import org.http4s.util._

/**
  * An HTTP version, as seen on the start line of an HTTP request or response.
  *
  * @see [http://tools.ietf.org/html/rfc7230#section-2.6 RFC 7320, Section 2.6
  */
final case class HttpVersion private[HttpVersion] (major: Int, minor: Int)
    extends Renderable
    with Ordered[HttpVersion] {
  override def render(writer: Writer): writer.type = writer << "HTTP/" << major << '.' << minor
  override def compare(that: HttpVersion): Int =
    (this.major, this.minor).compare((that.major, that.minor))
}

object HttpVersion {
  val `HTTP/1.0` = new HttpVersion(1, 0)
  val `HTTP/1.1` = new HttpVersion(1, 1)
  val `HTTP/2.0` = new HttpVersion(2, 0)

  def fromString(s: String): ParseResult[HttpVersion] = s match {
    case "HTTP/1.1" => Either.right(`HTTP/1.1`)
    case "HTTP/1.0" => Either.right(`HTTP/1.0`)
    case _ =>
      new Parser(s).HttpVersion.run()(Parser.DeliveryScheme.Either).leftMap { _ =>
        ParseFailure("Invalid HTTP version", s"$s was not found to be a valid HTTP version")
      }
  }

  private class Parser(val input: ParserInput)
      extends org.http4s.internal.parboiled2.Parser
      with Rfc2616BasicRules {
    def HttpVersion: Rule1[org.http4s.HttpVersion] = rule {
      "HTTP/" ~ capture(Digit) ~ "." ~ capture(Digit) ~> { (major: String, minor: String) =>
        new HttpVersion(major.toInt, minor.toInt)
      }
    }
  }

  def fromVersion(major: Int, minor: Int): ParseResult[HttpVersion] =
    if (major < 0) ParseResult.fail("Invalid HTTP version", s"major must be > 0: $major")
    else if (major > 9) ParseResult.fail("Invalid HTTP version", s"major must be <= 9: $major")
    else if (minor < 0) ParseResult.fail("Invalid HTTP version", s"major must be > 0: $minor")
    else if (minor > 9) ParseResult.fail("Invalid HTTP version", s"major must be <= 9: $minor")
    else ParseResult.success(new HttpVersion(major, minor))

  implicit val http4sHttpOrderForVersion: Order[HttpVersion] =
    Order.fromComparable
  implicit val http4sHttpShowForVersion: Show[HttpVersion] =
    Show.fromToString
}

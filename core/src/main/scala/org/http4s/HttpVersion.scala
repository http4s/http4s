package org.http4s

import scala.language.experimental.macros
import scala.math.Ordered.orderingToOrdered
import scala.reflect.macros.Context
import scala.util.control.NoStackTrace
import scalaz._

import org.http4s.parser.Rfc2616BasicRules
import org.http4s.util.{Renderable, Writer}
import org.parboiled2._

/**
 * An HTTP version, as seen on the start line of an HTTP request or response.
 *
 * @see [http://tools.ietf.org/html/rfc7230#section-2.6 RFC 7320, Section 2.6
 */
case class HttpVersion private[HttpVersion] (major: Int, minor: Int) extends Renderable with Ordered[HttpVersion] {
  override def render[W <: Writer](writer: W): writer.type = writer ~ "HTTP/" ~ major ~ "." ~ minor
  override def compare(that: HttpVersion): Int = ((this.major, this.minor)) compare ((that.major, that.minor))
}

object HttpVersion extends HttpVersionInstances {
  val `HTTP/1.0` = new HttpVersion(1, 0)
  val `HTTP/1.1` = new HttpVersion(1, 1)

  def fromString(s: String): Validation[InvalidHttpVersion, HttpVersion] = s match {
    case "HTTP/1.1" => Success(`HTTP/1.1`)
    case "HTTP/1.0" => Success(`HTTP/1.0`)
    case other => new Parser(s).HttpVersion.run()(parser.validationScheme).leftMap(_ => InvalidHttpVersion(s))
  }

  private class Parser(val input: ParserInput) extends org.parboiled2.Parser with Rfc2616BasicRules {
    def HttpVersion: Rule1[org.http4s.HttpVersion] = rule {
      "HTTP/" ~ capture(Digit) ~ "." ~ capture(Digit) ~> { (major: String, minor: String) => new HttpVersion(major.toInt, minor.toInt) }
    }
  }

  def fromVersion(major: Int, minor: Int): Validation[InvalidHttpVersion, HttpVersion] = {
    if (major < 0 || major > 9) Failure(InvalidHttpVersion(s"${major}.${minor}"))
    else if (minor < 0 || minor > 9) Failure(InvalidHttpVersion(s"${major}.${minor}"))
    else Success(new HttpVersion(major, minor))
  }
}

trait HttpVersionInstances {
  implicit val HttpVersionShow = Show.showFromToString[HttpVersion]
  implicit val HttpVersionOrder = Order.fromScalaOrdering[HttpVersion]
}

case class InvalidHttpVersion(string: String) extends Http4sException(s"Invalid HTTP version: ${string}") with NoStackTrace


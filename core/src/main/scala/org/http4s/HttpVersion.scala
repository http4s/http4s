/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.data.{Writer => _}
import cats.syntax.all._
import cats.kernel.BoundedEnumerable
import cats.{Hash, Order, Show}
import org.http4s.internal.parboiled2._
import org.http4s.parser._
import org.http4s.util._

/** An HTTP version, as seen on the start line of an HTTP request or response.
  *
  * @see [[http://tools.ietf.org/html/rfc7230#section-2.6 RFC 7320, Section 2.6]]
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
  @deprecated("Use catsInstancesForHttp4sHttpVersion", "0.21.12")
  val http4sHttpOrderForVersion: Order[HttpVersion] =
    Order.fromComparable
  @deprecated("Use catsInstancesForHttp4sHttpVersion", "0.21.12")
  val http4sHttpShowForVersion: Show[HttpVersion] =
    Show.fromToString
  private[this] val right_1_0 = Right(`HTTP/1.0`)
  private[this] val right_1_1 = Right(`HTTP/1.1`)

  def fromString(s: String): ParseResult[HttpVersion] =
    s match {
      case "HTTP/1.1" => right_1_1
      case "HTTP/1.0" => right_1_0
      case _ =>
        new Parser(s).HttpVersion.run()(Parser.DeliveryScheme.Either).leftMap { _ =>
          ParseFailure("Invalid HTTP version", s"$s was not found to be a valid HTTP version")
        }
    }

  def fromVersion(major: Int, minor: Int): ParseResult[HttpVersion] =
    if (major < 0) ParseResult.fail("Invalid HTTP version", s"major must be > 0: $major")
    else if (major > 9) ParseResult.fail("Invalid HTTP version", s"major must be <= 9: $major")
    else if (minor < 0) ParseResult.fail("Invalid HTTP version", s"major must be > 0: $minor")
    else if (minor > 9) ParseResult.fail("Invalid HTTP version", s"major must be <= 9: $minor")
    else ParseResult.success(new HttpVersion(major, minor))

  private class Parser(val input: ParserInput)
      extends org.http4s.internal.parboiled2.Parser
      with Rfc2616BasicRules {
    def HttpVersion: Rule1[org.http4s.HttpVersion] =
      rule {
        "HTTP/" ~ capture(Digit) ~ "." ~ capture(Digit) ~> { (major: String, minor: String) =>
          new HttpVersion(major.toInt, minor.toInt)
        }
      }
  }

  implicit val catsInstancesForHttp4sHttpVersion: Order[HttpVersion]
    with Show[HttpVersion]
    with Hash[HttpVersion]
    with BoundedEnumerable[HttpVersion] = new Order[HttpVersion]
    with Show[HttpVersion]
    with Hash[HttpVersion]
    with BoundedEnumerable[HttpVersion] { self =>
    // Hash
    override def hash(x: HttpVersion): Int = x.hashCode

    // Show
    override def show(t: HttpVersion): String = t.renderString

    // Order
    override def compare(x: HttpVersion, y: HttpVersion): Int = x.compare(y)

    // BoundedEnumerable
    override def partialNext(a: HttpVersion): Option[HttpVersion] = a match {
      case HttpVersion(9, 9) => None
      case HttpVersion(major, minor) if fromVersion(major, minor).isLeft => None
      case HttpVersion(major, 9) => Some(HttpVersion(major + 1, 0))
      case HttpVersion(major, minor) => Some(HttpVersion(major, minor + 1))
      case _ => None
    }
    override def partialPrevious(a: HttpVersion): Option[HttpVersion] = a match {
      case HttpVersion(0, 0) => None
      case HttpVersion(major, minor) if fromVersion(major, minor).isLeft => None
      case HttpVersion(major, 0) => Some(HttpVersion(major - 1, 9))
      case HttpVersion(major, minor) => Some(HttpVersion(major, minor - 1))
      case _ => None
    }
    override def order: Order[HttpVersion] = self
    override def minBound: HttpVersion = HttpVersion(0, 0)
    override def maxBound: HttpVersion = HttpVersion(9, 9)
  }
}

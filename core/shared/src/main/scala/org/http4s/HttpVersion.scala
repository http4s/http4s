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

import cats.Hash
import cats.Order
import cats.Show
import cats.kernel.BoundedEnumerable
import cats.parse.Rfc5234.digit
import cats.parse.{Parser => P}
import cats.syntax.all._
import org.http4s.util._

/** HTTP's version number consists of two decimal digits separated by
  * a "." (period or decimal point). The first digit ("major version")
  * indicates the messaging syntax, whereas the second digit ("minor
  * version") indicates the highest minor version within that major
  * version to which the sender is conformant (able to understand for
  * future communication).
  *
  * @param major The major version, `0` to `9` inclusive
  *
  * @param minor The minor version, `0` to `9` inclusive
  *
  * @see
  * [[https://httpwg.org/http-core/draft-ietf-httpbis-semantics-latest.html#protocol.version
  * HTTP Semantics, Protocol Versioning]]
  */
// scalafix:off Http4sGeneralLinters.nonValidatingCopyConstructor; bincompat until 1.0
final case class HttpVersion private[HttpVersion] (major: Int, minor: Int)
    extends Renderable
    with Ordered[HttpVersion] {
  // scalafix:on

  /** Renders as an HTTP/1.1 string
    *
    * {{{
    * >>> HttpVersion.`HTTP/1.1`.renderString
    * HTTP/1.1
    * }}}
    */
  override def render(writer: Writer): writer.type = writer << "HTTP/" << major << '.' << minor

  /** Orders by major version ascending, then minor version ascending.
    *
    * {{{
    * >>> List(HttpVersion.`HTTP/1.0`, HttpVersion.`HTTP/1.1`, HttpVersion.`HTTP/0.9`).sorted
    * List(HTTP/0.9, HTTP/1.0, HTTP/1.1)
    * }}}
    */
  override def compare(that: HttpVersion): Int =
    (this.major, this.minor).compare((that.major, that.minor))

  @deprecated("Does not range check parameters. Will be removed from public API in 1.0.", "0.22.6")
  def copy(major: Int = major, minor: Int = minor): HttpVersion =
    new HttpVersion(major, minor)
}

object HttpVersion {

  def apply(major: Int, minor: Int): HttpVersion = new HttpVersion(major, minor)

  /** HTTP/0.9 was first formalized in the HTTP/1.0 spec. `HTTP/0.9`
    * does not literally appear in the HTTP/0.9 protocol.
    *
    * @see [[https://www.w3.org/Protocols/HTTP/AsImplemented.html The
    * original HTTP as defined in 1991]]
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc1945 RFC1945,
    * Hypertext Transfer Protocol -- HTTP/1.0]]
    */
  val `HTTP/0.9`: HttpVersion = new HttpVersion(0, 9)

  /** HTTP/1.0 is the first major version of HTTP.
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc1945 RFC1945,
    * Hypertext Transfer Protocol -- HTTP/1.0]]
    */
  val `HTTP/1.0`: HttpVersion = new HttpVersion(1, 0)

  /** HTTP/1.1 revises HTTP/1.0, and is currently defined by six RFCs.
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc208 Hypertext
    * Transfer Protocol -- HTTP/1.1]] (obsolete)
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc2616 Hypertext
    * Transfer Protocol -- HTTP/1.1]] (obsolete)
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc7230 Hypertext
    * Transfer Protocol (HTTP/1.1): Message Syntax and Routing]]
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc7231 Hypertext
    * Transfer Protocol (HTTP/1.1): Semantics and Content]]
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc7232 Hypertext
    * Transfer Protocol (HTTP/1.1): Conditional Requests]]
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc7233 Hypertext
    * Transfer Protocol (HTTP/1.1): Range Requests]]
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc7234 Hypertext
    * Transfer Protocol (HTTP/1.1): Caching]]
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc7235 Hypertext
    * Transfer Protocol (HTTP/1.1): Authentication]]
    *
    * @see
    * [[https://datatracker.ietf.org/doc/draft-ietf-httpbis-messaging/
    * HTTP/1.1]] (draft)
    */
  val `HTTP/1.1`: HttpVersion = new HttpVersion(1, 1)

  /** HTTP/2 is the second major version of HTTP.  It defines no minor
    * versions, so minor version `0` is implied.
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc7540 RFC7540,
    * Hypertext Transfer Protocol Version 2 (HTTP/2)]]
    */
  val `HTTP/2`: HttpVersion = new HttpVersion(2, 0)

  @deprecated("Renamed to `HTTP/2`. HTTP/2 does not define minor versions.", "0.22.6")
  def `HTTP/2.0`: HttpVersion = `HTTP/2`

  /** HTTP/3 is the third major version of HTTP.  It defines no minor
    * versions, so minor version `0` is implied.
    *
    * @see [[https://quicwg.org/base-drafts/draft-ietf-quic-http.html
    * Hypertext Transfer Protocol Version 3 (HTTP/3)]] (draft)
    */
  val `HTTP/3`: HttpVersion = new HttpVersion(3, 0)

  /** The set of HTTP versions that have a specification */
  private[http4s] val specified: Set[HttpVersion] = Set(
    `HTTP/0.9`,
    `HTTP/1.0`,
    `HTTP/1.1`,
    `HTTP/2`,
    `HTTP/3`,
  )

  private[this] val right_1_0 = Right(`HTTP/1.0`)
  private[this] val right_1_1 = Right(`HTTP/1.1`)

  /** Returns an HTTP version from its HTTP/1 string representation.
    *
    * {{{
    * >>> HttpVersion.fromString("HTTP/1.1")
    * Right(HTTP/1.1)
    * }}}
    */
  def fromString(s: String): ParseResult[HttpVersion] =
    s match {
      case "HTTP/1.1" => right_1_1
      case "HTTP/1.0" => right_1_0
      case _ =>
        ParseResult.fromParser(parser, "HTTP version")(s)
    }

  private val parser: P[HttpVersion] = {
    // HTTP-name = %x48.54.54.50 ; HTTP
    // HTTP-version = HTTP-name "/" DIGIT "." DIGIT
    val httpVersion = P.string("HTTP/") *> digit ~ (P.char('.') *> digit)

    httpVersion.map { case (major, minor) =>
      new HttpVersion(major - '0', minor - '0')
    }
  }

  /** Returns an HTTP version from a major and minor version.
    *
    * {{{
    * >>> HttpVersion.fromVersion(1, 1)
    * Right(HTTP/1.1)
    *
    * >>> HttpVersion.fromVersion(1, 10)
    * Left(org.http4s.ParseFailure: Invalid HTTP version: major must be <= 9: 10)
    * }}}
    *
    * @param major The major version, `0` to `9` inclusive
    * @param minor The minor version, `0` to `9` inclusive
    */
  def fromVersion(major: Int, minor: Int): ParseResult[HttpVersion] =
    if (major < 0) ParseResult.fail("Invalid HTTP version", s"major must be > 0: $major")
    else if (major > 9) ParseResult.fail("Invalid HTTP version", s"major must be <= 9: $major")
    else if (minor < 0) ParseResult.fail("Invalid HTTP version", s"major must be > 0: $minor")
    else if (minor > 9) ParseResult.fail("Invalid HTTP version", s"major must be <= 9: $minor")
    else ParseResult.success(new HttpVersion(major, minor))

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
    }
    override def partialPrevious(a: HttpVersion): Option[HttpVersion] = a match {
      case HttpVersion(0, 0) => None
      case HttpVersion(major, minor) if fromVersion(major, minor).isLeft => None
      case HttpVersion(major, 0) => Some(HttpVersion(major - 1, 9))
      case HttpVersion(major, minor) => Some(HttpVersion(major, minor - 1))
    }
    override def order: Order[HttpVersion] = self
    override def minBound: HttpVersion = HttpVersion(0, 0)
    override def maxBound: HttpVersion = HttpVersion(9, 9)
  }
}

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

import cats.{Hash, Order, Show}
import cats.parse.Parser
import cats.syntax.all._
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.{Renderable, Writer}
import scala.util.hashing.MurmurHash3

/** An HTTP method.
  *
  * @param name The name of the method
  *
  * @param isSafe Request methods are considered "safe" if their defined
  * semantics are essentially read-only; i.e., the client does not request, and
  * does not expect, any state change on the origin server as a result of
  * applying a safe method to a target resource.
  *
  * @param isIdempotent A request method is considered "idempotent" if the
  * intended effect on the server of multiple identical requests with that
  * method is the same as the effect for a single such request.
  *
  * @see [[http://tools.ietf.org/html/rfc7231#section-4 RFC 7321, Section 4, Request Methods]]
  * @see [[http://www.iana.org/assignments/http-methods/http-methods.xhtml IANA HTTP Method Registry]]
  */
final class Method private (val name: String, val isSafe: Boolean, val isIdempotent: Boolean)
    extends Renderable
    with Serializable {
  override def equals(that: Any): Boolean =
    that match {
      case that: Method => this.name == that.name
      case _ => false
    }

  override def hashCode(): Int = MurmurHash3.stringHash(name, Method.HashSeed)

  override def toString(): String = name

  final override def render(writer: Writer): writer.type = writer << name
}

object Method {
  private final val HashSeed = 0x892abd01

  def fromString(s: String): ParseResult[Method] =
    allByKey.getOrElse(s, ParseResult.fromParser(parser, "Invalid method")(s))

  private[http4s] val parser: Parser[Method] =
    Rfc7230.token.map(apply)

  private def apply(name: String) =
    new Method(name, isSafe = false, isIdempotent = false)
  private def idempotent(name: String) =
    new Method(name, isSafe = false, isIdempotent = true)
  private def safe(name: String) =
    new Method(name, isSafe = true, isIdempotent = true)

  val ACL: Method = idempotent("ACL")
  val `BASELINE-CONTROL`: Method = idempotent("BASELINE-CONTROL")
  val BIND: Method = idempotent("BIND")
  val CHECKIN: Method = idempotent("CHECKIN")
  val CHECKOUT: Method = idempotent("CHECKOUT")
  val CONNECT: Method = apply("CONNECT")
  val COPY: Method = idempotent("COPY")
  val DELETE: Method = idempotent("DELETE")
  val GET: Method = safe("GET")
  val HEAD: Method = safe("HEAD")
  val LABEL: Method = idempotent("LABEL")
  val LINK: Method = idempotent("LINK")
  val LOCK: Method = apply("LOCK")
  val MERGE: Method = idempotent("MERGE")
  val MKACTIVITY: Method = idempotent("MKACTIVITY")
  val MKCALENDAR: Method = idempotent("MKCALENDAR")
  val MKCOL: Method = idempotent("MKCOL")
  val MKREDIRECTREF: Method = idempotent("MKREDIRECTREF")
  val MKWORKSPACE: Method = idempotent("MKWORKSPACE")
  val MOVE: Method = idempotent("MOVE")
  val OPTIONS: Method = safe("OPTIONS")
  val ORDERPATCH: Method = idempotent("ORDERPATCH")
  val PATCH: Method = apply("PATCH")
  val POST: Method = apply("POST")
  val PRI: Method = safe("PRI")
  val PROPFIND: Method = safe("PROPFIND")
  val PROPPATCH: Method = idempotent("PROPPATCH")
  val PUT: Method = idempotent("PUT")
  val REBIND: Method = idempotent("REBIND")
  val REPORT: Method = safe("REPORT")
  val SEARCH: Method = safe("SEARCH")
  val TRACE: Method = safe("TRACE")
  val UNBIND: Method = idempotent("UNBIND")
  val UNCHECKOUT: Method = idempotent("UNCHECKOUT")
  val UNLINK: Method = idempotent("UNLINK")
  val UNLOCK: Method = idempotent("UNLOCK")
  val UPDATE: Method = idempotent("UPDATE")
  val UPDATEREDIRECTREF: Method = idempotent("UPDATEREDIRECTREF")
  val `VERSION-CONTROL`: Method = idempotent("VERSION-CONTROL")

  val all = List(
    ACL,
    `BASELINE-CONTROL`,
    BIND,
    CHECKIN,
    CHECKOUT,
    CONNECT,
    COPY,
    DELETE,
    GET,
    HEAD,
    LABEL,
    LINK,
    LOCK,
    MERGE,
    MKACTIVITY,
    MKCALENDAR,
    MKCOL,
    MKREDIRECTREF,
    MKWORKSPACE,
    MOVE,
    OPTIONS,
    ORDERPATCH,
    PATCH,
    POST,
    PRI,
    PROPFIND,
    PROPPATCH,
    PUT,
    REBIND,
    REPORT,
    SEARCH,
    TRACE,
    UNBIND,
    UNCHECKOUT,
    UNLINK,
    UNLOCK,
    UPDATEREDIRECTREF,
    `VERSION-CONTROL`
  )

  private val allByKey: Map[String, Right[Nothing, Method]] = all.map(m => (m.name, Right(m))).toMap

  implicit val catsInstancesForHttp4sMethod: Show[Method] with Hash[Method] with Order[Method] =
    new Show[Method] with Hash[Method] with Order[Method] {
      override def show(t: Method): String =
        t.toString

      override def hash(x: Method): Int =
        x.hashCode

      override def compare(x: Method, y: Method): Int =
        x.name.compare(y.name)
    }
}

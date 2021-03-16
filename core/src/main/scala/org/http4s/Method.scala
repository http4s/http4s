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

  val Acl: Method = idempotent("ACL")
  val BaselineControl: Method = idempotent("BASELINE-CONTROL")
  val Bind: Method = idempotent("BIND")
  val Checkin: Method = idempotent("CHECKIN")
  val Checkout: Method = idempotent("CHECKOUT")
  val Connect: Method = apply("CONNECT")
  val Copy: Method = idempotent("COPY")
  val Delete: Method = idempotent("DELETE")
  val Get: Method = safe("GET")
  val Head: Method = safe("HEAD")
  val Label: Method = idempotent("LABEL")
  val Link: Method = idempotent("LINK")
  val Lock: Method = apply("LOCK")
  val Merge: Method = idempotent("MERGE")
  val MkActivity: Method = idempotent("MKACTIVITY")
  val MkCalendar: Method = idempotent("MKCALENDAR")
  val MkCol: Method = idempotent("MKCOL")
  val MkRedirectRef: Method = idempotent("MKREDIRECTREF")
  val MkWorkspace: Method = idempotent("MKWORKSPACE")
  val Move: Method = idempotent("MOVE")
  val Options: Method = safe("OPTIONS")
  val OrderPatch: Method = idempotent("ORDERPATCH")
  val Patch: Method = apply("PATCH")
  val Post: Method = apply("POST")
  val Pri: Method = safe("PRI")
  val PropFind: Method = safe("PROPFIND")
  val PropPatch: Method = idempotent("PROPPATCH")
  val Put: Method = idempotent("PUT")
  val Rebind: Method = idempotent("REBIND")
  val Report: Method = safe("REPORT")
  val Search: Method = safe("SEARCH")
  val Trace: Method = safe("TRACE")
  val Unbind: Method = idempotent("UNBIND")
  val Uncheckout: Method = idempotent("UNCHECKOUT")
  val Unlink: Method = idempotent("UNLINK")
  val Unlock: Method = idempotent("UNLOCK")
  val Update: Method = idempotent("UPDATE")
  val UpdateDirectRef: Method = idempotent("UPDATEREDIRECTREF")
  val VersionControl: Method = idempotent("VERSION-CONTROL")

  val all = List(
    Acl,
    BaselineControl,
    Bind,
    Checkin,
    Checkout,
    Connect,
    Copy,
    Delete,
    Get,
    Head,
    Label,
    Link,
    Lock,
    Merge,
    MkActivity,
    MkCalendar,
    MkCol,
    MkRedirectRef,
    MkWorkspace,
    Move,
    Options,
    OrderPatch,
    Patch,
    Post,
    Pri,
    PropFind,
    PropPatch,
    Put,
    Rebind,
    Report,
    Search,
    Trace,
    Unbind,
    Uncheckout,
    Unlink,
    Unlock,
    UpdateDirectRef,
    VersionControl
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

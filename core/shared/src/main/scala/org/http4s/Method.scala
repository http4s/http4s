package org.http4s

import cats.implicits._
import cats.{Eq, Show}
import org.http4s.Method.Semantics
import org.http4s.parser.Rfc2616BasicRules
import org.http4s.util.{Renderable, Writer}

/**
  * An HTTP method.
  *
  * @see [http://tools.ietf.org/html/rfc7231#section-4 RFC7321, Section 4]
  * @see [http://www.iana.org/assignments/http-methods/http-methods.xhtml IANA HTTP Method Registry]
  */
sealed abstract case class Method private (name: String) extends Renderable with Semantics {
  final override def render(writer: Writer): writer.type = writer << name
}

object Method {
  sealed trait Semantics {
    def isIdempotent: Boolean
    def isSafe: Boolean
  }

  object Semantics {
    trait Default extends Semantics {
      def isIdempotent: Boolean = false
      def isSafe: Boolean = false
    }
    trait Idempotent extends Semantics {
      def isIdempotent: Boolean = true
      def isSafe: Boolean = false
    }
    trait Safe extends Semantics {
      def isIdempotent: Boolean = true
      def isSafe: Boolean = true
    }
  }

  // Type tags for a method allowing a body or not
  sealed trait PermitsBody extends Method
  sealed trait NoBody extends Method

  def fromString(s: String): ParseResult[Method] =
    allByKey.getOrElse(
      s,
      Rfc2616BasicRules
        .token(s)
        .bimap(
          e => ParseFailure("Invalid method", e.details),
          new Method(_) with Semantics.Default
        ))

  import Semantics._

  type IdempotentMethod = Method with Idempotent
  type IdempotentMethodNoBody = IdempotentMethod with NoBody
  type IdempotentMethodWithBody = IdempotentMethod with PermitsBody
  type SafeMethod = Method with Safe
  type SafeMethodNoBody = SafeMethod with NoBody
  type SafeMethodWithBody = SafeMethod with PermitsBody
  type DefaultMethod = Method with Default
  type DefaultMethodNoBody = DefaultMethod with NoBody
  type DefaultMethodWithBody = DefaultMethod with PermitsBody

  // TODO: find out the rest of the body permissions. http://www.iana.org/assignments/http-methods/http-methods.xhtml#methods
  val ACL: IdempotentMethod = new Method("ACL") with Idempotent
  val `BASELINE-CONTROL`: IdempotentMethod = new Method("BASELINE-CONTROL") with Idempotent
  val BIND: IdempotentMethod = new Method("BIND") with Idempotent
  val CHECKIN: IdempotentMethod = new Method("CHECKIN") with Idempotent
  val CHECKOUT: IdempotentMethod = new Method("CHECKOUT") with Idempotent
  val CONNECT: DefaultMethodWithBody = new Method("CONNECT") with Default with PermitsBody
  val COPY: IdempotentMethod = new Method("COPY") with Idempotent
  val DELETE: IdempotentMethodWithBody = new Method("DELETE") with Idempotent with PermitsBody
  val GET: SafeMethodWithBody = new Method("GET") with Safe with PermitsBody
  val HEAD: SafeMethodWithBody = new Method("HEAD") with Safe with PermitsBody
  val LABEL: IdempotentMethodWithBody = new Method("LABEL") with Idempotent with PermitsBody
  val LINK: IdempotentMethod = new Method("LINK") with Idempotent
  val LOCK: DefaultMethod = new Method("LOCK") with Default
  val MERGE: IdempotentMethod = new Method("MERGE") with Idempotent
  val MKACTIVITY: IdempotentMethod = new Method("MKACTIVITY") with Idempotent
  val MKCALENDAR: IdempotentMethod = new Method("MKCALENDAR") with Idempotent
  val MKCOL: IdempotentMethod = new Method("MKCOL") with Idempotent
  val MKREDIRECTREF: IdempotentMethod = new Method("MKREDIRECTREF") with Idempotent
  val MKWORKSPACE: IdempotentMethod = new Method("MKWORKSPACE") with Idempotent
  val MOVE: IdempotentMethod = new Method("MOVE") with Idempotent
  val OPTIONS: SafeMethodWithBody = new Method("OPTIONS") with Safe with PermitsBody
  val ORDERPATCH: IdempotentMethod = new Method("ORDERPATCH") with Idempotent
  val PATCH: DefaultMethodWithBody = new Method("PATCH") with Default with PermitsBody
  val POST: DefaultMethodWithBody = new Method("POST") with Default with PermitsBody
  val PROPFIND: SafeMethod = new Method("PROPFIND") with Safe
  val PROPPATCH: IdempotentMethod = new Method("PROPPATCH") with Idempotent
  val PUT: IdempotentMethodWithBody = new Method("PUT") with Idempotent with PermitsBody
  val REBIND: IdempotentMethod = new Method("REBIND") with Idempotent
  val REPORT: SafeMethod = new Method("REPORT") with Safe
  val SEARCH: SafeMethod = new Method("SEARCH") with Safe
  val TRACE: SafeMethodWithBody = new Method("TRACE") with Safe with PermitsBody
  val UNBIND: IdempotentMethod = new Method("UNBIND") with Idempotent
  val UNCHECKOUT: IdempotentMethod = new Method("UNCHECKOUT") with Idempotent
  val UNLINK: IdempotentMethod = new Method("UNLINK") with Idempotent
  val UNLOCK: IdempotentMethod = new Method("UNLOCK") with Idempotent
  val UPDATE: IdempotentMethod = new Method("UPDATE") with Idempotent
  val UPDATEREDIRECTREF: IdempotentMethod = new Method("UPDATEREDIRECTREF") with Idempotent
  val `VERSION-CONTROL`: IdempotentMethod = new Method("VERSION-CONTROL") with Idempotent

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

  implicit val http4sEqForMethod: Eq[Method] = Eq.fromUniversalEquals
  implicit val http4sShowForMethod: Show[Method] = Show.fromToString
}

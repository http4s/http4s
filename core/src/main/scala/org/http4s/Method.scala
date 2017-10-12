package org.http4s

import cats._
import cats.implicits._
import org.http4s.parser.Rfc2616BasicRules
import org.http4s.util.{Renderable, Writer}
import org.http4s.Method.Semantics

/**
  * An HTTP method.
  *
  * @see [http://tools.ietf.org/html/rfc7231#section-4 RFC7321, Section 4]
  * @see [http://www.iana.org/assignments/http-methods/http-methods.xhtml IANA HTTP Method Registry]
  */
sealed abstract case class Method private (name: String) extends Renderable with Semantics {
  final override def render(writer: Writer): writer.type = writer << name
}

object Method extends MethodInstances {
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

  // TODO: find out the rest of the body permissions. http://www.iana.org/assignments/http-methods/http-methods.xhtml#methods
  val ACL: Method = new Method("ACL") with Idempotent
  val `BASELINE-CONTROL`: Method = new Method("BASELINE-CONTROL") with Idempotent
  val BIND: Method = new Method("BIND") with Idempotent
  val CHECKIN: Method = new Method("CHECKIN") with Idempotent
  val CHECKOUT: Method = new Method("CHECKOUT") with Idempotent
  val CONNECT: Method = new Method("CONNECT") with Default with NoBody
  val COPY: Method = new Method("COPY") with Idempotent
  val DELETE: Method = new Method("DELETE") with Idempotent with NoBody
  val GET: Method = new Method("GET") with Safe with NoBody
  val HEAD: Method = new Method("HEAD") with Safe with NoBody
  val LABEL: Method = new Method("LABEL") with Idempotent with PermitsBody
  val LINK: Method = new Method("LINK") with Idempotent
  val LOCK: Method = new Method("LOCK") with Default
  val MERGE: Method = new Method("MERGE") with Idempotent
  val MKACTIVITY: Method = new Method("MKACTIVITY") with Idempotent
  val MKCALENDAR: Method = new Method("MKCALENDAR") with Idempotent
  val MKCOL: Method = new Method("MKCOL") with Idempotent
  val MKREDIRECTREF: Method = new Method("MKREDIRECTREF") with Idempotent
  val MKWORKSPACE: Method = new Method("MKWORKSPACE") with Idempotent
  val MOVE: Method = new Method("MOVE") with Idempotent
  val OPTIONS: Method = new Method("OPTIONS") with Safe with PermitsBody
  val ORDERPATCH: Method = new Method("ORDERPATCH") with Idempotent
  val PATCH: Method = new Method("PATCH") with Default with PermitsBody
  val POST: Method = new Method("POST") with Default with PermitsBody
  val PROPFIND: Method = new Method("PROPFIND") with Safe
  val PROPPATCH: Method = new Method("PROPPATCH") with Idempotent
  val PUT: Method = new Method("PUT") with Idempotent with PermitsBody
  val REBIND: Method = new Method("REBIND") with Idempotent
  val REPORT: Method = new Method("REPORT") with Safe
  val SEARCH: Method = new Method("SEARCH") with Safe
  val TRACE: Method = new Method("TRACE") with Safe with PermitsBody
  val UNBIND: Method = new Method("UNBIND") with Idempotent
  val UNCHECKOUT: Method = new Method("UNCHECKOUT") with Idempotent
  val UNLINK: Method = new Method("UNLINK") with Idempotent
  val UNLOCK: Method = new Method("UNLOCK") with Idempotent
  val UPDATE: Method = new Method("UPDATE") with Idempotent
  val UPDATEREDIRECTREF: Method = new Method("UPDATEREDIRECTREF") with Idempotent
  val `VERSION-CONTROL`: Method = new Method("VERSION-CONTROL") with Idempotent

  val all = List(
    ACL, `BASELINE-CONTROL`, BIND, CHECKIN, CHECKOUT, CONNECT, COPY,
    DELETE, GET, HEAD, LABEL, LINK, LOCK, MERGE, MKACTIVITY, MKCALENDAR,
    MKCOL, MKREDIRECTREF, MKWORKSPACE, MOVE, OPTIONS, ORDERPATCH, PATCH,
    POST, PROPFIND, PROPPATCH, PUT, REBIND, REPORT, SEARCH, TRACE,
    UNBIND, UNCHECKOUT, UNLINK, UNLOCK, UPDATEREDIRECTREF, `VERSION-CONTROL`
  )

  private val allByKey: Map[String, Right[Nothing, Method]] = all.map(m => (m.name, Right(m))).toMap
}

trait MethodInstances {
  implicit val MethodInstances = new Show[Method] with Eq[Method] {
    override def show(f: Method): String = f.toString
    override def eqv(a1: Method, a2: Method): Boolean = a1 == a2
  }
}

package org.http4s

import scala.collection.concurrent.TrieMap

import cats._
import cats.implicits._
import org.http4s.parser.Rfc2616BasicRules
import org.http4s.util.{Renderable, Writer}

import Method.Semantics

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
    registry.getOrElse(
      s,
      Rfc2616BasicRules
        .token(s)
        .bimap(
          e => ParseFailure("Invalid method", e.details),
          new Method(_) with Semantics.Default
        ))

  import Semantics._

  // Lookups will usually be on fromString, so we store it wrapped in a Right
  private val registry = TrieMap[String, Right[Nothing, Method]]()

  private def register[M <: Method](method: M): method.type = {
    registry(method.name) = Right(method)
    method
  }

  def registered: Iterable[Method] = registry.readOnlySnapshot().values.map(_.right.get)
  // TODO: find out the rest of the body permissions. http://www.iana.org/assignments/http-methods/http-methods.xhtml#methods
  val ACL = register(new Method("ACL") with Idempotent)
  val `BASELINE-CONTROL` = register(new Method("BASELINE-CONTROL") with Idempotent)
  val BIND = register(new Method("BIND") with Idempotent)
  val CHECKIN = register(new Method("CHECKIN") with Idempotent)
  val CHECKOUT = register(new Method("CHECKOUT") with Idempotent)
  val CONNECT = register(new Method("CONNECT") with Default with NoBody)
  val COPY = register(new Method("COPY") with Idempotent)
  val DELETE = register(new Method("DELETE") with Idempotent with NoBody)
  val GET = register(new Method("GET") with Safe with NoBody)
  val HEAD = register(new Method("HEAD") with Safe with NoBody)
  val LABEL = register(new Method("LABEL") with Idempotent with PermitsBody)
  val LINK = register(new Method("LINK") with Idempotent)
  val LOCK = register(new Method("LOCK") with Default)
  val MERGE = register(new Method("MERGE") with Idempotent)
  val MKACTIVITY = register(new Method("MKACTIVITY") with Idempotent)
  val MKCALENDAR = register(new Method("MKCALENDAR") with Idempotent)
  val MKCOL = register(new Method("MKCOL") with Idempotent)
  val MKREDIRECTREF = register(new Method("MKREDIRECTREF") with Idempotent)
  val MKWORKSPACE = register(new Method("MKWORKSPACE") with Idempotent)
  val MOVE = register(new Method("MOVE") with Idempotent)
  val OPTIONS = register(new Method("OPTIONS") with Safe with PermitsBody)
  val ORDERPATCH = register(new Method("ORDERPATCH") with Idempotent)
  val PATCH = register(new Method("PATCH") with Default with PermitsBody)
  val POST = register(new Method("POST") with Default with PermitsBody)
  val PROPFIND = register(new Method("PROPFIND") with Safe)
  val PROPPATCH = register(new Method("PROPPATCH") with Idempotent)
  val PUT = register(new Method("PUT") with Idempotent with PermitsBody)
  val REBIND = register(new Method("REBIND") with Idempotent)
  val REPORT = register(new Method("REPORT") with Safe)
  val SEARCH = register(new Method("SEARCH") with Safe)
  val TRACE = register(new Method("TRACE") with Safe with PermitsBody)
  val UNBIND = register(new Method("UNBIND") with Idempotent)
  val UNCHECKOUT = register(new Method("UNCHECKOUT") with Idempotent)
  val UNLINK = register(new Method("UNLINK") with Idempotent)
  val UNLOCK = register(new Method("UNLOCK") with Idempotent)
  val UPDATE = register(new Method("UPDATE") with Idempotent)
  val UPDATEREDIRECTREF = register(new Method("UPDATEREDIRECTREF") with Idempotent)
  val `VERSION-CONTROL` = register(new Method("VERSION-CONTROL") with Idempotent)
}

trait MethodInstances {
  implicit val MethodInstances = new Show[Method] with Eq[Method] {
    override def show(f: Method): String = f.toString
    override def eqv(a1: Method, a2: Method): Boolean = a1 == a2
  }
}

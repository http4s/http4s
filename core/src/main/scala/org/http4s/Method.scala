package org.http4s

import scala.collection.concurrent.TrieMap
import scalaz._

import org.http4s.parser.Rfc2616BasicRules
import org.http4s.util.{Writer, Renderable}

import Method.Semantics

/**
 * An HTTP method.
 *
 * @see [http://tools.ietf.org/html/rfc7231#section-4 RFC7321, Section 4]
 * @see [http://www.iana.org/assignments/http-methods/http-methods.xhtml IANA HTTP Method Registry]
 */
final case class Method private (name: String)(semantics: Semantics = Semantics.Default) extends Renderable {
  override def render(writer: Writer): writer.type = writer ~ name
  def isIdempotent = semantics.isIdempotent
  def isSafe = semantics.isSafe
}

object Method extends MethodInstances {
  sealed trait Semantics {
    def isIdempotent: Boolean
    def isSafe: Boolean
  }

  object Semantics {
    case object Default extends Semantics {
      def isIdempotent = false
      def isSafe = false
    }
    case object Idempotent extends Semantics {
      def isIdempotent = true
      def isSafe = false
    }
    case object Safe extends Semantics {
      def isIdempotent = true
      def isSafe = true
    }
  }

  def fromString(s: String): ParseResult[Method] =
    registry.getOrElse(s, Rfc2616BasicRules.token(s).bimap(
      e => ParseFailure("Invalid method", e.details),
      new Method(_)()
    ))

  import Semantics._

  // Lookups will usually be on fromString, so we store it wrapped in a \/-
  private val registry = TrieMap[String, \/-[Method]]()

  private def register(method: Method): method.type = {
    registry(method.name) = \/-(method)
    method
  }

  def registered: Iterable[Method] = registry.readOnlySnapshot().values.map(_.b)

  val ACL = register(new Method("ACL")(Idempotent))
  val `BASELINE-CONTROL` = register(new Method("BASELINE-CONTROL")(Idempotent))
  val BIND = register(new Method("BIND")(Idempotent))
  val CHECKIN = register(new Method("CHECKIN")(Idempotent))
  val CHECKOUT = register(new Method("CHECKOUT")(Idempotent))
  val CONNECT = register(new Method("CONNECT")())
  val COPY = register(new Method("COPY")(Idempotent))
  val DELETE = register(new Method("DELETE")(Idempotent))
  val GET = register(new Method("GET")(Safe))
  val HEAD = register(new Method("HEAD")(Safe))
  val LABEL = register(new Method("LABEL")(Idempotent))
  val LINK = register(new Method("LINK")(Idempotent))
  val LOCK = register(new Method("LOCK")())
  val MERGE = register(new Method("MERGE")(Idempotent))
  val MKACTIVITY = register(new Method("MKACTIVITY")(Idempotent))
  val MKCALENDAR = register(new Method("MKCALENDAR")(Idempotent))
  val MKCOL = register(new Method("MKCOL")(Idempotent))
  val MKREDIRECTREF = register(new Method("MKREDIRECTREF")(Idempotent))
  val MKWORKSPACE = register(new Method("MKWORKSPACE")(Idempotent))
  val MOVE = register(new Method("MOVE")(Idempotent))
  val OPTIONS = register(new Method("OPTIONS")(Safe))
  val ORDERPATCH = register(new Method("ORDERPATCH")(Idempotent))
  val PATCH = register(new Method("PATCH")())
  val POST = register(new Method("POST")())
  val PROPFIND = register(new Method("PROPFIND")(Safe))
  val PROPPATCH = register(new Method("PROPPATCH")(Idempotent))
  val PUT = register(new Method("PUT")(Idempotent))
  val REBIND = register(new Method("REBIND")(Idempotent))
  val REPORT = register(new Method("REPORT")(Safe))
  val SEARCH = register(new Method("SEARCH")(Safe))
  val TRACE = register(new Method("TRACE")(Safe))
  val UNBIND = register(new Method("UNBIND")(Idempotent))
  val UNCHECKOUT = register(new Method("UNCHECKOUT")(Idempotent))
  val UNLINK = register(new Method("UNLINK")(Idempotent))
  val UNLOCK = register(new Method("UNLOCK")(Idempotent))
  val UPDATE = register(new Method("UPDATE")(Idempotent))
  val UPDATEREDIRECTREF = register(new Method("UPDATEREDIRECTREF")(Idempotent))
  val `VERSION-CONTROL` = register(new Method("VERSION-CONTROL")(Idempotent))
}

trait MethodInstances {
  implicit val MethodInstances = new Show[Method] with Equal[Method] {
    override def shows(f: Method): String = f.toString
    override def equal(a1: Method, a2: Method): Boolean = a1 == a2
  }
}

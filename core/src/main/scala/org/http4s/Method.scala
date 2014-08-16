package org.http4s

import scala.util.control.NoStackTrace
import scalaz._

import org.http4s.parser.Rfc2616BasicRules
import org.http4s.util.{Writer, Renderable}
import scalaz.concurrent.Task

/**
 * An HTTP method.
 *
 * @param name the name of the method.
 * @see [http://tools.ietf.org/html/rfc7231#section-4 RFC7321, Section 4]
 */
final case class Method private (name: String) extends Renderable {
  override def render[W <: Writer](writer: W): writer.type = writer ~ name
}

object Method extends MethodInstances {
  def fromString(s: String): ParseResult[Method] =
    registry.getOrElse(s, Rfc2616BasicRules.token(s).bimap(
      e => ParseFailure("Invalid method", e.details),
      new Method(_))
    )

  val GET = new Method("GET")
  val HEAD = new Method("HEAD")
  val POST = new Method("POST")
  val PUT = new Method("PUT")
  val DELETE = new Method("DELETE")
  val CONNECT = new Method("CONNECT")
  val OPTIONS = new Method("OPTIONS")
  val TRACE = new Method("TRACE")
  val PATCH = new Method("PATCH")

  private val registry: Map[String, Nothing \/ Method] =
    Seq(GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE, PATCH).map { m =>
      m.name -> \/-(m)
    }.toMap
}

trait MethodInstances {
  implicit val MethodShow: Show[Method] = Show.shows(_.renderString)
  implicit val MethodEqual: Equal[Method] = Equal.equalA
}

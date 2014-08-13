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
final case class Method private (val name: String) extends AnyVal with Renderable {
  override def render[W <: Writer](writer: W): writer.type = writer ~ name

  /** Make a [[org.http4s.Request]] using this Method */
  def apply(uri: Uri): Task[Request] = Task.now(Request(this, uri))

  /** Make a [[org.http4s.Request]] using this Method */
  def apply(uri: String): Task[Request] = {
    apply(Uri.fromString(uri)
      .getOrElse(throw new IllegalArgumentException(s"Invalid path: $uri")))
  }
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

trait MethodConstants {
  final val GET = Method.GET
  final val HEAD = Method.HEAD
  final val POST = Method.POST
  final val PUT = Method.PUT
  final val DELETE = Method.DELETE
  final val CONNECT = Method.CONNECT
  final val OPTIONS = Method.OPTIONS
  final val TRACE = Method.TRACE
  final val PATCH = Method.PATCH
}


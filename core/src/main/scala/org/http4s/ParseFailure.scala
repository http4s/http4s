package org.http4s

import scala.util.control.NoStackTrace
import scalaz.concurrent.Task
import scalaz.{\/-, -\/, Equal}

/**
 * Indicates an error parsing an HTTP message.
 *
 * @param sanitized May safely be displayed to a client to describe an error
 *                  condition.  Should not echo any part of a Request.
 *
 * @param details Contains any relevant details omitted from the sanitized
 *                version of the error.  This may freely echo a Request.
 */
final case class ParseFailure(sanitized: String, details: String)

final case class ParseException(failure: ParseFailure) extends RuntimeException(failure.sanitized)

object ParseFailure {
  implicit val eq = Equal.equalA[ParseFailure]
}

object ParseResult {
  def fail(sanitized: String, details: String) = -\/(ParseFailure(sanitized, details))
  def success[A](a: A) = \/-(a)
}

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

import cats.Eq
import cats.MonadError
import cats.instances.either._
import cats.parse.Parser0
import cats.syntax.all._

import scala.util.control.NoStackTrace
import scala.util.control.NonFatal

/** Indicates a failure to handle an HTTP [[Message]]. */
trait MessageFailure extends RuntimeException {

  /** Provides a message appropriate for logging. */
  def message: String

  /* Overridden for sensible logging of the failure */
  override final def getMessage: String = message

  def cause: Option[Throwable]

  override final def getCause: Throwable = cause.orNull

  /** Provides a default rendering of this failure as a [[Response]]. */
  def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F]
}

/** Indicates an error parsing an HTTP [[Message]].
  *
  * @param sanitized May safely be displayed to a client to describe an error
  *                  condition.  Should not echo any part of a Request.
  * @param details Contains any relevant details omitted from the sanitized
  *                version of the error.  This may freely echo a Request.
  */
final case class ParseFailure(sanitized: String, details: String)
    extends MessageFailure
    with NoStackTrace {
  def message: String =
    if (sanitized.isEmpty) details
    else if (details.isEmpty) sanitized
    else s"$sanitized: $details"

  def cause: Option[Throwable] = None

  def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(Status.BadRequest, httpVersion)
      .withEntity(sanitized)
}

object ParseFailure {
  implicit val eq: Eq[ParseFailure] = Eq.fromUniversalEquals[ParseFailure]
}

object ParseResult {
  def fail(sanitized: String, details: String): ParseResult[Nothing] =
    Left(ParseFailure(sanitized, details))

  def success[A](a: A): ParseResult[A] =
    Right(a)

  def fromTryCatchNonFatal[A](sanitized: String)(f: => A): ParseResult[A] =
    try ParseResult.success(f)
    catch {
      case NonFatal(e) => Left(ParseFailure(sanitized, e.getMessage))
    }

  private[http4s] def fromParser[A](parser: Parser0[A], errorMessage: => String)(
      s: String
  ): ParseResult[A] =
    try parser.parseAll(s).leftMap(e => ParseFailure(errorMessage, e.show))
    catch { case p: ParseFailure => p.asLeft[A] }

  implicit val parseResultMonad: MonadError[ParseResult, ParseFailure] =
    catsStdInstancesForEither[ParseFailure]
}

/** Indicates a problem decoding a [[Message]].  This may either be a problem with
  * the entity headers or with the entity itself.
  */
trait DecodeFailure extends MessageFailure

object DecodeFailure {
  implicit val http4sEqForDecodeFailure: Eq[DecodeFailure] = Eq.fromUniversalEquals
}

/** Indicates a problem decoding a [[Message]] body. */
trait MessageBodyFailure extends DecodeFailure

/** Indicates an syntactic error decoding the body of an HTTP [[Message]]. */
final case class MalformedMessageBodyFailure(details: String, cause: Option[Throwable] = None)
    extends MessageBodyFailure {
  def message: String =
    s"Malformed message body: $details"

  def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(Status.BadRequest, httpVersion)
      .withEntity(s"The request body was malformed.")
}

/** Indicates a semantic error decoding the body of an HTTP [[Message]]. */
final case class InvalidMessageBodyFailure(details: String, cause: Option[Throwable] = None)
    extends MessageBodyFailure {
  def message: String =
    s"Invalid message body: $details"

  def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(Status.UnprocessableContent, httpVersion)
      .withEntity(s"The request body was invalid.")
}

/** Indicates that a [[Message]] came with no supported [[MediaType]]. */
sealed abstract class UnsupportedMediaTypeFailure extends DecodeFailure with NoStackTrace {
  def expected: Set[MediaRange]
  def cause: Option[Throwable] = None

  protected def sanitizedResponsePrefix: String
  protected def expectedMsg: String =
    s"Expected one of the following media ranges: ${expected.iterator.map(_.show).mkString(", ")}"
  protected def responseMsg: String = s"$sanitizedResponsePrefix. $expectedMsg"

  def toHttpResponse[F[_]](httpVersion: HttpVersion): Response[F] =
    Response(Status.UnsupportedMediaType, httpVersion)
      .withEntity(responseMsg)
}

/** Indicates that a [[Message]] attempting to be decoded has no [[MediaType]] and no
  * [[EntityDecoder]] was lenient enough to accept it.
  */
final case class MediaTypeMissing(expected: Set[MediaRange]) extends UnsupportedMediaTypeFailure {
  def sanitizedResponsePrefix: String = "No media type specified in Content-Type header"
  def message: String = responseMsg
}

/** Indicates that no [[EntityDecoder]] matches the [[MediaType]] of the [[Message]] being decoded */
final case class MediaTypeMismatch(messageType: MediaType, expected: Set[MediaRange])
    extends UnsupportedMediaTypeFailure {
  def sanitizedResponsePrefix: String =
    "Media type supplied in Content-Type header is not supported"
  def message: String = s"${messageType.show} is not a supported media type. $expectedMsg"
}

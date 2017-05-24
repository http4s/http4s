package org.http4s

import scala.util.control.{NoStackTrace, NonFatal}

import cats._
import cats.data._
import cats.implicits._
import fs2._

/** Indicates a failure to handle an HTTP [[Message]]. */
sealed abstract class MessageFailure extends RuntimeException {

  /** Provides a message appropriate for logging. */
  def message: String

  /* Overridden for sensible logging of the failure */
  final override def getMessage: String =
    message

  /** Provides a default rendering of this failure as a [[Response]]. */
  def toHttpResponse[F[_]](httpVersion: HttpVersion)(implicit F: Applicative[F]): F[Response[F]]

}

/**
  * Indicates an error parsing an HTTP [[Message]].
  */
sealed abstract class ParsingFailure extends MessageFailure with NoStackTrace

/**
  * Indicates an error parsing an HTTP [[Message]].
  *
  * @param sanitized May safely be displayed to a client to describe an error
  *                  condition.  Should not echo any part of a Request.
  * @param details Contains any relevant details omitted from the sanitized
  *                version of the error.  This may freely echo a Request.
  */
final case class ParseFailure(sanitized: String, details: String) extends ParsingFailure {

  def message: String =
    if (sanitized.isEmpty) details
    else if (details.isEmpty) sanitized
    else s"$sanitized: $details"

  def toHttpResponse[F[_]](httpVersion: HttpVersion)(implicit F: Applicative[F]): F[Response[F]] =
    Response[F](Status.BadRequest, httpVersion).withBody(sanitized)(F, EntityEncoder.stringEncoder[F])
}

/** TODO parameterization
/** Generic description of a failure to parse an HTTP [[Message]] */
final case class GenericParsingFailure(sanitized: String, details: String, response: HttpVersion => F[Response[F]]) extends ParsingFailure {
  def message: String =
    ParseFailure(sanitized, details).message

  def toHttpResponse[F[_]](httpVersion: HttpVersion): F[Response[F]] =
    response(httpVersion)
}
 */

object ParseFailure {
  implicit val eq = Eq.fromUniversalEquals[ParseFailure]
}

object ParseResult {
  def fail(sanitized: String, details: String): ParseResult[Nothing] =
    Either.left(ParseFailure(sanitized, details))
  def success[A](a: A): ParseResult[A] =
    Either.right(a)

  def fromTryCatchNonFatal[A](sanitized: String)(f: => A): ParseResult[A] =
    try ParseResult.success(f)
    catch {
      case NonFatal(e) => Either.left(ParseFailure(sanitized, e.getMessage))
    }

  implicit val parseResultMonad: MonadError[ParseResult, ParseFailure] = catsStdInstancesForEither[ParseFailure]

  // implicit class ParseResultOps[A](parseResult: ParseResult[A])
  //     extends catsStdInstancesForEither[ParseFailure]

}

/** Indicates a problem decoding a [[Message]].  This may either be a problem with
  * the entity headers or with the entity itself.   */
sealed abstract class DecodeFailure extends MessageFailure

/** Generic description of a failure to decode a [[Message]] */
/** TODO parameterization
final case class GenericDecodeFailure(message: String, response: HttpVersion => F[Response[F]]) extends DecodeFailure {
  def toHttpResponse[F[_]](httpVersion: HttpVersion): F[Response[F]] =
    response(httpVersion)
}
 */

/** Indicates a problem decoding a [[Message]] body. */
sealed abstract class MessageBodyFailure extends DecodeFailure {

  def cause: Option[Throwable] = None

  override def getCause: Throwable =
    cause.orNull
}

/** Generic description of a failure to handle a [[Message]] body */
/** TODO parameterization
final case class GenericMessageBodyFailure(message: String,
                                           override val cause: Option[Throwable],
                                           response: HttpVersion => F[Response[F]]) extends MessageBodyFailure {
  def toHttpResponse[F[_]](httpVersion: HttpVersion): F[Response[F]] =
    response(httpVersion)
}
 */

/** Indicates an syntactic error decoding the body of an HTTP [[Message]]. */
sealed case class MalformedMessageBodyFailure(details: String, override val cause: Option[Throwable] = None) extends MessageBodyFailure {
  def message: String =
    s"Malformed request body: $details"

  def toHttpResponse[F[_]](httpVersion: HttpVersion)(implicit F: Applicative[F]): F[Response[F]] =
    Response[F](Status.BadRequest, httpVersion).withBody(s"The request body was malformed.")(F, EntityEncoder.stringEncoder[F])
}

/** Indicates a semantic error decoding the body of an HTTP [[Message]]. */
sealed case class InvalidMessageBodyFailure(details: String, override val cause: Option[Throwable] = None) extends MessageBodyFailure {
  def message: String =
    s"Invalid request body: $details"

  override def toHttpResponse[F[_]](httpVersion: HttpVersion)(implicit F: Applicative[F]): F[Response[F]] =
    Response[F](Status.UnprocessableEntity, httpVersion).withBody(s"The request body was invalid.")(F, EntityEncoder.stringEncoder[F])
}

/** Indicates that a [[Message]] came with no supported [[MediaType]]. */
sealed abstract class UnsupportedMediaTypeFailure(expected: Set[MediaRange]) extends DecodeFailure with NoStackTrace {
  def sanitizedResponsePrefix: String

  val expectedMsg: String = s"Expected one of the following media ranges: ${expected.map(_.renderString).mkString(", ")}"
  val responseMsg: String = s"$sanitizedResponsePrefix. $expectedMsg"

  def toHttpResponse[F[_]](httpVersion: HttpVersion)(implicit F: Applicative[F]): F[Response[F]] =
    Response[F](Status.UnsupportedMediaType, httpVersion)
      .withBody(responseMsg)(F, EntityEncoder.stringEncoder[F])
}

/** Indicates that a [[Message]] attempting to be decoded has no [[MediaType]] and no
  * [[EntityDecoder]] was lenient enough to accept it. */
final case class MediaTypeMissing(expected: Set[MediaRange])
  extends UnsupportedMediaTypeFailure(expected)
{
  def sanitizedResponsePrefix: String = "No media type specified in Content-Type header"
  val message: String = responseMsg
}

/** Indicates that no [[EntityDecoder]] matches the [[MediaType]] of the [[Message]] being decoded */
final case class MediaTypeMismatch(messageType: MediaType, expected: Set[MediaRange])
  extends UnsupportedMediaTypeFailure(expected)
{
  def sanitizedResponsePrefix: String = "Media type supplied in Content-Type header is not supported"
  def message: String = s"${messageType.renderString} is not a supported media type. $expectedMsg"
}

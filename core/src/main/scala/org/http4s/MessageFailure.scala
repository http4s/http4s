package org.http4s

import scala.util.control.{NoStackTrace, NonFatal}
import scalaz.concurrent.Task
import scalaz.{\/-, -\/, Equal}

/** Indicates a failure to handle an HTTP [[Message]]. */
sealed abstract class MessageFailure extends RuntimeException {
  /** Provides a message appropriate for logging. */
  def message: String

  /* Overridden for sensible logging of the failure */
  final override def getMessage: String =
    message

  /** Provides a default rendering of this failure as a [[Response]]. */
  def toHttpResponse(httpVersion: HttpVersion): Task[Response]
}

/**
  * Indicates an error parsing an HTTP [[Message]].
  *
  * @param sanitized May safely be displayed to a client to describe an error
  *                  condition.  Should not echo any part of a Request.
  * @param details Contains any relevant details omitted from the sanitized
  *                version of the error.  This may freely echo a Request.
  */
final case class ParseFailure(sanitized: String, details: String) extends MessageFailure with NoStackTrace {
  override def message: String =
    if (sanitized.isEmpty) details
    else if (details.isEmpty) sanitized
    else s"$sanitized: $details"

  override def toHttpResponse(httpVersion: HttpVersion): Task[Response] =
    Response(Status.BadRequest, httpVersion).withBody(sanitized)
}

object ParseFailure {
  implicit val eq = Equal.equalA[ParseFailure]
}

object ParseResult {
  def fail(sanitized: String, details: String) = -\/(ParseFailure(sanitized, details))
  def success[A](a: A) = \/-(a)

  def fromTryCatchNonFatal[A](sanitized: String)(f: => A): ParseResult[A] =
    try ParseResult.success(f)
    catch {
      case NonFatal(e) => -\/(ParseFailure(sanitized, e.getMessage))
    }
}

/** Indicates a problem decoding a [[Message]].  This may either be a problem with
  * the entity headers or with the entity itself.   */
sealed abstract class DecodeFailure extends MessageFailure

/** Indicates a problem decoding a [[Message]] body. */
sealed abstract class MessageBodyFailure extends DecodeFailure {
  def cause: Option[Throwable] = None

  override def getCause: Throwable =
    cause.orNull
}

/** Indicates an syntactic error decoding the body of an HTTP [[Message]. */
sealed case class MalformedMessageBodyFailure(details: String, override val cause: Option[Throwable] = None) extends MessageBodyFailure {
  override def message: String =
    s"Malformed request body: $details"

  override def toHttpResponse(httpVersion: HttpVersion): Task[Response] =
    Response(Status.BadRequest, httpVersion).withBody(s"The request body was malformed.")
}

/** Indicates a semantic error decoding the body of an HTTP [[Message]]. */
sealed case class InvalidMessageBodyFailure(details: String, override val cause: Option[Throwable] = None) extends MessageBodyFailure {
  override def message: String =
    s"Invalid request body: $details"

  override def toHttpResponse(httpVersion: HttpVersion): Task[Response] =
    Response(Status.UnprocessableEntity, httpVersion).withBody(s"The request body was invalid.")
}

/** Indicates that a [[Message]] came with no supported [[MediaType]]. */
sealed abstract class UnsupportedMediaTypeFailure(expected: Set[MediaRange]) extends DecodeFailure with NoStackTrace {
  def sanitizedResponsePrefix: String

  val expectedMsg = s"Expected one of the following media ranges: ${expected.map(_.renderString).mkString(", ")}"
  val responseMsg = s"$sanitizedResponsePrefix. $expectedMsg"

  override def toHttpResponse(httpVersion: HttpVersion): Task[Response] =
    Response(Status.UnsupportedMediaType, httpVersion)
      .withBody(responseMsg)
}

/** Indicates that a [[Message]] attempting to be decoded has no [[MediaType]] and no
  * [[EntityDecoder]] was lenient enough to accept it. */
final case class MediaTypeMissing(expected: Set[MediaRange])
  extends UnsupportedMediaTypeFailure(expected)
{
  def sanitizedResponsePrefix = "No media type specified in Content-Type header"
  val message = responseMsg
}

/** Indicates that no [[EntityDecoder]] matches the [[MediaType]] of the [[Message]] being decoded */
final case class MediaTypeMismatch(messageType: MediaType, expected: Set[MediaRange])
  extends UnsupportedMediaTypeFailure(expected)
{
  def sanitizedResponsePrefix = "Media type supplied in Content-Type header is not supported"
  def message = s"${messageType.renderString} is not a supported media type. $expectedMsg"
}

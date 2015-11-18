package org.http4s

import scalaz.{\/-, -\/, Equal}

sealed trait DecodeFailure {
  def msg: String

  def toHttpResponse(httpVersion: HttpVersion) = this match {
    case e: ParseFailure => Response(Status.BadRequest, httpVersion).withBody(e.sanitized)
    case e: MediaTypeMismatch => Response(Status.UnsupportedMediaType, httpVersion).withBody(
      s"${e.messageType} is not a supported media type. Please send a request that satisfies one of the following media ranges: ${e.expected}")
    case e: MediaTypeMissing => Response(Status.UnsupportedMediaType, httpVersion).withBody(
      s"Please specify a media type in the following ranges: ${e.expected}")
  }
}


final case class DecodeFailureException(failure: DecodeFailure) extends RuntimeException(failure.msg)

/**
  * Indicates an error parsing an HTTP message.
  *
  * @param sanitized May safely be displayed to a client to describe an error
  *                  condition.  Should not echo any part of a Request.
  *
  * @param details Contains any relevant details omitted from the sanitized
  *                version of the error.  This may freely echo a Request.
  */
final case class ParseFailure(sanitized: String, details: String) extends DecodeFailure {
  val msg = sanitized
}

final case class ParseException(failure: ParseFailure) extends RuntimeException(failure.sanitized)

object ParseFailure {
  implicit val eq = Equal.equalA[ParseFailure]
}

object ParseResult {
  def fail(sanitized: String, details: String) = -\/(ParseFailure(sanitized, details))
  def success[A](a: A) = \/-(a)
}

/** Indicates that a Message attempting to be decoded has no [[MediaType]] and no
  * [[EntityDecoder]] was lenient enough to accept it.
  */
case class MediaTypeMissing(expected: Set[MediaRange]) extends DecodeFailure {
  val msg = s"Decoder is unable to decode a Message without a MediaType. Expected media ranges: $expected"
}

/** Indicates that no [[EntityDecoder]] matches the [[MediaType]] of the message being decoded*/
case class MediaTypeMismatch(messageType: MediaType, expected: Set[MediaRange]) extends DecodeFailure {
  def msg = s"$messageType is not a supported media type. Decoder accepts one of the following media ranges: $expected"
}

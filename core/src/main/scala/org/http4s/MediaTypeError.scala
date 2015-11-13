package org.http4s

sealed trait MediaTypeError {
  def msg: String
}

/** Indicates that a Message attempting to be decoded has no [[MediaType]] and no
  * [[EntityDecoder]] was lenient enough to accept it.
  */
case class MediaTypeMissing(expected: Set[MediaRange]) extends MediaTypeError {
  val msg = s"Decoder is unable to decode a Message without a MediaType. Expected media ranges: $expected"
}

/** Indicates that no [[EntityDecoder]] matches the [[MediaType]] of the message being decoded*/
case class MediaTypeMismatch(messageType: MediaType, expected: Set[MediaRange]) extends MediaTypeError {
  def msg = s"$messageType is not a supported media type. Decoder accepts one of the following media ranges: $expected"
}

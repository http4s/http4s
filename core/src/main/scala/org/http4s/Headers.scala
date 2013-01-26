package org.http4s

trait Headers extends Seq[Header]

trait Header {
  def name: String
  def value: String
}

trait RequestHeaders extends Headers {
  def contentType: Option[ContentType]
  def contentLength: Option[Long]
}

object RequestHeaders {
  val Empty: RequestHeaders = ???
}

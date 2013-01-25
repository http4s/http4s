package org.http4s

trait Headers extends Seq[Header] {
  def contentType: Option[ContentType]
  def contentLength: Option[Long]
}

trait Header {
  def name: String
  def value: String
}

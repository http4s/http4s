package org.http4s

sealed abstract class ContentCodingRange {
  def value: CiString
  def matches(encoding: ContentCoding): Boolean
}

final case class ContentCoding(value: CiString) extends ContentCodingRange {
  def matches(encoding: ContentCoding) = this == encoding
}

object ContentCoding extends ObjectRegistry[CiString, ContentCoding] {
  def register(encoding: ContentCoding): ContentCoding = {
    register(encoding.value, encoding)
    encoding
  }

  def register(value: String): ContentCoding = ContentCoding(value.lowercaseEn)

  val `*`: ContentCodingRange = new ContentCodingRange {
    def value = "*".lowercaseEn
    def matches(encoding: ContentCoding) = true
  }

  // see http://www.iana.org/assignments/http-parameters/http-parameters.xml
  val compress       = register("compress")
  // TODO: This is actually a transfer-coding
  val chunked        = register("chunked")
  val deflate        = register("deflate")
  val exi            = register("exi")
  val gzip           = register("gzip")
  val identity       = register("identity")
  val `pack200-gzip` = register("pack200-gzip")

  // Legacy encodings defined by RFC2616 3.5.
  register("x-compress".lowercaseEn, compress)
  register("x-gzip".lowercaseEn, gzip)
}

package org.http4s

import org.http4s.util.{Registry, CaseInsensitiveString}

sealed abstract class ContentCodingRange extends HttpValue[CaseInsensitiveString] {
  def matches(encoding: ContentCoding): Boolean
}

sealed case class ContentCoding private (value: CaseInsensitiveString) extends ContentCodingRange {
  def matches(encoding: ContentCoding) = this == encoding
}

object ContentCoding extends Registry[CaseInsensitiveString, ContentCoding] {
  def apply(name: String): ContentCoding = getForKey(name.ci).getOrElse(new ContentCoding(name.ci))

  def register(encoding: ContentCoding): ContentCoding = {
    register(encoding.value, encoding)
    encoding
  }

  def register(value: String): ContentCoding = ContentCoding(value.ci)

  val `*`: ContentCodingRange = new ContentCodingRange {
    def value = "*".ci
    def matches(encoding: ContentCoding) = true
  }

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-1
  val compress       = register("compress")
  val deflate        = register("deflate")
  val exi            = register("exi")
  val gzip           = register("gzip")
  val identity       = register("identity")
  val `pack200-gzip` = register("pack200-gzip")

  // Legacy encodings defined by RFC2616 3.5.
  val `x-compress`   = register("x-compress".ci, compress)
  val `x-gzip`       = register("x-gzip".ci, gzip)
}

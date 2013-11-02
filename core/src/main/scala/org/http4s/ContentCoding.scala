package org.http4s

import org.http4s.util.Lowercase
import scalaz.@@
import java.util.Locale

sealed abstract class ContentCodingRange {
  def value: String @@ Lowercase
  def matches(encoding: ContentCoding): Boolean
}

final case class ContentCoding(value: String @@ Lowercase) extends ContentCodingRange {
  def matches(encoding: ContentCoding) = this == encoding
}

object ContentCodings extends ObjectRegistry[String @@ Lowercase, ContentCoding] {
  def register(encoding: ContentCoding): ContentCoding = {
    register(encoding.value.lowercase, encoding)
    encoding
  }

  def register(value: String): ContentCoding = ContentCoding(value.lowercase(Locale.ENGLISH))

  val `*`: ContentCodingRange = new ContentCodingRange {
    def value = "*".lowercase
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
}

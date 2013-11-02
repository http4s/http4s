package org.http4s

sealed abstract class ContentCodingRange {
  def value: String
  def matches(encoding: ContentCoding): Boolean
  override def toString = "HttpEncodingRange(" + value + ')'
}

sealed abstract class ContentCoding extends ContentCodingRange {
  def matches(encoding: ContentCoding) = this == encoding
  override def equals(obj: Any) = obj match {
    case x: ContentCoding => (this eq x) || value == x.value
    case _ => false
  }
  override def hashCode() = value.##
  override def toString = "Encoding(" + value + ')'
}

// see http://www.iana.org/assignments/http-parameters/http-parameters.xml
object ContentCodings extends ObjectRegistry[String, ContentCoding] {

  def register(encoding: ContentCoding): ContentCoding = {
    register(encoding.value.toLowerCase, encoding)
    encoding
  }

  val `*`: ContentCodingRange = new ContentCodingRange {
    def value = "*"
    def matches(encoding: ContentCoding) = true
  }

  private class PredefContentCoding(val value: String) extends ContentCoding

  val compress      = register(new PredefContentCoding("compress"))
  val chunked       = register(new PredefContentCoding("chunked"))
  val deflate       = register(new PredefContentCoding("deflate"))
  val gzip          = register(new PredefContentCoding("gzip"))
  val identity      = register(new PredefContentCoding("identity"))
  val `x-compress`  = register(new PredefContentCoding("x-compress"))
  val `x-zip`       = register(new PredefContentCoding("x-zip"))

  case class CustomHttpContentCoding(value: String) extends ContentCoding
}

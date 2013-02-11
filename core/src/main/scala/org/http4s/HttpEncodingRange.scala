package org.http4s

sealed abstract class HttpEncodingRange {
  def value: String
  def matches(encoding: HttpEncoding): Boolean
  override def toString = "HttpEncodingRange(" + value + ')'
}

sealed abstract class HttpEncoding extends HttpEncodingRange {
  def matches(encoding: HttpEncoding) = this == encoding
  override def equals(obj: Any) = obj match {
    case x: HttpEncoding => (this eq x) || value == x.value
    case _ => false
  }
  override def hashCode() = value.##
  override def toString = "HttpEncoding(" + value + ')'
}

// see http://www.iana.org/assignments/http-parameters/http-parameters.xml
object HttpEncodings extends ObjectRegistry[String, HttpEncoding] {

  def register(encoding: HttpEncoding): HttpEncoding = {
    register(encoding.value.toLowerCase, encoding)
    encoding
  }

  val `*`: HttpEncodingRange = new HttpEncodingRange {
    def value = "*"
    def matches(encoding: HttpEncoding) = true
  }

  private class PredefEncoding(val value: String) extends HttpEncoding

  val compress      = register(new PredefEncoding("compress"))
  val chunked       = register(new PredefEncoding("chunked"))
  val deflate       = register(new PredefEncoding("deflate"))
  val gzip          = register(new PredefEncoding("gzip"))
  val identity      = register(new PredefEncoding("identity"))
  val `x-compress`  = register(new PredefEncoding("x-compress"))
  val `x-zip`       = register(new PredefEncoding("x-zip"))

  case class CustomHttpEncoding(value: String) extends HttpEncoding
}

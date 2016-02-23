package org.http4s.util.encoding

import org.http4s.util.encoding.tags._
import org.http4s.parser.{Rfc3986CharPredicate => CP}
import org.parboiled2.CharPredicate


sealed trait EncodedString[E] extends Any {
  def encoded: String
  override def toString: String = encoded
}

object EncodedString {
  private[encoding] def apply[E](_encoded: String) = new EncodedString[E] { def encoded = _encoded }
}



case class EncodedFormQuery private[encoding](encoded: String) extends AnyVal with EncodedString[FormQuery]

case class EncodedPlainQuery private[encoding](encoded: String) extends AnyVal with EncodedString[UriQuery]

case class EncodedFormQueryKey private[encoding](encoded: String) extends AnyVal with EncodedString[FormQueryKey] {
  def asKV = EncodedString[FormQueryKV](encoded)
}

class EncodedFormQueryParam private(val encoded: String) extends AnyVal with EncodedString[FormQueryParam] {
  def asKey = EncodedFormQueryKey(encoded)
}

object EncodedFormQueryParam {
  private[encoding] def apply(simplePctEncoded: PctEncoded[FormQueryPreEncode]) =
    new EncodedFormQueryParam(simplePctEncoded.encoded.replace("%20", "+"))
}



case class PctEncoded[T] private[encoding](encoded: String) extends AnyVal with EncodedString[T]

case class PercentEncoding[T](isLegal: CharPredicate) extends AnyVal

object PercentEncoding {
  implicit val userInfo: PercentEncoding[UserInfo] = PercentEncoding(CP.UserInfo)
  implicit val regName: PercentEncoding[RegName] = PercentEncoding(CP.UserInfo)

  implicit val fragment: PercentEncoding[Fragment] = PercentEncoding(CP.Fragment)
  implicit val pathSegment: PercentEncoding[PathSegment] = PercentEncoding(CP.Pchar)
  implicit val pathSegmentNzNc: PercentEncoding[PathSegmentNzNc] = PercentEncoding(CP.SegmentNzNc)

  implicit val queryString: PercentEncoding[UriQuery] = PercentEncoding(CP.Query)
  implicit val formPreQueryParam: PercentEncoding[FormQueryPreEncode] = PercentEncoding(UriCodingUtils.FormQueryParam)
}

package org.http4s.util.encoding

object tags {
  // if an A is a B, then an Encoded[A] can be used in place of an Encoded[B]
  // , then an Encoded[PathSegmentNc] can be used in place of an Encoded[PathSegment]

  trait UserInfo
  trait RegName
  trait IdnaRegName // https://tools.ietf.org/html/rfc3986#section-3.2.2
  trait Path // SegmentNc ( '/' PathSegment )*
  trait PathSegment
  trait PathSegmentNzNc
  trait Fragment


  trait UriQuery // aka QueryString
  trait FormQuery
  trait FormQueryParam
  trait FormQueryKey
  trait FormQueryKV

  private[encoding] trait FormQueryPreEncode
}

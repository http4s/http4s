package org.http4s.util

import org.http4s.ParseResult
import org.http4s.parser.QueryParser
import org.http4s.util.encoding.UriCodingUtils

import collection.immutable.BitSet
import scala.io.Codec


@deprecated("Use UriCodingUtils", since="0.13")
object UrlFormCodec {
  def decode(formData: String, codec: Codec = Codec.UTF8): ParseResult[Map[String, Seq[String]]] =
    QueryParser.parseQueryString(formData.replace("+", "%20"), codec).map(_.asForm.multiParams)

  @deprecated("use UriCodingUtils.encodeQueryMap", since="0.13")
  def encode(formData: Map[String,Seq[String]]): String =
    UriCodingUtils.encodeQueryMap(formData).encoded

  val urlUnreserved = BitSet((('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-_.~".toSet).map(_.toInt): _*)
}

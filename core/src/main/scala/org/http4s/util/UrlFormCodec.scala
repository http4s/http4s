package org.http4s.util

import org.http4s.ParseResult
import org.http4s.parser.QueryParser
import rl.UrlCodingUtils

import collection.immutable.BitSet
import scala.io.Codec


object UrlFormCodec {
  def decode(formData: String, codec: Codec = Codec.UTF8): ParseResult[Map[String, Seq[String]]] =
    QueryParser.parseQueryString(formData.replace("+", "%20"), codec).map(convert)

  def encode(formData: Map[String,Seq[String]]): String = {
    val sb = new StringBuilder(formData.size * 20)
    formData.foreach { case (k, vs) =>
      if (!sb.isEmpty) sb.append('&')

      if (vs.isEmpty) sb.append(formEncode(k))
      else vs.foreach { v =>
        sb.append(formEncode(k))
          .append('=')
          .append(formEncode(v))
      }
    }

    sb.result()
  }

  private def formEncode(s: String): String =
    UrlCodingUtils.urlEncode(s, spaceIsPlus = true, toSkip = urlReserved)
  
  private def convert(seq: Seq[(String,Option[String])]): Map[String,Seq[String]] =
    seq.groupBy(_._1).mapValues(_.flatMap(_._2))

  private val urlReserved = BitSet((('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-_.~".toSet).map(_.toInt): _*)
}

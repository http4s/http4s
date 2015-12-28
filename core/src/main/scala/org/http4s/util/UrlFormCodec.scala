package org.http4s.util

import org.http4s.ParseResult
import org.http4s.parser.QueryParser

import collection.immutable.BitSet
import scala.io.Codec


object UrlFormCodec {
  def decode(formData: String, codec: Codec = Codec.UTF8): ParseResult[Map[String, Seq[String]]] =
    QueryParser.parseQueryString(formData.replace("+", "%20"), codec).map(_.multiParams)

  def encode(formData: Map[String,Seq[String]]): String = {
    val sb = new StringBuilder(formData.size * 20)
    formData.foreach { case (k, vs) =>
      if (sb.nonEmpty) sb.append('&')

      if (vs.isEmpty) sb.append(formEncode(k))
      else {
        def addKvPair(v: String): Unit = {
          sb.append(formEncode(k))
            .append('=')
            .append(formEncode(v))
        }

        addKvPair(vs.head)
        vs.tail.foreach { v =>
          sb.append('&')
          addKvPair(v)
        }
      }
    }

    sb.result()
  }

  private def formEncode(s: String): String =
    UrlCodingUtils.urlEncode(s, spaceIsPlus = true, toSkip = urlUnreserved)

  val urlUnreserved = BitSet((('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-_.~".toSet).map(_.toInt): _*)
}

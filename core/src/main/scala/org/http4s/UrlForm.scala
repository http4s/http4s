package org.http4s

import org.http4s.Header.`Content-Type`
import org.http4s.parser.QueryParser
import org.http4s.util.UrlCodingUtils

import scala.collection.immutable.BitSet
import scala.io.Codec


class UrlForm(val values: Map[String, Seq[String]]) extends AnyVal {
  override def toString: String = values.toString()
}

object UrlForm {

  def apply(values: Map[String, Seq[String]]): UrlForm =
    // value "" -> Seq() is just noise and it is not maintain during encoding round trip
    if(values.get("").fold(false)(_.isEmpty)) new UrlForm(values - "")
    else new UrlForm(values)

  def entityEncoder(charset: Charset): EntityEncoder[UrlForm] =
    EntityEncoder.stringEncoder(charset)
      .contramap[UrlForm](encodeString(charset))
      .withContentType(`Content-Type`(MediaType.`application/x-www-form-urlencoded`, charset))


  implicit val entityDecoder: EntityDecoder[UrlForm] =
    EntityDecoder.decodeBy(MediaType.`application/x-www-form-urlencoded`){ m =>
      DecodeResult(
        EntityDecoder.decodeString(m)
          .map(decodeString(m.charset))
      )
    }

  private[http4s] def decodeString(charset: Charset)(urlForm: String): ParseResult[UrlForm] =
    QueryParser.parseQueryString(urlForm.replace("+", "%20"), new Codec(charset.nioCharset))
      .map(_.groupBy(_._1).mapValues(_.flatMap(_._2)))
      .map(UrlForm.apply)

  private[http4s] def encodeString(charset: Charset)(urlForm: UrlForm): String = {
    def encode(s: String): String =
      UrlCodingUtils.urlEncode(s, charset, spaceIsPlus = true, toSkip = urlReserved)

    val sb = new StringBuilder(urlForm.values.size * 20)
    urlForm.values.foreach { case (k, vs) =>
      if (sb.nonEmpty) sb.append('&')
      val encodedKey = encode(k)
      if (vs.isEmpty) sb.append(encodedKey)
      else {
        var first = true
        vs.foreach { v =>
          if(!first) sb.append('&')
          else first = false
          sb.append(encodedKey)
            .append('=')
            .append(encode(v))
        }
      }
    }
    sb.result()
  }

  private val urlReserved: BitSet =
    BitSet((('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-_.~".toSet).map(_.toInt): _*)
}
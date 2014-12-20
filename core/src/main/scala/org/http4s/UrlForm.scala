package org.http4s

import org.http4s.Header.`Content-Type`
import org.http4s.parser.QueryParser
import org.http4s.util.UrlCodingUtils

import scala.collection.immutable.BitSet
import scala.io.Codec


final case class UrlForm(values: Map[String, Seq[String]]) extends AnyVal

object UrlForm {

  def entityEncoder(charset: Charset): EntityEncoder[UrlForm] =
    EntityEncoder.stringEncoder(charset)
      .contramap[UrlForm](urlFormEncode)
      .withContentType(`Content-Type`(MediaType.`application/x-www-form-urlencoded`))

  def entityDecoder(charset: Charset): EntityDecoder[UrlForm] =
    EntityDecoder.decodeBy(MediaType.`application/x-www-form-urlencoded`){ m =>
      DecodeResult(
        EntityDecoder.decodeString(m).map { urlForm =>
          QueryParser.parseQueryString(urlForm.replace("+", "%20"), new Codec(charset.nioCharset))
            .map(_.groupBy(_._1).mapValues(_.flatMap(_._2)))
            .map(UrlForm.apply)
        }
      )
    }

  private def urlFormEncode(urlForm: UrlForm): String = {
    def encode(s: String): String =
      UrlCodingUtils.urlEncode(s, spaceIsPlus = true, toSkip = urlReserved)

    val sb = new StringBuilder(urlForm.values.size * 20)
    urlForm.values.foreach { case (k, vs) =>
      if (sb.nonEmpty) sb.append('&')

      if (vs.isEmpty) sb.append(encode(k))
      else vs.foreach { v =>
        sb.append(encode(k))
          .append('=')
          .append(encode(v))
      }
    }
    sb.result()
  }

  private val urlReserved: BitSet =
    BitSet((('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-_.~".toSet).map(_.toInt): _*)
}
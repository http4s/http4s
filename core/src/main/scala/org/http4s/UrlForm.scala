package org.http4s

import org.http4s.headers.`Content-Type`
import org.http4s.parser.QueryParser
import org.http4s.util.{UrlFormCodec, UrlCodingUtils}

import scala.io.Codec


class UrlForm(val values: Map[String, Seq[String]]) extends AnyVal {
  override def toString: String = values.toString()

  def get(key: String): Seq[String] =
    this.getOrElse(key, Seq.empty[String])

  def getOrElse(key: String, default: => Seq[String]): Seq[String] =
    values.get(key).getOrElse(default)

  def getFirst(key: String): Option[String] =
    values.get(key).flatMap(_.headOption)

  def getFirstOrElse(key: String, default: => String): String =
    this.getFirst(key).getOrElse(default)
}

object UrlForm {

  val empty: UrlForm = new UrlForm(Map.empty)

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
          .map(decodeString(m.charset.getOrElse(Charset.`ISO-8859-1`)))
      )
    }

  /** Attempt to decode the `String` to a [[UrlForm]] */
  def decodeString(charset: Charset)(urlForm: String): ParseResult[UrlForm] =
    QueryParser.parseQueryString(urlForm.replace("+", "%20"), new Codec(charset.nioCharset))
      .map(q => UrlForm(q.multiParams))

  /** Encode the [[UrlForm]] into a `String` using the provided `Charset` */
  def encodeString(charset: Charset)(urlForm: UrlForm): String = {
    def encode(s: String): String =
      UrlCodingUtils.urlEncode(s, charset.nioCharset, spaceIsPlus = true, toSkip = UrlFormCodec.urlUnreserved)

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
}

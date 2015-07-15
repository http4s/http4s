package org.http4s

import org.http4s.headers.`Content-Type`
import org.http4s.parser.QueryParser
import org.http4s.util.{UrlFormCodec, UrlCodingUtils}

import scala.collection.{GenTraversableOnce, MapLike}
import scala.io.Codec


class UrlForm private (val values: Map[String, Seq[String]]) extends AnyVal {
  override def toString: String = values.toString()

  def get(key: String): Seq[String] =
    this.getOrElse(key, Seq.empty[String])

  def getOrElse(key: String, default: => Seq[String]): Seq[String] =
    values.get(key).getOrElse(default)

  def getFirst(key: String): Option[String] =
    values.get(key).flatMap(_.headOption)

  def getFirstOrElse(key: String, default: => String): String =
    this.getFirst(key).getOrElse(default)

  def +(kv: (String, String)): UrlForm = {
    val newValues = values.get(kv._1).fold(Seq(kv._2))(_ :+ kv._2)
    UrlForm(values.updated(kv._1, newValues))
  }
}

object UrlForm {

  val empty: UrlForm = new UrlForm(Map.empty)

  def apply(values: Map[String, Seq[String]]): UrlForm =
    // value "" -> Seq() is just noise and it is not maintain during encoding round trip
    if(values.get("").fold(false)(_.isEmpty)) new UrlForm(values - "")
    else new UrlForm(values)

  def apply(values: (String, String)*): UrlForm =
    values.foldLeft(empty)(_ + _)

  implicit def entityEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[UrlForm] =
    EntityEncoder.stringEncoder(charset)
      .contramap[UrlForm](encodeString(charset))
      .withContentType(`Content-Type`(MediaType.`application/x-www-form-urlencoded`, charset))

  implicit def entityDecoder(implicit defaultCharset: Charset = DefaultCharset): EntityDecoder[UrlForm] =
    EntityDecoder.decodeBy(MediaType.`application/x-www-form-urlencoded`){ m =>
      DecodeResult(
        EntityDecoder.decodeString(m)
          .map(decodeString(m.charset.getOrElse(defaultCharset)))
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

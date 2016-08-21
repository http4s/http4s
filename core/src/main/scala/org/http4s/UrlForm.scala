package org.http4s

import scala.io.Codec

import cats._
import cats.data._
import org.http4s.batteries._
import org.http4s.headers._
import org.http4s.parser._
import org.http4s.util._

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

  /**
    * @param key name of the field
    * @param value value of the field
    * @param ev evidence of the existence of `QueryParamEncoder[T]`
    * @return `UrlForm` updated with `key` and `value` pair if key does not exist in `values`. Otherwise `value` will be added to the existing entry.
    */
  def updateFormField[T](key: String, value: T)(implicit ev: QueryParamEncoder[T]): UrlForm =
    this + (key -> ev.encode(value).value)

  /**
    * @param key name of the field
    * @param value optional value of the field
    * @param ev evidence of the existence of `QueryParamEncoder[T]`
    * @return `UrlForm` updated as it is updated with `updateFormField(key, v)` if `value` is `Some(v)`, otherwise it is unaltered
    */
  def updateFormField[T](key: String, value: Option[T])(implicit ev: QueryParamEncoder[T]): UrlForm =
    value.fold(this)(updateFormField(key, _))

  /**
    * @param key name of the field
    * @param vals a sequence of values for the field
    * @param ev evidence of the existence of `QueryParamEncoder[T]`
    * @return `UrlForm` updated with `key` and `vals` if key does not exist in `values`, otherwise `vals` will be appended to the existing entry. If `vals` is empty, `UrlForm` will remain as is
    */
  def updateFormFields[T](key: String, vals: Seq[T])(implicit ev: QueryParamEncoder[T]): UrlForm =
    vals.foldLeft(this)(_.updateFormField(key, _)(ev))

  /* same as `updateFormField(key, value)` */
  def +?[T : QueryParamEncoder](key: String, value: T): UrlForm =
    updateFormField(key, value)

  /* same as `updateParamEncoder`(key, value) */
  def +?[T : QueryParamEncoder](key: String, value: Option[T]): UrlForm =
    updateFormField(key, value)

  /* same as `updatedParamEncoders`(key, vals) */
  def ++?[T : QueryParamEncoder](key: String, vals: Seq[T]): UrlForm =
    updateFormFields(key, vals)
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

  implicit val eqInstance: Eq[UrlForm] = new Eq[UrlForm] {
    def eqv(x: UrlForm, y: UrlForm): Boolean =
      x.values.mapValues(_.toList).view.force === y.values.mapValues(_.toList).view.force
  }

  /** Attempt to decode the `String` to a [[UrlForm]] */
  def decodeString(charset: Charset)(urlForm: String): Either[MalformedMessageBodyFailure, UrlForm] =
    QueryParser.parseQueryString(urlForm.replace("+", "%20"), new Codec(charset.nioCharset))
      .map(q => UrlForm(q.multiParams))
      .leftMap { parseFailure => MalformedMessageBodyFailure(parseFailure.message, None) }

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

package org.http4s

import cats._
import cats.data._
import cats.effect.Sync
import cats.implicits.{catsSyntaxEither => _, _}
import org.http4s.headers._
import org.http4s.parser._
import org.http4s.util._
import scala.io.Codec

class UrlForm private (val values: Map[String, Chain[String]]) extends AnyVal {
  override def toString: String = values.toString()

  def get(key: String): Chain[String] =
    this.getOrElse(key, Chain.empty[String])

  def getOrElse(key: String, default: => Chain[String]): Chain[String] =
    values.getOrElse(key, default)

  def getFirst(key: String): Option[String] =
    values.get(key).flatMap(_.uncons).map { case (s, _) => s }

  def getFirstOrElse(key: String, default: => String): String =
    this.getFirst(key).getOrElse(default)

  def +(kv: (String, String)): UrlForm = {
    val newValues = values.get(kv._1).fold(Chain(kv._2))(_ :+ kv._2)
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
  def updateFormField[T](key: String, value: Option[T])(
      implicit ev: QueryParamEncoder[T]): UrlForm =
    value.fold(this)(updateFormField(key, _))

  /**
    * @param key name of the field
    * @param vals a Chain of values for the field
    * @param ev evidence of the existence of `QueryParamEncoder[T]`
    * @return `UrlForm` updated with `key` and `vals` if key does not exist in `values`, otherwise `vals` will be appended to the existing entry. If `vals` is empty, `UrlForm` will remain as is
    */
  def updateFormFields[T](key: String, vals: Chain[T])(implicit ev: QueryParamEncoder[T]): UrlForm =
    vals.foldLeft(this)(_.updateFormField(key, _)(ev))

  /* same as `updateFormField(key, value)` */
  def +?[T: QueryParamEncoder](key: String, value: T): UrlForm =
    updateFormField(key, value)

  /* same as `updateParamEncoder`(key, value) */
  def +?[T: QueryParamEncoder](key: String, value: Option[T]): UrlForm =
    updateFormField(key, value)

  /* same as `updatedParamEncoders`(key, vals) */
  def ++?[T: QueryParamEncoder](key: String, vals: Chain[T]): UrlForm =
    updateFormFields(key, vals)
}

object UrlForm {

  val empty: UrlForm = new UrlForm(Map.empty)

  def apply(values: Map[String, Chain[String]]): UrlForm =
    // value "" -> Chain() is just noise and it is not maintain during encoding round trip
    if (values.get("").fold(false)(_.isEmpty)) new UrlForm(values - "")
    else new UrlForm(values)

  def apply(values: (String, String)*): UrlForm =
    values.foldLeft(empty)(_ + _)

  def fromChain(values: Chain[(String, String)]): UrlForm =
    apply(values.toList: _*)

  implicit def entityEncoder[F[_]](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, UrlForm] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[UrlForm](encodeString(charset))
      .withContentType(`Content-Type`(MediaType.application.`x-www-form-urlencoded`, charset))

  implicit def entityDecoder[F[_]](
      implicit F: Sync[F],
      defaultCharset: Charset = DefaultCharset): EntityDecoder[F, UrlForm] =
    EntityDecoder.decodeBy(MediaType.application.`x-www-form-urlencoded`) { m =>
      DecodeResult(
        EntityDecoder
          .decodeString(m)
          .map(decodeString(m.charset.getOrElse(defaultCharset)))
      )
    }

  implicit val eqInstance: Eq[UrlForm] = Eq.instance { (x: UrlForm, y: UrlForm) =>
    x.values.mapValues(_.toList).view.force === y.values.mapValues(_.toList).view.force
  }

  implicit val monoidInstance: Monoid[UrlForm] = new Monoid[UrlForm] {
    override def empty: UrlForm = UrlForm.empty

    override def combine(x: UrlForm, y: UrlForm): UrlForm =
      UrlForm(x.values |+| y.values)
  }

  /** Attempt to decode the `String` to a [[UrlForm]] */
  def decodeString(charset: Charset)(
      urlForm: String): Either[MalformedMessageBodyFailure, UrlForm] =
    QueryParser
      .parseQueryString(urlForm.replace("+", "%20"), new Codec(charset.nioCharset))
      .map(q => UrlForm(q.multiParams.mapValues(Chain.fromSeq)))
      .leftMap { parseFailure =>
        MalformedMessageBodyFailure(parseFailure.message, None)
      }

  /** Encode the [[UrlForm]] into a `String` using the provided `Charset` */
  def encodeString(charset: Charset)(urlForm: UrlForm): String = {
    def encode(s: String): String =
      UrlCodingUtils.urlEncode(
        s,
        charset.nioCharset,
        spaceIsPlus = true,
        toSkip = UrlCodingUtils.Unreserved)

    val sb = new StringBuilder(urlForm.values.size * 20)
    urlForm.values.foreach {
      case (k, vs) =>
        if (sb.nonEmpty) sb.append('&')
        val encodedKey = encode(k)
        if (vs.isEmpty) sb.append(encodedKey)
        else {
          var first = true
          vs.map { v =>
            if (!first) sb.append('&')
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

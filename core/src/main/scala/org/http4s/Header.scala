package org.http4s

import org.joda.time.DateTime
import java.net.InetAddress
import com.typesafe.scalalogging.slf4j.Logging
import scalaz.NonEmptyList

import org.http4s.util.{Writer, CaseInsensitiveString, Renderable}
import org.http4s.util.string._
import org.http4s.util.jodaTime._
import org.http4s.CharacterSet._
import scala.util.hashing.MurmurHash3


/** Abstract representation o the HTTP header
  * @see [[HeaderKey]]
   */
sealed trait Header extends Logging with Renderable with Product {

  import org.http4s.Header.RawHeader

  def name: CaseInsensitiveString

  def parsed: Header

  def is(key: HeaderKey): Boolean = key.matchHeader(this).isDefined

  def isNot(key: HeaderKey): Boolean = !is(key)

  override def toString = name + ": " + value

  def raw: RawHeader = RawHeader(name, value)

  final override def hashCode(): Int = MurmurHash3.mixLast(name.hashCode, MurmurHash3.productHash(parsed))

  override def equals(that: Any): Boolean = that match {
    case h: AnyRef if this eq h => true
    case h: Header =>
      (name == h.name) &&
      (parsed.productArity == h.parsed.productArity) &&
      (parsed.productIterator sameElements h.parsed.productIterator)
    case _ => false
  }
}

/** A Header that is already parsed from its String representation. */
trait ParsedHeader extends Header {
  def key: HeaderKey
  def name = key.name
  def parsed: this.type = this
}

/**
 * A recurring header that satisfies this clause of the Spec:
 *
 * Multiple message-header fields with the same field-name MAY be present in a message if and only if the entire
 * field-value for that header field is defined as a comma-separated list [i.e., #(values)]. It MUST be possible
 * to combine the multiple header fields into one "field-name: field-value" pair, without changing the semantics
 * of the message, by appending each subsequent field-value to the first, each separated by a comma.
 */
trait RecurringHeader extends ParsedHeader {
  type Value
  def values: NonEmptyList[Value]

}

trait RecurringRenderableHeader extends RecurringHeader {
  type Value <: Renderable
  def render[W <: Writer](writer: W) = {
    values.head.render(writer)
    values.tail.foreach( writer ~ ", " ~ _ )
    writer
  }
}

object Header {
  def unapply(header: Header): Option[(CaseInsensitiveString, String)] = Some((header.name, header.value))

  def apply(name: String, value: String): RawHeader = RawHeader(name.ci, value)

  final case class RawHeader private[Header] (name: CaseInsensitiveString, override val value: String) extends Header {
    override lazy val parsed = parser.HttpParser.parseHeader(this).getOrElse(this)
    def render[W <: Writer](writer: W) = writer.append(value)
  }

  object Accept extends InternalHeaderKey[Accept] with RecurringHeaderKey
  final case class Accept(values: NonEmptyList[MediaRange]) extends RecurringRenderableHeader {
    def key = Accept
    type Value = MediaRange
  }

  object `Accept-Charset` extends InternalHeaderKey[`Accept-Charset`] with RecurringHeaderKey
  final case class `Accept-Charset`(values: NonEmptyList[CharacterSet]) extends RecurringRenderableHeader {
    def key = `Accept-Charset`
    type Value = CharacterSet
    def preferred: CharacterSet = values.tail.fold(values.head)((a, b) => if (a.q >= b.q) a else b)
    def satisfiedBy(characterSet: CharacterSet) = values.list.find(_.satisfiedBy(characterSet)).isDefined
  }

  object `Accept-Encoding` extends InternalHeaderKey[`Accept-Encoding`] with RecurringHeaderKey
  final case class `Accept-Encoding`(values: NonEmptyList[ContentCoding]) extends RecurringRenderableHeader {
    def key = `Accept-Encoding`
    type Value = ContentCoding
    def preferred: ContentCoding = values.tail.fold(values.head)((a, b) => if (a.q >= b.q) a else b)
    def satisfiedBy(coding: ContentCoding): Boolean = values.list.find(_.satisfiedBy(coding)).isDefined
  }

  object `Accept-Language` extends InternalHeaderKey[`Accept-Language`] with RecurringHeaderKey
  final case class `Accept-Language`(values: NonEmptyList[LanguageTag]) extends RecurringRenderableHeader {
    def key = `Accept-Language`
    type Value = LanguageTag
    def preferred: LanguageTag = values.tail.fold(values.head)((a, b) => if (a.q >= b.q) a else b)
    def satisfiedBy(languageTag: LanguageTag) = values.list.find(_.satisfiedBy(languageTag)).isDefined
  }

  // TODO Interpreting this as not a recurring header, because of "none".
  object `Accept-Ranges` extends InternalHeaderKey[`Accept-Ranges`] with SingletonHeaderKey {
    def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more)
    def bytes = apply(RangeUnit.bytes)
    def none = apply(Nil)
  }
  final case class `Accept-Ranges` private[http4s] (rangeUnits: Seq[RangeUnit]) extends ParsedHeader {
    def key = `Accept-Ranges`
    def render[W <: Writer](writer: W) = {
      if (rangeUnits.isEmpty) writer.append("none")
      else {
        writer.append(rangeUnits.head)
        rangeUnits.tail.foreach(r => writer.append(", ").append(r))
        writer
      }
    }
  }

  object `Accept-Patch` extends DefaultHeaderKey

  object `Access-Control-Allow-Credentials` extends DefaultHeaderKey

  object `Access-Control-Allow-Headers` extends DefaultHeaderKey

  object `Access-Control-Allow-Methods` extends DefaultHeaderKey

  object `Access-Control-Allow-Origin` extends DefaultHeaderKey

  object `Access-Control-Expose-Headers` extends DefaultHeaderKey

  object `Access-Control-Max-Age` extends DefaultHeaderKey

  object `Access-Control-Request-Headers` extends DefaultHeaderKey

  object `Access-Control-Request-Method` extends DefaultHeaderKey

  object Age extends DefaultHeaderKey

  object Allow extends DefaultHeaderKey

  object Authorization extends InternalHeaderKey[Authorization] with SingletonHeaderKey
  final case class Authorization(credentials: Credentials) extends ParsedHeader {
    def key = `Authorization`
    def render[W <: Writer](writer: W) = credentials.render(writer)
  }

  object `Cache-Control` extends InternalHeaderKey[`Cache-Control`] with RecurringHeaderKey
  final case class `Cache-Control`(values: NonEmptyList[CacheDirective]) extends RecurringRenderableHeader {
    def key = `Cache-Control`
    type Value = CacheDirective
  }

  object Connection extends InternalHeaderKey[Connection] with RecurringHeaderKey
  final case class Connection(values: NonEmptyList[String]) extends RecurringHeader {
    def key = Connection
    type Value = String
    def hasClose = values.list.exists(_.equalsIgnoreCase("close"))
    def hasKeepAlive = values.list.exists(_.equalsIgnoreCase("keep-alive"))
    def render[W <: Writer](writer: W) = writer.addStrings(values.list, ", ")
  }

  object `Content-Base` extends DefaultHeaderKey

  object `Content-Disposition` extends InternalHeaderKey[`Content-Disposition`] with SingletonHeaderKey
  // see http://tools.ietf.org/html/rfc2183
  final case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String]) extends ParsedHeader {
    def key = `Content-Disposition`
    override lazy val value = super.value
    def render[W <: Writer](writer: W) = {
      writer.append(dispositionType)
      parameters.foreach(p =>  writer ~ "; "~ p._1~ "=\""~ p._2 ~ '"')
      writer
    }
  }

  object `Content-Encoding` extends InternalHeaderKey[`Content-Encoding`] with SingletonHeaderKey
  final case class `Content-Encoding`(contentCoding: ContentCoding) extends ParsedHeader {
    def key = `Content-Encoding`
    def render[W <: Writer](writer: W) = contentCoding.render(writer)
  }

  object `Content-Language` extends DefaultHeaderKey

  object `Content-Length` extends InternalHeaderKey[`Content-Length`] with SingletonHeaderKey
  final case class `Content-Length`(length: Int) extends ParsedHeader {
    def key = `Content-Length`
    def render[W <: Writer](writer: W) = writer.append(length)
  }

  object `Content-Location` extends DefaultHeaderKey

  object `Content-Transfer-Encoding` extends DefaultHeaderKey

  object `Content-MD5` extends DefaultHeaderKey

  object `Content-Range` extends DefaultHeaderKey

  object `Content-Type` extends InternalHeaderKey[`Content-Type`] with SingletonHeaderKey {
    val `text/plain` = `Content-Type`(MediaType.`text/plain`)
    val `application/octet-stream` = `Content-Type`(MediaType.`application/octet-stream`)

    // RFC4627 defines JSON to always be UTF encoded, we always render JSON to UTF-8
    val `application/json` = `Content-Type`(MediaType.`application/json`, `UTF-8`)

    def apply(mediaType: MediaType, charset: CharacterSet): `Content-Type` = apply(mediaType, Some(charset))
    implicit def apply(mediaType: MediaType): `Content-Type` = apply(mediaType, None)
  }

  final case class `Content-Type`(mediaType: MediaType, definedCharset: Option[CharacterSet]) extends ParsedHeader {
    def key = `Content-Type`
    def render[W <: Writer](writer: W) = definedCharset match {
      case Some(cs) => writer ~ mediaType ~ "; charset=" ~ cs
      case _        => mediaType.render(writer)
    }

    def withMediaType(mediaType: MediaType) =
      if (mediaType != this.mediaType) copy(mediaType = mediaType) else this
    def withCharset(charset: CharacterSet) =
      if (noCharsetDefined || charset != definedCharset.get) copy(definedCharset = Some(charset)) else this
    def withoutDefinedCharset =
      if (isCharsetDefined) copy(definedCharset = None) else this

    def isCharsetDefined = definedCharset.isDefined
    def noCharsetDefined = definedCharset.isEmpty

    def charset: CharacterSet = definedCharset.getOrElse(`ISO-8859-1`)
  }

  object Cookie extends InternalHeaderKey[Cookie] with RecurringHeaderKey
  final case class Cookie(values: NonEmptyList[org.http4s.Cookie]) extends RecurringRenderableHeader {
    def key = Cookie
    type Value = org.http4s.Cookie
    override def render[W <: Writer](writer: W) = {
      values.head.render(writer)
      values.tail.foreach( writer ~ "; "~ _ )
      writer
    }
  }

  object Date extends InternalHeaderKey[Date] with SingletonHeaderKey
  final case class Date(date: DateTime) extends ParsedHeader {
    def key = `Date`
    override def value = date.formatRfc1123
    def render[W <: Writer](writer: W) = writer.append(value)
  }

  object ETag extends InternalHeaderKey[ETag] with SingletonHeaderKey
  final case class ETag(tag: String) extends ParsedHeader {
    def key: HeaderKey = ETag
    override def value: String = tag
    def render[W <: Writer](writer: W) = writer.append(tag)
  }

  object Expect extends DefaultHeaderKey

  object Expires extends DefaultHeaderKey

  object From extends DefaultHeaderKey

  object `Front-End-Https` extends DefaultHeaderKey

  object Host extends InternalHeaderKey[Host] with SingletonHeaderKey {
    def apply(host: String, port: Int): Host = apply(host, Some(port))
  }
  final case class Host(host: String, port: Option[Int] = None) extends ParsedHeader {
    def key = `Host`
    def render[W <: Writer](writer: W) = {
      writer.append(host)
      if (port.isDefined) writer ~ ':' ~ port.get
      writer
    }
  }

  object `If-Match` extends DefaultHeaderKey

  object `If-Modified-Since` extends InternalHeaderKey[`If-Modified-Since`] with SingletonHeaderKey
  final case class `If-Modified-Since`(date: DateTime) extends ParsedHeader {
    def key: HeaderKey = `If-Modified-Since`
    override def value: String = date.formatRfc1123
    def render[W <: Writer](writer: W) = writer.append(value)
  }

  object `If-None-Match` extends InternalHeaderKey[`If-None-Match`] with SingletonHeaderKey
  case class `If-None-Match`(tag: String) extends ParsedHeader {
    def key: HeaderKey = `If-None-Match`
    override def value: String = tag
    def render[W <: Writer](writer: W) = writer.append(tag)
  }

  object `If-Range` extends DefaultHeaderKey

  object `If-Unmodified-Since` extends DefaultHeaderKey

  object `Last-Modified` extends InternalHeaderKey[`Last-Modified`] with SingletonHeaderKey
  final case class `Last-Modified`(date: DateTime) extends ParsedHeader {
    def key = `Last-Modified`
    override def value = date.formatRfc1123
    def render[W <: Writer](writer: W) = writer.append(value)
  }

  object Location extends InternalHeaderKey[Location] with SingletonHeaderKey

  final case class Location(absoluteUri: String) extends ParsedHeader {
    def key = `Location`
    override def value = absoluteUri
    def render[W <: Writer](writer: W) = writer.append(absoluteUri)
  }

  object `Max-Forwards` extends DefaultHeaderKey

  object Origin extends DefaultHeaderKey

  object Pragma extends DefaultHeaderKey

  object `Proxy-Authenticate` extends DefaultHeaderKey

  object `Proxy-Authorization` extends DefaultHeaderKey

  object Range extends DefaultHeaderKey

  object Referer extends DefaultHeaderKey

  object `Retry-After` extends DefaultHeaderKey

  object `Sec-WebSocket-Key` extends DefaultHeaderKey

  object `Sec-WebSocket-Key1` extends DefaultHeaderKey

  object `Sec-WebSocket-Key2` extends DefaultHeaderKey

  object `Sec-WebSocket-Location` extends DefaultHeaderKey

  object `Sec-WebSocket-Origin` extends DefaultHeaderKey

  object `Sec-WebSocket-Protocol` extends DefaultHeaderKey

  object `Sec-WebSocket-Version` extends DefaultHeaderKey

  object `Sec-WebSocket-Accept` extends DefaultHeaderKey

  object Server extends DefaultHeaderKey

  object `Set-Cookie` extends InternalHeaderKey[`Set-Cookie`] with SingletonHeaderKey
  final case class `Set-Cookie`(cookie: org.http4s.Cookie) extends ParsedHeader {
    def key = `Set-Cookie`
    def render[W <: Writer](writer: W) = writer.append(cookie)
  }

  object `TE` extends DefaultHeaderKey

  object `Trailer` extends DefaultHeaderKey

  object `Transfer-Encoding` extends InternalHeaderKey[`Transfer-Encoding`] with RecurringHeaderKey
  final case class `Transfer-Encoding`(values: NonEmptyList[TransferCoding]) extends RecurringRenderableHeader {
    def key = `Transfer-Encoding`
    def hasChunked = values.list.exists(_.value.equalsIgnoreCase("chunked"))
    type Value = TransferCoding
  }

  object Upgrade extends DefaultHeaderKey

  object `User-Agent` extends DefaultHeaderKey

  object Vary extends DefaultHeaderKey

  object Via extends DefaultHeaderKey

  object Warning extends DefaultHeaderKey

  object `WebSocket-Location` extends DefaultHeaderKey

  object `WebSocket-Origin` extends DefaultHeaderKey

  object `WebSocket-Protocol` extends DefaultHeaderKey

  object `WWW-Authenticate` extends InternalHeaderKey[`WWW-Authenticate`] with RecurringHeaderKey
  final case class `WWW-Authenticate`(values: NonEmptyList[Challenge]) extends RecurringRenderableHeader {
    def key = `WWW-Authenticate`
    type Value = Challenge
  }

  object `X-Forwarded-For` extends InternalHeaderKey[`X-Forwarded-For`] with RecurringHeaderKey
  final case class `X-Forwarded-For`(values: NonEmptyList[Option[InetAddress]]) extends RecurringHeader {
    def key = `X-Forwarded-For`
    type Value = Option[InetAddress]
    override lazy val value = super.value

    def render[W <: Writer](writer: W) = {
      values.head.fold(writer.append("unknown"))(i => writer.append(i.getHostAddress))
      values.tail.foreach(append(writer, _))
      writer
    }

    @inline
    private def append(sb: Writer, add: Option[InetAddress]): Unit = {
      sb.append(", ")
      if (add.isDefined) sb.append(add.get.getHostAddress)
      else sb.append("unknown")
    }
  }

  object `X-Forwarded-Proto` extends DefaultHeaderKey

  object `X-Powered-By` extends DefaultHeaderKey
}
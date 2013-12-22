package org.http4s

import parserold.HttpParser
import org.joda.time.DateTime
import java.net.InetAddress
import scala.reflect.ClassTag
import com.typesafe.scalalogging.slf4j.Logging
import scalaz.NonEmptyList
import scala.annotation.tailrec
import scala.util.hashing.MurmurHash3
import org.http4s.util.CaseInsensitiveString

sealed trait Header extends Logging with Product {

  def name: CaseInsensitiveString

  def value: String

  def is(key: HeaderKey): Boolean = key.matchHeader(this).isDefined

  def isNot(key: HeaderKey): Boolean = !is(key)

  override def toString = name + ": " + value

  def parsed: Header

  final override def hashCode(): Int = MurmurHash3.mixLast(name.hashCode, MurmurHash3.productHash(parsed))

  override def equals(that: Any): Boolean = that match {
    case h: AnyRef if this eq h => true
    case h: Header => (name == h.name) &&
      (parsed.productArity == h.parsed.productArity) &&
      (parsed.productIterator sameElements h.parsed.productIterator)
    case _ => false
  }
}

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
  def value: String = values.list.mkString(", ")
}

object Header {
  def unapply(header: Header): Option[(CaseInsensitiveString, String)] = Some((header.name, header.value))

  def apply(name: String, value: String): Header = RawHeader(name.ci, value)

  final case class RawHeader private[Header] (name: CaseInsensitiveString, value: String) extends Header {
    override lazy val parsed = HttpParser.parseHeader(this).fold(_ => this, identity)
  }

  object Accept extends InternalHeaderKey[Accept] with RecurringHeaderKey
  final case class Accept(values: NonEmptyList[MediaRange]) extends RecurringHeader {
    def key = Accept
    type Value = MediaRange
  }

  object `Accept-Charset` extends InternalHeaderKey[`Accept-Charset`] with RecurringHeaderKey
  final case class `Accept-Charset`(values: NonEmptyList[CharacterSetRange]) extends RecurringHeader {
    def key = `Accept-Charset`
    type Value = CharacterSetRange
  }

  object `Accept-Encoding` extends InternalHeaderKey[`Accept-Encoding`] with RecurringHeaderKey
  final case class `Accept-Encoding`(values: NonEmptyList[ContentCodingRange]) extends RecurringHeader {
    def key = `Accept-Encoding`
    type Value = ContentCodingRange

    def acceptsEncoding(coding: ContentCoding): Boolean = {
      @tailrec
      def go(c: ContentCodingRange, rest: List[ContentCodingRange]): Boolean = {
        if (c.matches(coding)) true
        else if (rest.isEmpty) false
        else go(rest.head, rest.tail)
      }
      go(values.head, values.tail)
    }
  }

  object `Accept-Language` extends InternalHeaderKey[`Accept-Language`] with RecurringHeaderKey
  final case class `Accept-Language`(values: NonEmptyList[LanguageRange]) extends RecurringHeader {
    def key = `Accept-Language`
    type Value = LanguageRange
  }

  // TODO Interpreting this as not a recurring header, because of "none".
  object `Accept-Ranges` extends InternalHeaderKey[`Accept-Ranges`] with SingletonHeaderKey {
    def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more)
  }
  final case class `Accept-Ranges` private[http4s] (rangeUnits: Seq[RangeUnit]) extends ParsedHeader {
    def key = `Accept-Ranges`
    def value = if (rangeUnits.isEmpty) "none" else rangeUnits.mkString(", ")
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
    def value = credentials.value
  }

  object `Cache-Control` extends InternalHeaderKey[`Cache-Control`] with RecurringHeaderKey
  final case class `Cache-Control`(values: NonEmptyList[CacheDirective]) extends RecurringHeader {
    def key = `Cache-Control`
    type Value = CacheDirective
  }

  object Connection extends InternalHeaderKey[Connection] with RecurringHeaderKey
  final case class Connection(values: NonEmptyList[String]) extends RecurringHeader {
    def key = Connection
    type Value = String
    def hasClose = values.list.exists(_.equalsIgnoreCase("close"))
    def hasKeepAlive = values.list.exists(_.equalsIgnoreCase("keep-alive"))
  }

  object `Content-Base` extends DefaultHeaderKey

  object `Content-Disposition` extends InternalHeaderKey[`Content-Disposition`] with SingletonHeaderKey
  // see http://tools.ietf.org/html/rfc2183
  final case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String]) extends ParsedHeader {
    def key = `Content-Disposition`
    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  object `Content-Encoding` extends InternalHeaderKey[`Content-Encoding`] with SingletonHeaderKey
  final case class `Content-Encoding`(contentCoding: ContentCoding) extends ParsedHeader {
    def key = `Content-Encoding`
    def value = contentCoding.value.toString
  }

  object `Content-Language` extends DefaultHeaderKey

  object `Content-Length` extends InternalHeaderKey[`Content-Length`] with SingletonHeaderKey
  final case class `Content-Length`(length: Int) extends ParsedHeader {
    def key = `Content-Length`
    def value = length.toString
  }

  object `Content-Location` extends DefaultHeaderKey

  object `Content-Transfer-Encoding` extends DefaultHeaderKey

  object `Content-MD5` extends DefaultHeaderKey

  object `Content-Range` extends DefaultHeaderKey

  object `Content-Type` extends InternalHeaderKey[`Content-Type`] with SingletonHeaderKey
  final case class `Content-Type`(contentType: ContentType) extends ParsedHeader {
    def key = `Content-Type`
    def value = contentType.value
  }

  object Cookie extends InternalHeaderKey[Cookie] with RecurringHeaderKey
  final case class Cookie(values: NonEmptyList[org.http4s.Cookie]) extends RecurringHeader {
    def key = Cookie
    type Value = org.http4s.Cookie
    override def value: String = values.list.mkString("; ")
  }

  object Date extends InternalHeaderKey[Date] with SingletonHeaderKey
  final case class Date(date: DateTime) extends ParsedHeader {
    def key = `Date`
    def value = date.formatRfc1123
  }

  object ETag extends InternalHeaderKey[ETag] with SingletonHeaderKey
  case class ETag(tag: String) extends ParsedHeader {
    def key: HeaderKey = ETag
    def value: String = tag
  }

  object Expect extends DefaultHeaderKey

  object Expires extends DefaultHeaderKey

  object From extends DefaultHeaderKey

  object `Front-End-Https` extends DefaultHeaderKey

  object Host extends InternalHeaderKey[Host] with SingletonHeaderKey {
    def apply(host: String, port: Int): Host = apply(host, Some(port))
  }
  final case class Host (host: String, port: Option[Int] = None) extends ParsedHeader {
    def key = `Host`
    def value = port.map(host + ':' + _).getOrElse(host)
  }

  object `If-Match` extends DefaultHeaderKey

  object `If-Modified-Since` extends InternalHeaderKey[`If-Modified-Since`] with SingletonHeaderKey
  final case class `If-Modified-Since`(date: DateTime) extends ParsedHeader {
    def key: HeaderKey = `Last-Modified`
    def value: String = date.formatRfc1123
  }

  object `If-None-Match` extends InternalHeaderKey[`If-None-Match`] with SingletonHeaderKey
  case class `If-None-Match`(tag: String) extends ParsedHeader {
    def key: HeaderKey = `If-None-Match`
    def value: String = tag
  }

  object `If-Range` extends DefaultHeaderKey

  object `If-Unmodified-Since` extends DefaultHeaderKey

  object `Last-Modified` extends InternalHeaderKey[`Last-Modified`] with SingletonHeaderKey
  final case class `Last-Modified`(date: DateTime) extends ParsedHeader {
    def key = `Last-Modified`
    def value = date.formatRfc1123
  }

  object Location extends InternalHeaderKey[Location] with SingletonHeaderKey

  final case class Location(absoluteUri: String) extends ParsedHeader {
    def key = `Location`
    def value = absoluteUri
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
    def value = cookie.value
  }

  object `Set-Cookie2` extends DefaultHeaderKey

  object `TE` extends DefaultHeaderKey

  object `Trailer` extends DefaultHeaderKey

  object `Transfer-Encoding` extends InternalHeaderKey[`Transfer-Encoding`] with RecurringHeaderKey
  final case class `Transfer-Encoding`(values: NonEmptyList[TransferCoding]) extends RecurringHeader {
    def key = `Transfer-Encoding`
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
  final case class `WWW-Authenticate`(values: NonEmptyList[Challenge]) extends RecurringHeader {
    def key = `WWW-Authenticate`
    type Value = Challenge
  }

  object `X-Forwarded-For` extends InternalHeaderKey[`X-Forwarded-For`] with RecurringHeaderKey
  final case class `X-Forwarded-For`(values: NonEmptyList[Option[InetAddress]]) extends RecurringHeader {
    def key = `X-Forwarded-For`
    type Value = Option[InetAddress]
    override def value = values.list.map(_.fold("unknown")(_.getHostAddress)).mkString(", ")
  }

  object `X-Forwarded-Proto` extends DefaultHeaderKey

  object `X-Powered-By` extends DefaultHeaderKey
}
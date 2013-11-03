package org.http4s

import parser.HttpParser
import org.joda.time.DateTime
import java.net.InetAddress
import scala.reflect.ClassTag
import com.typesafe.scalalogging.slf4j.Logging

sealed trait Header extends Logging {

  def name: String

  def lowercaseName: CiString = name.lowercaseEn

  def value: String

  def fromKey[T <: Header](key: HeaderKey[T]): Option[T] = key.unapply(this)

  def is(key: HeaderKey[_]): Boolean = key.is(this)

  def isNot(key: HeaderKey[_]): Boolean = key.isNot(this)

  override def toString = name + ": " + value

  def parsed: Header

  override def equals(obj: scala.Any): Boolean = obj match {
    case h: Header => h.parsed.hashCode() == this.parsed.hashCode()
    case _ => false
  }
}

abstract class ParsedHeader extends Header {
  def parsed: this.type = this
}

object Header {
  def unapply(header: Header): Option[(String, String)] = Some((header.lowercaseName, header.value))
}

object Headers {

  sealed abstract class InternalHeaderKey[T <: Header : ClassTag] extends HeaderKey[T] {
    val name = getClass.getName.split("\\.").last.replaceAll("\\$minus", "-").split("\\$").last.replace("\\$$", "").lowercaseEn

    private val runtimeClass = implicitly[ClassTag[T]].runtimeClass

    override def matchHeader(header: Header): Option[T] = {
      if (runtimeClass.isInstance(header)) Some(header.asInstanceOf[T])
      else if (header.isInstanceOf[RawHeader] && name.equalsIgnoreCase(header.name) && runtimeClass.isInstance(header.parsed))
        Some(header.parsed.asInstanceOf[T])
      else None
    }
  }

  sealed trait DefaultHeaderKey extends InternalHeaderKey[Header] with StringHeaderKey

  final case class RawHeader(name: String, value: String) extends Header {
    override lazy val parsed = HttpParser.parseHeader(this).fold(_ => this, identity)
  }

  object Accept extends InternalHeaderKey[Accept] {
    def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more)
  }
  final case class Accept private[http4s] (mediaRanges: Seq[MediaRange]) extends ParsedHeader {
    def name = "Accept"
    def value = mediaRanges.map(_.value).mkString(", ")
  }

  object `Accept-Charset` extends InternalHeaderKey[`Accept-Charset`] {
    def apply(first: CharsetRange, more: CharsetRange*): `Accept-Charset` = apply(first +: more)
  }
  final case class `Accept-Charset` private[http4s] (charsetRanges: Seq[CharsetRange]) extends ParsedHeader {
    def name = "Accept-Charset"
    def value = charsetRanges.map(_.value).mkString(", ")
  }

  object `Accept-Encoding` extends InternalHeaderKey[`Accept-Encoding`] {
    def apply(first: ContentCodingRange, more: ContentCodingRange*): `Accept-Encoding` = apply(first +: more)
  }
  final case class `Accept-Encoding` private[http4s] (contentCodings: Seq[ContentCodingRange]) extends ParsedHeader {
    def name = "Accept-Encoding"
    def value = contentCodings.map(_.value).mkString(", ")
  }

  object `Accept-Language` extends InternalHeaderKey[`Accept-Language`] {
    def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more)
  }
  final case class `Accept-Language` private[http4s] (languageRanges: Seq[LanguageRange]) extends ParsedHeader {
    def name = "Accept-Language"
    def value = languageRanges.map(_.value).mkString(", ")
  }

  object `Accept-Ranges` extends InternalHeaderKey[`Accept-Ranges`] {
    def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more)
  }
  final case class `Accept-Ranges` private[http4s] (rangeUnits: Seq[RangeUnit]) extends ParsedHeader {
    def name = "Accept-Ranges"
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

  object Authorization extends InternalHeaderKey[Authorization]
  final case class Authorization(credentials: Credentials) extends ParsedHeader {
    def name = "Authorization"
    def value = credentials.value
  }

  object `Cache-Control` extends InternalHeaderKey[`Cache-Control`] {
    def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(first +: more)
  }
  final case class `Cache-Control` private[http4s] (directives: Seq[CacheDirective]) extends ParsedHeader {
    def name = "Cache-Control"
    def value = directives.mkString(", ")
  }

  object Connection extends InternalHeaderKey[Connection] {
    def apply(first: String, more: String*): Connection = apply(first +: more)
  }
  final case class Connection private[http4s] (connectionTokens: Seq[String]) extends ParsedHeader {
    def name = "Connection"
    def value = connectionTokens.mkString(", ")
    def hasClose = connectionTokens.exists(_.toLowerCase == "close")
    def hasKeepAlive = connectionTokens.exists(_.toLowerCase == "keep-alive")
  }

  object `Content-Base` extends DefaultHeaderKey

  object `Content-Disposition` extends InternalHeaderKey[`Content-Disposition`]
  // see http://tools.ietf.org/html/rfc2183
  final case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String]) extends ParsedHeader {
    def name = "Content-Disposition"
    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  object `Content-Encoding` extends InternalHeaderKey[`Content-Encoding`]
  final case class `Content-Encoding`(contentCoding: ContentCoding) extends ParsedHeader {
    def name = "Content-Encoding"
    def value = contentCoding.value
  }

  object `Content-Language` extends DefaultHeaderKey

  object `Content-Length` extends InternalHeaderKey[`Content-Length`]
  final case class `Content-Length`(length: Int) extends ParsedHeader {
    def name = "Content-Length"
    def value = length.toString
  }

  object `Content-Location` extends DefaultHeaderKey

  object `Content-Transfer-Encoding` extends DefaultHeaderKey

  object `Content-MD5` extends DefaultHeaderKey

  object `Content-Range` extends DefaultHeaderKey

  object `Content-Type` extends InternalHeaderKey[`Content-Type`]
  final case class `Content-Type`(contentType: ContentType) extends ParsedHeader {
    def name = "Content-Type"
    def value = contentType.value
  }

  object Cookie extends InternalHeaderKey[Cookie] {
    def apply(first: org.http4s.Cookie, more: org.http4s.Cookie*): Cookie = apply(first +: more)
  }
  final case class Cookie private[http4s] (cookies: Seq[org.http4s.Cookie]) extends ParsedHeader {
    def name = "Cookie"
    def value = cookies.mkString("; ")
  }

  object Date extends InternalHeaderKey[Date]
  final case class Date(date: DateTime) extends ParsedHeader {
    def name = "Date"
    def value = date.formatRfc1123
  }

  object ETag extends DefaultHeaderKey

  object Expect extends DefaultHeaderKey

  object Expires extends DefaultHeaderKey

  object From extends DefaultHeaderKey

  object `Front-End-Https` extends DefaultHeaderKey

  object Host extends InternalHeaderKey[Host] {
    def apply(host: String, port: Int): Host = apply(host, Some(port))
  }
  final case class Host (host: String, port: Option[Int] = None) extends ParsedHeader {
    def name = "Host"
    def value = port.map(host + ':' + _).getOrElse(host)
  }

  object `If-Match` extends DefaultHeaderKey

  object `If-Modified-Since` extends DefaultHeaderKey

  object `If-None-Match` extends DefaultHeaderKey

  object `If-Range` extends DefaultHeaderKey

  object `If-Unmodified-Since` extends DefaultHeaderKey

  object `Last-Modified` extends InternalHeaderKey[`Last-Modified`]
  final case class `Last-Modified`(date: DateTime) extends ParsedHeader {
    def name = "Last-Modified"
    def value = date.formatRfc1123
  }

  object Location extends InternalHeaderKey[Location]

  final case class Location(absoluteUri: String) extends ParsedHeader {
    def name = "Location"
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

  object `Set-Cookie` extends InternalHeaderKey[`Set-Cookie`]
  final case class `Set-Cookie`(cookie: org.http4s.Cookie) extends ParsedHeader {
    def name = "Set-Cookie"
    def value = cookie.value
  }

  object `Set-Cookie2` extends DefaultHeaderKey

  object `TE` extends DefaultHeaderKey

  object `Trailer` extends DefaultHeaderKey

  object `Transfer-Encoding` extends InternalHeaderKey[`Transfer-Encoding`]
  final case class `Transfer-Encoding`(coding: ContentCoding) extends ParsedHeader {
    def name = "Transfer-Encoding"
    def value = coding.value
  }

  object Upgrade extends DefaultHeaderKey

  object `User-Agent` extends DefaultHeaderKey

  object Vary extends DefaultHeaderKey

  object Via extends DefaultHeaderKey

  object Warning extends DefaultHeaderKey

  object `WebSocket-Location` extends DefaultHeaderKey

  object `WebSocket-Origin` extends DefaultHeaderKey

  object `WebSocket-Protocol` extends DefaultHeaderKey

  object `WWW-Authenticate` extends InternalHeaderKey[`WWW-Authenticate`] {
    def apply(first: Challenge, more: Challenge*): `WWW-Authenticate` = apply(first +: more)
  }
  final case class `WWW-Authenticate` private[http4s] (challenges: Seq[Challenge]) extends ParsedHeader {
    def name = "WWW-Authenticate"
    def value = challenges.mkString(", ")
  }

  object `X-Forwarded-For` extends InternalHeaderKey[`X-Forwarded-For`] {
    def apply(first: InetAddress, more: InetAddress*): `X-Forwarded-For` = apply((first +: more).map(Some(_)))
  }
  final case class `X-Forwarded-For` private[http4s] (ips: Seq[Option[InetAddress]]) extends ParsedHeader {
    def name = "X-Forwarded-For"
    def value = ips.map(_.fold("unknown")(_.getHostAddress)).mkString(", ")
  }

  object `X-Forwarded-Proto` extends DefaultHeaderKey

  object `X-Powered-By` extends DefaultHeaderKey
}
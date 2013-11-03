package org.http4s

import parser.HttpParser
import org.joda.time.DateTime
import java.net.InetAddress
import scala.reflect.ClassTag
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s.Headers.RawHeader

abstract class HeaderKey[T <: Header: ClassTag] {

  def name: String

  def option(header: Header): Option[T]

  private[http4s] val runtimeClass = implicitly[ClassTag[T]].runtimeClass

  override def toString: String = name

  def unapply(headers: HeaderCollection): Option[T] =  {
    val it =headers.iterator
    while(it.hasNext) {
      val n = option(it.next())
      if (n.isDefined) return n
    }
    None // Didn't find it
  }

  def unapplySeq(headers: HeaderCollection): Option[Seq[T]] =
    Some(headers flatMap option)

  def from(headers: HeaderCollection): Option[T] = unapply(headers)

  def findIn(headers: HeaderCollection): Seq[T] = unapplySeq(headers) getOrElse Seq.empty

  def unapply(header: Header): Option[T] = option(header)

  def isNot(header: Header) = option(header).isEmpty

  def is(header: Header) = !isNot(header)
}

sealed abstract class InternalHeaderKey[T <: Header : ClassTag] extends HeaderKey[T] {
  val name = getClass.getName.split("\\.").last.replaceAll("\\$minus", "-").split("\\$").last.replace("\\$$", "").lowercaseEn

  override def option(header: Header): Option[T] = {
    if (runtimeClass.isInstance(header)) Some(header.asInstanceOf[T])
    else if (header.isInstanceOf[RawHeader] && runtimeClass.isInstance(header.parsed)) {
      Some(header.parsed.asInstanceOf[T])
    } else None
  }
}

abstract class Header extends Logging {
  def name: String

  def lowercaseName: CiString = name.lowercaseEn

  def value: String

  def option[T <: Header](key: HeaderKey[T]): Option[T] = key.option(this)

  def is(key: HeaderKey[_]): Boolean = key.is(this)

  def isNot(key: HeaderKey[_]): Boolean = key.isNot(this)

  override def toString = name + ": " + value

  lazy val parsed: Header = HttpParser.parseHeader(this).fold(_ => this, identity)
}

object Header {
  def unapply(header: Header): Option[(String, String)] = Some((header.lowercaseName, header.value))
}

object Headers {
  class DefaultHeaderKey extends InternalHeaderKey[Header] {
    // All these headers will likely be raw
    override def option(header: Header): Option[Header] = {
      if (header.name.equalsIgnoreCase(this.name)) Some(header)
      else None
    }
  }

  object Accept extends InternalHeaderKey[Accept] {
    def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more)
  }
  case class Accept private[http4s] (mediaRanges: Seq[MediaRange]) extends Header {
    def name = "Accept"
    def value = mediaRanges.map(_.value).mkString(", ")
  }

  object `Accept-Charset` extends InternalHeaderKey[`Accept-Charset`] {
    def apply(first: CharsetRange, more: CharsetRange*): `Accept-Charset` = apply(first +: more)
  }
  case class `Accept-Charset` private[http4s] (charsetRanges: Seq[CharsetRange]) extends Header {
    def name = "Accept-Charset"
    def value = charsetRanges.map(_.value).mkString(", ")
  }

  object `Accept-Encoding` extends InternalHeaderKey[`Accept-Encoding`] {
    def apply(first: ContentCodingRange, more: ContentCodingRange*): `Accept-Encoding` = apply(first +: more)
  }
  case class `Accept-Encoding` private[http4s] (contentCodings: Seq[ContentCodingRange]) extends Header {
    def name = "Accept-Encoding"
    def value = contentCodings.map(_.value).mkString(", ")
  }

  object `Accept-Language` extends InternalHeaderKey[`Accept-Language`] {
    def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more)
  }
  case class `Accept-Language` private[http4s] (languageRanges: Seq[LanguageRange]) extends Header {
    def name = "Accept-Language"
    def value = languageRanges.map(_.value).mkString(", ")
  }

  object `Accept-Ranges` extends InternalHeaderKey[`Accept-Ranges`] {
    def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more)
  }
  case class `Accept-Ranges` private[http4s] (rangeUnits: Seq[RangeUnit]) extends Header {
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
  case class Authorization(credentials: Credentials) extends Header {
    def name = "Authorization"
    def value = credentials.value
  }

  object `Cache-Control` extends InternalHeaderKey[`Cache-Control`] {
    def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(first +: more)
  }
  case class `Cache-Control` private[http4s] (directives: Seq[CacheDirective]) extends Header {
    def name = "Cache-Control"
    def value = directives.mkString(", ")
  }

  object Connection extends InternalHeaderKey[Connection] {
    def apply(first: String, more: String*): Connection = apply(first +: more)
  }
  case class Connection private[http4s] (connectionTokens: Seq[String]) extends Header {
    def name = "Connection"
    def value = connectionTokens.mkString(", ")
    def hasClose = connectionTokens.exists(_.toLowerCase == "close")
    def hasKeepAlive = connectionTokens.exists(_.toLowerCase == "keep-alive")
  }

  object `Content-Base` extends DefaultHeaderKey

  object `Content-Disposition` extends InternalHeaderKey[`Content-Disposition`]
  // see http://tools.ietf.org/html/rfc2183
  case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String]) extends Header {
    def name = "Content-Disposition"
    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  object `Content-Encoding` extends InternalHeaderKey[`Content-Encoding`]
  case class `Content-Encoding`(contentCoding: ContentCoding) extends Header {
    def name = "Content-Encoding"
    def value = contentCoding.value
  }

  object `Content-Language` extends DefaultHeaderKey

  object `Content-Length` extends InternalHeaderKey[`Content-Length`]
  case class `Content-Length`(length: Int) extends Header {
    def name = "Content-Length"
    def value = length.toString
  }

  object `Content-Location` extends DefaultHeaderKey

  object `Content-Transfer-Encoding` extends DefaultHeaderKey

  object `Content-MD5` extends DefaultHeaderKey

  object `Content-Range` extends DefaultHeaderKey

  object `Content-Type` extends InternalHeaderKey[`Content-Type`]
  case class `Content-Type`(contentType: ContentType) extends Header {
    def name = "Content-Type"
    def value = contentType.value
  }

  object Cookie extends InternalHeaderKey[Cookie] {
    def apply(first: org.http4s.Cookie, more: org.http4s.Cookie*): Cookie = apply(first +: more)
  }
  case class Cookie private[http4s] (cookies: Seq[org.http4s.Cookie]) extends Header {
    def name = "Cookie"
    def value = cookies.mkString("; ")
  }

  object Date extends InternalHeaderKey[Date]
  case class Date(date: DateTime) extends Header {
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
  case class Host (host: String, port: Option[Int] = None) extends Header {
    def name = "Host"
    def value = port.map(host + ':' + _).getOrElse(host)
  }

  object `If-Match` extends DefaultHeaderKey

  object `If-Modified-Since` extends DefaultHeaderKey

  object `If-None-Match` extends DefaultHeaderKey

  object `If-Range` extends DefaultHeaderKey

  object `If-Unmodified-Since` extends DefaultHeaderKey

  object `Last-Modified` extends InternalHeaderKey[`Last-Modified`]
  case class `Last-Modified`(date: DateTime) extends Header {
    def name = "Last-Modified"
    def value = date.formatRfc1123
  }

  object Location extends InternalHeaderKey[Location]

  case class Location(absoluteUri: String) extends Header {
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
  case class `Set-Cookie`(cookie: org.http4s.Cookie) extends Header {
    def name = "Set-Cookie"
    def value = cookie.value
  }

  object `Set-Cookie2` extends DefaultHeaderKey

  object `TE` extends DefaultHeaderKey

  object `Trailer` extends DefaultHeaderKey

  object `Transfer-Encoding` extends InternalHeaderKey[`Transfer-Encoding`]
  case class `Transfer-Encoding`(coding: ContentCoding) extends Header {
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
  case class `WWW-Authenticate` private[http4s] (challenges: Seq[Challenge]) extends Header {
    def name = "WWW-Authenticate"
    def value = challenges.mkString(", ")
  }

  object `X-Forwarded-For` extends InternalHeaderKey[`X-Forwarded-For`] {
    def apply(first: InetAddress, more: InetAddress*): `X-Forwarded-For` = apply((first +: more).map(Some(_)))
  }
  case class `X-Forwarded-For` private[http4s] (ips: Seq[Option[InetAddress]]) extends Header {
    def name = "X-Forwarded-For"
    def value = ips.map(_.fold("unknown")(_.getHostAddress)).mkString(", ")
  }

  object `X-Forwarded-Proto` extends DefaultHeaderKey

  object `X-Powered-By` extends DefaultHeaderKey

  case class RawHeader(name: String, value: String) extends Header
}
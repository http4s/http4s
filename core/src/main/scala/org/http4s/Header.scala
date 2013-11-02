package org.http4s

import parser.HttpParser
import org.joda.time.DateTime
import java.net.InetAddress
import java.util.Locale
import org.http4s.util.Lowercase
import scalaz.@@
import scala.reflect.ClassTag

abstract class HeaderKey[T <: Header : ClassTag] {
  private[this] val _cn = getClass.getName.split("\\.").last.split("\\$").last.replace("\\$$", "")

  private[http4s] val _clazz = implicitly[ClassTag[T]].runtimeClass

  def name: CiString = _cn.lowercaseEn

  override def toString: String = name

  def unapply(headers: HeaderCollection): Option[T] =
    (headers find (_ is this) map (_.parsed)).collectFirst(collectHeader)

  def unapplySeq(headers: HeaderCollection): Option[Seq[T]] =
    Some((headers filter (_ is this) map (_.parsed)).collect(collectHeader))

  def from(headers: HeaderCollection): Option[T] = unapply(headers)

  def findIn(headers: HeaderCollection): Seq[T] = unapplySeq(headers) getOrElse Seq.empty

  protected[this] def collectHeader: PartialFunction[Header, T]
}

abstract class Header {
  def name: String

  def lowercaseName: CiString

  def value: String

  def is(key: HeaderKey[_]): Boolean = key._clazz.getClass.isAssignableFrom(this.getClass)

  def isNot(key: HeaderKey[_]): Boolean = !is(key)

  def is(otherName: CiString) = this.lowercaseName == otherName

  def isNot(otherName: CiString) = !is(otherName)

  override def toString = name + ": " + value

  lazy val parsed = this
}

object Header {
  def unapply(header: Header): Option[(String, String)] = Some((header.lowercaseName, header.value))
}

object Headers {

  abstract class DefaultHeaderKey extends HeaderKey[Header] {

    protected[this] def collectHeader: PartialFunction[Header, Header] = {
      case h => h
    }

  }


  object Accept extends HeaderKey[Accept] {
    protected[this] def collectHeader: PartialFunction[Header, Accept] = {
      case h: Accept => h
    }

    def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more)
  }

  case class Accept(mediaRanges: Seq[MediaRange]) extends Header {
    def name = "Accept"

    def lowercaseName = "accept".lowercaseEn

    def value = mediaRanges.map(_.value).mkString(", ")
  }

  object AcceptCharset extends HeaderKey[AcceptCharset] {
    override val name: CiString = "Accept-Charset".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, AcceptCharset] = {
      case h: AcceptCharset => h
    }

    def apply(first: CharsetRange, more: CharsetRange*): AcceptCharset = apply(first +: more)
  }
  case class AcceptCharset(charsetRanges: Seq[CharsetRange]) extends Header {
    def name = "Accept-Charset"

    def lowercaseName = "accept-charset".lowercaseEn

    def value = charsetRanges.map(_.value).mkString(", ")
  }

  object AcceptEncoding extends HeaderKey[AcceptEncoding] {
    override val name: CiString = "Accept-Encoding".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, AcceptEncoding] = {
      case h: AcceptEncoding => h
    }

    def apply(first: ContentCodingRange, more: ContentCodingRange*): AcceptEncoding = apply(first +: more)
  }

  case class AcceptEncoding(encodings: Seq[ContentCodingRange]) extends Header {
    def name = "Accept-Encoding"

    def lowercaseName = "accept-encoding".lowercaseEn

    def value = encodings.map(_.value).mkString(", ")
  }

  object AcceptLanguage extends HeaderKey[AcceptLanguage] {
    override val name: CiString = "Accept-Language".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, AcceptLanguage] = {
      case h: AcceptLanguage => h
    }

    def apply(first: LanguageRange, more: LanguageRange*): AcceptLanguage = apply(first +: more)
  }

  case class AcceptLanguage(languageRanges: Seq[LanguageRange]) extends Header {
    def name = "Accept-Language"

    def lowercaseName = "accept-language".lowercaseEn

    def value = languageRanges.map(_.value).mkString(", ")
  }

  object AcceptRanges extends HeaderKey[AcceptRanges] {
    override val name: CiString = "Accept-Ranges".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, AcceptRanges] = {
      case h: AcceptRanges => h
    }

    def apply(first: RangeUnit, more: RangeUnit*): AcceptRanges = apply(first +: more)
  }

  case class AcceptRanges(rangeUnits: Seq[RangeUnit]) extends Header {
    def name = "Accept-Ranges"

    def lowercaseName = "accept-ranges".lowercaseEn

    def value = if (rangeUnits.isEmpty) "none" else rangeUnits.mkString(", ")
  }

  object AcceptPatch extends DefaultHeaderKey {
    override val name: CiString = "Accept-Patch".lowercaseEn
  }

  object AccessControlAllowCredentials extends DefaultHeaderKey {
    override val name: CiString = "Access-Control-Allow-Credentials".lowercaseEn
  }

  object AccessControlAllowHeaders extends DefaultHeaderKey {
    override val name: CiString = "Access-Control-Allow-Headers".lowercaseEn
  }

  object AccessControlAllowMethods extends DefaultHeaderKey {
    override val name: CiString = "Access-Control-Allow-Methods".lowercaseEn
  }

  object AccessControlAllowOrigin extends DefaultHeaderKey {
    override val name: CiString = "Access-Control-Allow-Origin".lowercaseEn
  }

  object AccessControlExposeHeaders extends DefaultHeaderKey {
    override val name: CiString = "Access-Control-Expose-Headers".lowercaseEn
  }

  object AccessControlMaxAge extends DefaultHeaderKey {
    override val name: CiString = "Access-Control-Max-Age".lowercaseEn
  }

  object AccessControlRequestHeaders extends DefaultHeaderKey {
    override val name: CiString = "Access-Control-Request-Headers".lowercaseEn
  }

  object AccessControlRequestMethod extends DefaultHeaderKey {
    override val name: CiString = "Access-Control-Request-Method".lowercaseEn
  }

  object Age extends DefaultHeaderKey


  object Allow extends DefaultHeaderKey

  object Authorization extends HeaderKey[Authorization] {
    protected[this] def collectHeader: PartialFunction[Header, Authorization] = {
      case h: Authorization => h
    }
  }
  case class Authorization(credentials: Credentials) extends Header {
    def name = "Authorization"

    def lowercaseName = "authorization".lowercaseEn

    def value = credentials.value
  }
  object CacheControl extends HeaderKey[CacheControl] {

      override val name: CiString = "Cache-Control".lowercaseEn

      protected[this] def collectHeader: PartialFunction[Header, CacheControl] = {
        case h: CacheControl => h
      }

    def apply(first: CacheDirective, more: CacheDirective*): CacheControl = apply(first +: more)
  }

  case class CacheControl(directives: Seq[CacheDirective]) extends Header {
    def name = "Cache-Control"

    def lowercaseName = "cache-control".lowercaseEn

    def value = directives.mkString(", ")
  }

  object Connection extends HeaderKey[Connection] {
    protected[this] def collectHeader: PartialFunction[Header, Connection] = {
      case h: Connection => h
    }
    def apply(first: String, more: String*): `Connection` = apply(first +: more)
  }

  case class Connection(connectionTokens: Seq[String]) extends Header {
    def name = "Connection"

    def lowercaseName = "connection".lowercaseEn

    def value = connectionTokens.mkString(", ")

    def hasClose = connectionTokens.exists(_.toLowerCase == "close")

    def hasKeepAlive = connectionTokens.exists(_.toLowerCase == "keep-alive")
  }

  object ContentBase extends DefaultHeaderKey {
    override val name: CiString = "Content-Base".lowercaseEn
  }

  object ContentDisposition extends HeaderKey[ContentDisposition] {
    override val name: CiString = "Content-Disposition".lowercaseEn
    protected[this] def collectHeader: PartialFunction[Header, ContentDisposition] = { case h: ContentDisposition => h}
  }

  // see http://tools.ietf.org/html/rfc2183
  case class ContentDisposition(dispositionType: String, parameters: Map[String, String]) extends Header {
    def name = "Content-Disposition"

    def lowercaseName = "content-disposition".lowercaseEn

    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  object ContentEncoding extends HeaderKey[ContentEncoding] {
    override val name: CiString = "Content-Encoding".lowercaseEn
    protected[this] def collectHeader: PartialFunction[Header, ContentEncoding] = { case h: ContentEncoding => h}
  }
  case class ContentEncoding(encoding: ContentCoding) extends Header {
    def name = "Content-Encoding"

    def lowercaseName = "content-encoding".lowercaseEn

    def value = encoding.value
  }

  object ContentLanguage extends DefaultHeaderKey {
    override val name: CiString = "Content-Language".lowercaseEn
  }

  object ContentLength extends HeaderKey[ContentLength] {
    override val name: CiString = "Content-Length".lowercaseEn
    protected[this] def collectHeader: PartialFunction[Header, ContentLength] = { case h: ContentLength => h }
  }

  case class ContentLength(length: Int) extends Header {
    def name = "Content-Length"

    def lowercaseName = "content-length".lowercaseEn

    def value = length.toString
  }

  object ContentLocation extends DefaultHeaderKey {
    override val name: CiString = "Content-Location".lowercaseEn
  }

  object ContentTransferEncoding extends DefaultHeaderKey {
    override val name: CiString = "Content-Transfer-Encoding".lowercaseEn
  }

  object ContentMd5 extends DefaultHeaderKey {
    override val name: CiString = "Content-MD5".lowercaseEn
  }

  object ContentRange extends DefaultHeaderKey {
    override val name: CiString = "Content-Range".lowercaseEn
  }

  object ContentType extends HeaderKey[ContentType] {
    override val name: CiString = "Content-Type".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, ContentType] = {
      case h: ContentType => h
    }
  }
  case class ContentType(contentType: org.http4s.ContentType) extends Header {
    def name = "Content-Type"

    def lowercaseName = "content-type".lowercaseEn

    def value = contentType.value
  }


  object Cookie extends HeaderKey[Cookie] {
    protected[this] def collectHeader: PartialFunction[Header, Cookie] = {
      case h: Cookie => h
    }

    def apply(first: org.http4s.Cookie, more: org.http4s.Cookie*): Cookie = apply(first +: more)
  }

  case class Cookie(cookies: Seq[org.http4s.Cookie]) extends Header {
    def name = "Cookie"

    def lowercaseName = "cookie".lowercaseEn

    def value = cookies.mkString("; ")
  }

  object Date extends HeaderKey[Date] {
    protected[this] def collectHeader: PartialFunction[Header, Date] = {
      case h: Date => h
    }
  }

  case class Date(date: DateTime) extends Header {
    def name = "Date"

    def lowercaseName = "date".lowercaseEn

    def value = date.formatRfc1123
  }

  object ETag extends DefaultHeaderKey

  object Expect extends DefaultHeaderKey

  object Expires extends DefaultHeaderKey

  object From extends DefaultHeaderKey

  object FrontEndHttps extends DefaultHeaderKey {
    override val name: CiString = "Front-End-Https".lowercaseEn
  }

  object Host extends HeaderKey[Host] {
    protected[this] def collectHeader: PartialFunction[Header, Host] = {
      case h: Host => h
    }
    def apply(host: String, port: Int): Host = apply(host, Some(port))
  }

  case class Host(host: String, port: Option[Int] = None) extends Header {
    def name = "Host"

    def lowercaseName = "host".lowercaseEn

    def value = port.map(host + ':' + _).getOrElse(host)
  }

  object IfMatch extends DefaultHeaderKey {
    override val name: CiString = "If-Match".lowercaseEn
  }

  object IfModifiedSince extends DefaultHeaderKey {
    override val name: CiString = "If-Modified-Since".lowercaseEn
  }

  object IfNoneMatch extends DefaultHeaderKey {
    override val name: CiString = "If-None-Match".lowercaseEn
  }

  object IfRange extends DefaultHeaderKey {
    override val name: CiString = "If-Range".lowercaseEn
  }

  object IfUnmodifiedSince extends DefaultHeaderKey {
    override val name: CiString = "If-Unmodified-Since".lowercaseEn
  }

  object LastModified extends HeaderKey[LastModified] {
    override val name: CiString = "Last-Modified".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, LastModified] = {
      case h: LastModified => h
    }
  }

  case class LastModified(date: DateTime) extends Header {
    def name = "Last-Modified"

    def lowercaseName = "last-modified".lowercaseEn

    def value = date.formatRfc1123
  }

  object Location extends HeaderKey[Location] {
    protected[this] def collectHeader: PartialFunction[Header, Location] = {
      case h: Location => h
    }
  }

  case class Location(absoluteUri: String) extends Header {
    def name = "Location"

    def lowercaseName = "location".lowercaseEn

    def value = absoluteUri
  }

  object MaxForwards extends DefaultHeaderKey {
    override val name: CiString = "Max-Forwards".lowercaseEn
  }

  object Origin extends DefaultHeaderKey

  object Pragma extends DefaultHeaderKey

  object ProxyAuthenticate extends DefaultHeaderKey {
    override val name: CiString = "Proxy-Authenticate".lowercaseEn
  }

  object ProxyAuthorization extends DefaultHeaderKey {
    override val name: CiString = "Proxy-Authorization".lowercaseEn
  }

  object Range extends DefaultHeaderKey

  object Referer extends DefaultHeaderKey

  object RetryAfter extends DefaultHeaderKey {
    override val name: CiString = "Retry-After".lowercaseEn
  }

  object SecWebSocketKey extends DefaultHeaderKey {
    override val name: CiString = "Sec-WebSocket-Key".lowercaseEn
  }

  object SecWebSocketKey1 extends DefaultHeaderKey {
    override val name: CiString = "Sec-WebSocket-Key1".lowercaseEn
  }

  object SecWebSocketKey2 extends DefaultHeaderKey {
    override val name: CiString = "Sec-WebSocket-Key2".lowercaseEn
  }

  object SecWebSocketLocation extends DefaultHeaderKey {
    override val name: CiString = "Sec-WebSocket-Location".lowercaseEn
  }

  object SecWebSocketOrigin extends DefaultHeaderKey {
    override val name: CiString = "Sec-WebSocket-Origin".lowercaseEn
  }

  object SecWebSocketProtocol extends DefaultHeaderKey {
    override val name: CiString = "Sec-WebSocket-Protocol".lowercaseEn
  }

  object SecWebSocketVersion extends DefaultHeaderKey {
    override val name: CiString = "Sec-WebSocket-Version".lowercaseEn
  }

  object SecWebSocketAccept extends DefaultHeaderKey {
    override val name: CiString = "Sec-WebSocket-Accept".lowercaseEn
  }

  object Server extends DefaultHeaderKey

  object SetCookie extends HeaderKey[SetCookie] {
    override val name: CiString = "Set-Cookie".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, SetCookie] = {
      case h: SetCookie => h
    }
  }
  case class SetCookie(cookie: org.http4s.Cookie) extends Header {
    def name = "Set-Cookie"

    def lowercaseName = "set-cookie".lowercaseEn

    def value = cookie.value
  }


  object SetCookie2 extends DefaultHeaderKey {
    override val name: CiString = "Set-Cookie2".lowercaseEn
  }

  object TE extends DefaultHeaderKey

  object Trailer extends DefaultHeaderKey

  object TransferEncoding extends HeaderKey[TransferEncoding] {
    override val name: CiString = "Transfer-Encoding".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, TransferEncoding] = {
      case h: TransferEncoding => h
    }
  }
  case class TransferEncoding(coding: ContentCoding) extends Header {
    def name = "Transfer-Encoding"

    def lowercaseName = "transfer-encoding".lowercaseEn

    def value = coding.value
  }

  object Upgrade extends DefaultHeaderKey

  object UserAgent extends DefaultHeaderKey {
    override val name: CiString = "User-Agent".lowercaseEn
  }

  object Vary extends DefaultHeaderKey

  object Via extends DefaultHeaderKey

  object Warning extends DefaultHeaderKey

  object WebSocketLocation extends DefaultHeaderKey {
    override val name: CiString = "WebSocket-Location".lowercaseEn
  }

  object WebSocketOrigin extends DefaultHeaderKey {
    override val name: CiString = "WebSocket-Origin".lowercaseEn
  }

  object WebSocketProtocol extends DefaultHeaderKey {
    override val name: CiString = "WebSocket-Protocol".lowercaseEn
  }

  object WWWAuthenticate extends HeaderKey[WWWAuthenticate] {
    override val name: CiString = "WWW-Authenticate".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, WWWAuthenticate] = {
      case h: WWWAuthenticate => h
    }
    def apply(first: Challenge, more: Challenge*): WWWAuthenticate = apply(first +: more)
  }

  case class WWWAuthenticate(challenges: Seq[Challenge]) extends Header {
    def name = "WWW-Authenticate"

    def lowercaseName = "www-authenticate".lowercaseEn

    def value = challenges.mkString(", ")
  }
  object XForwardedFor extends HeaderKey[XForwardedFor] {
    override val name: CiString = "X-Forwarded-For".lowercaseEn

    protected[this] def collectHeader: PartialFunction[Header, XForwardedFor] = {
      case h: XForwardedFor => h
    }

    def apply(first: InetAddress, more: InetAddress*): XForwardedFor = apply((first +: more).map(Some(_)))
  }

  case class XForwardedFor(ips: Seq[Option[InetAddress]]) extends Header {
    def name = "X-Forwarded-For"

    def lowercaseName = "x-forwarded-for".lowercaseEn

    def value = ips.map(_.fold("unknown")(_.getHostAddress)).mkString(", ")
  }

  object XForwardedProto extends DefaultHeaderKey {
    override val name: CiString = "X-Forwarded-Proto".lowercaseEn
  }

  object XPoweredBy extends DefaultHeaderKey {
    override val name: CiString = "X-Powered-By".lowercaseEn
  }

  case class RawHeader(name: String, value: String) extends Header {
    val lowercaseName = name.lowercaseEn
    override lazy val parsed: Header = HttpParser.parseHeader(this).fold(_ => this, identity)
  }

  object Key {

    def apply[T <: Header : ClassTag](nm: String, collector: PartialFunction[Header, T]): HeaderKey[T] = new HeaderKey[T] {
      override val name: CiString = nm.lowercaseEn

      protected[this] def collectHeader: PartialFunction[Header, T] = collector
    }

    def apply(nm: String): HeaderKey[Header] = new DefaultHeaderKey {
      override val name: CiString = nm.lowercaseEn
    }
  }
}
package org.http4s

import parser.HttpParser
import org.joda.time.DateTime
import java.net.InetAddress
import java.util.Locale
import org.http4s.util.Lowercase
import scalaz.@@

trait HeaderKey[T <: Header] {
  private[this] val _cn = getClass.getName.split("\\.").last.split("\\$").last.replace("\\$$", "")

  def name = _cn

  override def toString: String = name

  def unapply(headers: HeaderCollection): Option[T] =
    (headers find (_ is name.lowercase(Locale.US)) map (_.parsed)).collectFirst(collectHeader)

  def unapplySeq(headers: HeaderCollection): Option[Seq[T]] =
    Some((headers filter (_ is name.lowercase(Locale.US)) map (_.parsed)).collect(collectHeader))

  def from(headers: HeaderCollection): Option[T] = unapply(headers)

  def findIn(headers: HeaderCollection): Seq[T] = unapplySeq(headers) getOrElse Seq.empty

  protected[this] def collectHeader: PartialFunction[Header, T]
}

abstract class Header {
  def name: String

  def lowercaseName: String @@ Lowercase

  def value: String

  def is(name: String @@ Lowercase): Boolean = this.lowercaseName == name

  def isNot(name: String @@ Lowercase): Boolean = this.lowercaseName != name

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

    def lowercaseName = "accept".lowercase

    def value = mediaRanges.map(_.value).mkString(", ")
  }

  object AcceptCharset extends HeaderKey[AcceptCharset] {
    override val name: String = "Accept-Charset"

    protected[this] def collectHeader: PartialFunction[Header, AcceptCharset] = {
      case h: AcceptCharset => h
    }

    def apply(first: HttpCharsetRange, more: HttpCharsetRange*): AcceptCharset = apply(first +: more)
  }
  case class AcceptCharset(charsetRanges: Seq[HttpCharsetRange]) extends Header {
    def name = "Accept-Charset"

    def lowercaseName = "accept-charset".lowercase

    def value = charsetRanges.map(_.value).mkString(", ")
  }

  object AcceptEncoding extends HeaderKey[AcceptEncoding] {
    override val name: String = "Accept-Encoding"

    protected[this] def collectHeader: PartialFunction[Header, AcceptEncoding] = {
      case h: AcceptEncoding => h
    }

    def apply(first: ContentCodingRange, more: ContentCodingRange*): AcceptEncoding = apply(first +: more)
  }

  case class AcceptEncoding(encodings: Seq[ContentCodingRange]) extends Header {
    def name = "Accept-Encoding"

    def lowercaseName = "accept-encoding".lowercase

    def value = encodings.map(_.value).mkString(", ")
  }

  object AcceptLanguage extends HeaderKey[AcceptLanguage] {
    override val name: String = "Accept-Language"

    protected[this] def collectHeader: PartialFunction[Header, AcceptLanguage] = {
      case h: AcceptLanguage => h
    }

    def apply(first: LanguageRange, more: LanguageRange*): AcceptLanguage = apply(first +: more)
  }

  case class AcceptLanguage(languageRanges: Seq[LanguageRange]) extends Header {
    def name = "Accept-Language"

    def lowercaseName = "accept-language".lowercase

    def value = languageRanges.map(_.value).mkString(", ")
  }

  object AcceptRanges extends HeaderKey[AcceptRanges] {
    override val name: String = "Accept-Ranges"

    protected[this] def collectHeader: PartialFunction[Header, AcceptRanges] = {
      case h: AcceptRanges => h
    }

    def apply(first: RangeUnit, more: RangeUnit*): AcceptRanges = apply(first +: more)
  }

  case class AcceptRanges(rangeUnits: Seq[RangeUnit]) extends Header {
    def name = "Accept-Ranges"

    def lowercaseName = "accept-ranges".lowercase

    def value = if (rangeUnits.isEmpty) "none" else rangeUnits.mkString(", ")
  }

  object AcceptPatch extends DefaultHeaderKey {
    override val name: String = "Accept-Patch"
  }

  object AccessControlAllowCredentials extends DefaultHeaderKey {
    override val name: String = "Access-Control-Allow-Credentials"
  }

  object AccessControlAllowHeaders extends DefaultHeaderKey {
    override val name: String = "Access-Control-Allow-Headers"
  }

  object AccessControlAllowMethods extends DefaultHeaderKey {
    override val name: String = "Access-Control-Allow-Methods"
  }

  object AccessControlAllowOrigin extends DefaultHeaderKey {
    override val name: String = "Access-Control-Allow-Origin"
  }

  object AccessControlExposeHeaders extends DefaultHeaderKey {
    override val name: String = "Access-Control-Expose-Headers"
  }

  object AccessControlMaxAge extends DefaultHeaderKey {
    override val name: String = "Access-Control-Max-Age"
  }

  object AccessControlRequestHeaders extends DefaultHeaderKey {
    override val name: String = "Access-Control-Request-Headers"
  }

  object AccessControlRequestMethod extends DefaultHeaderKey {
    override val name: String = "Access-Control-Request-Method"
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

    def lowercaseName = "authorization".lowercase

    def value = credentials.value
  }
  object CacheControl extends HeaderKey[CacheControl] {

      override val name: String = "Cache-Control"

      protected[this] def collectHeader: PartialFunction[Header, CacheControl] = {
        case h: CacheControl => h
      }

    def apply(first: CacheDirective, more: CacheDirective*): CacheControl = apply(first +: more)
  }

  case class CacheControl(directives: Seq[CacheDirective]) extends Header {
    def name = "Cache-Control"

    def lowercaseName = "cache-control".lowercase

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

    def lowercaseName = "connection".lowercase

    def value = connectionTokens.mkString(", ")

    def hasClose = connectionTokens.exists(_.toLowerCase == "close")

    def hasKeepAlive = connectionTokens.exists(_.toLowerCase == "keep-alive")
  }

  object ContentBase extends DefaultHeaderKey {
    override val name: String = "Content-Base"
  }

  object ContentDisposition extends HeaderKey[ContentDisposition] {
    override val name: String = "Content-Disposition"
    protected[this] def collectHeader: PartialFunction[Header, ContentDisposition] = { case h: ContentDisposition => h}
  }

  // see http://tools.ietf.org/html/rfc2183
  case class ContentDisposition(dispositionType: String, parameters: Map[String, String]) extends Header {
    def name = "Content-Disposition"

    def lowercaseName = "content-disposition".lowercase

    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  object ContentEncoding extends HeaderKey[ContentEncoding] {
    override val name: String = "Content-Encoding"
    protected[this] def collectHeader: PartialFunction[Header, ContentEncoding] = { case h: ContentEncoding => h}
  }
  case class ContentEncoding(encoding: ContentCoding) extends Header {
    def name = "Content-Encoding"

    def lowercaseName = "content-encoding".lowercase

    def value = encoding.value
  }

  object ContentLanguage extends DefaultHeaderKey { override val name: String = "Content-Language" }
  object ContentLength extends HeaderKey[ContentLength] {
    override val name: String = "Content-Length"
    protected[this] def collectHeader: PartialFunction[Header, ContentLength] = { case h: ContentLength => h }
  }

  case class ContentLength(length: Int) extends Header {
    def name = "Content-Length"

    def lowercaseName = "content-length".lowercase

    def value = length.toString
  }

  object ContentLocation extends DefaultHeaderKey {
    override val name: String = "Content-Location"
  }

  object ContentTransferEncoding extends DefaultHeaderKey {
    override val name: String = "Content-Transfer-Encoding"
  }

  object ContentMd5 extends DefaultHeaderKey {
    override val name: String = "Content-MD5"
  }

  object ContentRange extends DefaultHeaderKey {
    override val name: String = "Content-Range"
  }

  object ContentType extends HeaderKey[ContentType] {
    override val name: String = "Content-Type"

    protected[this] def collectHeader: PartialFunction[Header, ContentType] = {
      case h: ContentType => h
    }
  }
  case class ContentType(contentType: org.http4s.ContentType) extends Header {
    def name = "Content-Type"

    def lowercaseName = "content-type".lowercase

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

    def lowercaseName = "cookie".lowercase

    def value = cookies.mkString("; ")
  }

  object Date extends HeaderKey[Date] {
    protected[this] def collectHeader: PartialFunction[Header, Date] = {
      case h: Date => h
    }
  }

  case class Date(date: DateTime) extends Header {
    def name = "Date"

    def lowercaseName = "date".lowercase

    def value = date.formatRfc1123
  }

  object ETag extends DefaultHeaderKey

  object Expect extends DefaultHeaderKey

  object Expires extends DefaultHeaderKey

  object From extends DefaultHeaderKey

  object FrontEndHttps extends DefaultHeaderKey {
    override val name: String = "Front-End-Https"
  }

  object Host extends HeaderKey[Host] {
    protected[this] def collectHeader: PartialFunction[Header, Host] = {
      case h: Host => h
    }
    def apply(host: String, port: Int): Host = apply(host, Some(port))
  }

  case class Host(host: String, port: Option[Int] = None) extends Header {
    def name = "Host"

    def lowercaseName = "host".lowercase

    def value = port.map(host + ':' + _).getOrElse(host)
  }

  object IfMatch extends DefaultHeaderKey {
    override val name: String = "If-Match"
  }

  object IfModifiedSince extends DefaultHeaderKey {
    override val name: String = "If-Modified-Since"
  }

  object IfNoneMatch extends DefaultHeaderKey {
    override val name: String = "If-None-Match"
  }

  object IfRange extends DefaultHeaderKey {
    override val name: String = "If-Range"
  }

  object IfUnmodifiedSince extends DefaultHeaderKey {
    override val name: String = "If-Unmodified-Since"
  }

  object LastModified extends HeaderKey[LastModified] {
    override val name: String = "Last-Modified"

    protected[this] def collectHeader: PartialFunction[Header, LastModified] = {
      case h: LastModified => h
    }
  }

  case class LastModified(date: DateTime) extends Header {
    def name = "Last-Modified"

    def lowercaseName = "last-modified".lowercase

    def value = date.formatRfc1123
  }

  object Location extends HeaderKey[Location] {
    protected[this] def collectHeader: PartialFunction[Header, Location] = {
      case h: Location => h
    }
  }

  case class Location(absoluteUri: String) extends Header {
    def name = "Location"

    def lowercaseName = "location".lowercase

    def value = absoluteUri
  }

  object MaxForwards extends DefaultHeaderKey {
    override val name: String = "Max-Forwards"
  }

  object Origin extends DefaultHeaderKey

  object Pragma extends DefaultHeaderKey

  object ProxyAuthenticate extends DefaultHeaderKey {
    override val name: String = "Proxy-Authenticate"
  }

  object ProxyAuthorization extends DefaultHeaderKey {
    override val name: String = "Proxy-Authorization"
  }

  object Range extends DefaultHeaderKey

  object Referer extends DefaultHeaderKey

  object RetryAfter extends DefaultHeaderKey {
    override val name: String = "Retry-After"
  }

  object SecWebSocketKey extends DefaultHeaderKey {
    override val name: String = "Sec-WebSocket-Key"
  }

  object SecWebSocketKey1 extends DefaultHeaderKey {
    override val name: String = "Sec-WebSocket-Key1"
  }

  object SecWebSocketKey2 extends DefaultHeaderKey {
    override val name: String = "Sec-WebSocket-Key2"
  }

  object SecWebSocketLocation extends DefaultHeaderKey {
    override val name: String = "Sec-WebSocket-Location"
  }

  object SecWebSocketOrigin extends DefaultHeaderKey {
    override val name: String = "Sec-WebSocket-Origin"
  }

  object SecWebSocketProtocol extends DefaultHeaderKey {
    override val name: String = "Sec-WebSocket-Protocol"
  }

  object SecWebSocketVersion extends DefaultHeaderKey {
    override val name: String = "Sec-WebSocket-Version"
  }

  object SecWebSocketAccept extends DefaultHeaderKey {
    override val name: String = "Sec-WebSocket-Accept"
  }

  object Server extends DefaultHeaderKey

  object SetCookie extends HeaderKey[SetCookie] {
    override val name: String = "Set-Cookie"

    protected[this] def collectHeader: PartialFunction[Header, SetCookie] = {
      case h: SetCookie => h
    }
  }
  case class SetCookie(cookie: org.http4s.Cookie) extends Header {
    def name = "Set-Cookie"

    def lowercaseName = "set-cookie".lowercase

    def value = cookie.value
  }


  object SetCookie2 extends DefaultHeaderKey {
    override val name: String = "Set-Cookie2"
  }

  object TE extends DefaultHeaderKey

  object Trailer extends DefaultHeaderKey

  object TransferEncoding extends HeaderKey[TransferEncoding] {
    override val name: String = "Transfer-Encoding"

    protected[this] def collectHeader: PartialFunction[Header, TransferEncoding] = {
      case h: TransferEncoding => h
    }
  }
  case class TransferEncoding(coding: ContentCoding) extends Header {
    def name = "Transfer-Encoding"

    def lowercaseName = "transfer-encoding".lowercase

    def value = coding.value
  }

  object Upgrade extends DefaultHeaderKey

  object UserAgent extends DefaultHeaderKey {
    override val name: String = "User-Agent"
  }

  object Vary extends DefaultHeaderKey

  object Via extends DefaultHeaderKey

  object Warning extends DefaultHeaderKey

  object WebSocketLocation extends DefaultHeaderKey {
    override val name: String = "WebSocket-Location"
  }

  object WebSocketOrigin extends DefaultHeaderKey {
    override val name: String = "WebSocket-Origin"
  }

  object WebSocketProtocol extends DefaultHeaderKey {
    override val name: String = "WebSocket-Protocol"
  }

  object WWWAuthenticate extends HeaderKey[WWWAuthenticate] {
    override val name: String = "WWW-Authenticate"

    protected[this] def collectHeader: PartialFunction[Header, WWWAuthenticate] = {
      case h: WWWAuthenticate => h
    }
    def apply(first: Challenge, more: Challenge*): WWWAuthenticate = apply(first +: more)
  }

  case class WWWAuthenticate(challenges: Seq[Challenge]) extends Header {
    def name = "WWW-Authenticate"

    def lowercaseName = "www-authenticate".lowercase

    def value = challenges.mkString(", ")
  }
  object XForwardedFor extends HeaderKey[XForwardedFor] {
    override val name: String = "X-Forwarded-For"

    protected[this] def collectHeader: PartialFunction[Header, XForwardedFor] = {
      case h: XForwardedFor => h
    }

    def apply(first: InetAddress, more: InetAddress*): XForwardedFor = apply((first +: more).map(Some(_)))
  }

  case class XForwardedFor(ips: Seq[Option[InetAddress]]) extends Header {
    def name = "X-Forwarded-For"

    def lowercaseName = "x-forwarded-for".lowercase

    def value = ips.map(_.fold("unknown")(_.getHostAddress)).mkString(", ")
  }

  object XForwardedProto extends DefaultHeaderKey {
    override val name: String = "X-Forwarded-Proto"
  }

  object XPoweredBy extends DefaultHeaderKey {
    override val name: String = "X-Powered-By"
  }

  case class RawHeader(name: String, value: String) extends Header {
    val lowercaseName = name.lowercase(Locale.US)
    override lazy val parsed: Header = HttpParser.parseHeader(this).fold(_ => this, identity)
  }

  object Key {

    def apply[T <: Header](nm: String, collector: PartialFunction[Header, T]): HeaderKey[T] = new HeaderKey[T] {
      override val name: String = nm

      protected[this] def collectHeader: PartialFunction[Header, T] = collector
    }

    def apply(nm: String): HeaderKey[Header] = new DefaultHeaderKey {
      override val name: String = nm
    }
  }
}
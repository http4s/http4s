package org.http4s

import parser.HttpParser
import scala.collection.{mutable, immutable}
import scala.collection.generic.CanBuildFrom
import util.DateTime

trait HttpHeaderKey[T <: HttpHeader] {
  private[this] val _cn = getClass.getName.split("\\.").last.split("\\$").last.replace("\\$$", "")
  protected[this] def name = _cn
  override def toString: String = name

  def unapply(headers: HttpHeaders): Option[T] =
    (headers find (_ is name.toLowerCase) map (_.parsed)).collectFirst(collectHeader)
  def unapplySeq(headers: HttpHeaders): Option[Seq[T]] =
    Some((headers filter (_ is name.toLowerCase) map (_.parsed)).collect(collectHeader))

  def from(headers: HttpHeaders): Option[T] = unapply(headers)
  def findIn(headers: HttpHeaders): Seq[T] = unapplySeq(headers) getOrElse Seq.empty

  protected[this] def collectHeader: PartialFunction[HttpHeader, T]
}

class HttpHeaders private(headers: Seq[HttpHeader])
  extends immutable.Seq[HttpHeader]
  with collection.SeqLike[HttpHeader, HttpHeaders]
{
  override protected[this] def newBuilder: mutable.Builder[HttpHeader, HttpHeaders] = HttpHeaders.newBuilder

  def length: Int = headers.length

  def apply(idx: Int): HttpHeader = headers(idx)

  def iterator: Iterator[HttpHeader] = headers.iterator

  def apply[T <: HttpHeader](key: HttpHeaderKey[T]) = get(key).get

  def get[T <: HttpHeader](key: HttpHeaderKey[T]): Option[T] = key from this

  def getAll[T <: HttpHeader](key: HttpHeaderKey[T]): Seq[T] = key findIn this
}

abstract class HttpHeader {
  def name: String
  def lowercaseName: String
  def value: String
  def is(nameInLowerCase: String): Boolean = lowercaseName == nameInLowerCase
  def isNot(nameInLowerCase: String): Boolean = lowercaseName != nameInLowerCase
  override def toString = name + ": " + value

  lazy val parsed = this
}

object HttpHeader {
  def unapply(header: HttpHeader): Option[(String, String)] = Some((header.lowercaseName, header.value))
}

object HttpHeaders {


  
  abstract class DefaultHttpHeaderKey extends HttpHeaderKey[HttpHeader] {
    
    protected[this] def collectHeader: PartialFunction[HttpHeader, HttpHeader] = {
      case h => h
    }
    
  }
  

  object Accept { def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more) }
  case class Accept(mediaRanges: Seq[MediaRange]) extends HttpHeader {
    def name = "Accept"
    def lowercaseName = "accept"
    def value = mediaRanges.map(_.value).mkString(", ")
  }

  object `Accept-Charset` { def apply(first: HttpCharsetRange, more: HttpCharsetRange*): `Accept-Charset` = apply(first +: more) }
  case class `Accept-Charset`(charsetRanges: Seq[HttpCharsetRange]) extends HttpHeader {
    def name = "Accept-Charset"
    def lowercaseName = "accept-charset"
    def value = charsetRanges.map(_.value).mkString(", ")
  }

  object `Accept-Encoding` { def apply(first: HttpEncodingRange, more: HttpEncodingRange*): `Accept-Encoding` = apply(first +: more) }
  case class `Accept-Encoding`(encodings: Seq[HttpEncodingRange]) extends HttpHeader {
    def name = "Accept-Encoding"
    def lowercaseName = "accept-encoding"
    def value = encodings.map(_.value).mkString(", ")
  }

  object `Accept-Language` { def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(first +: more) }
  case class `Accept-Language`(languageRanges: Seq[LanguageRange]) extends HttpHeader {
    def name = "Accept-Language"
    def lowercaseName = "accept-language"
    def value = languageRanges.map(_.value).mkString(", ")
  }

  object `Accept-Ranges` { def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more) }
  case class `Accept-Ranges`(rangeUnits: Seq[RangeUnit]) extends HttpHeader {
    def name = "Accept-Ranges"
    def lowercaseName = "accept-ranges"
    def value = if (rangeUnits.isEmpty) "none" else rangeUnits.mkString(", ")
  }

  case class Authorization(credentials: HttpCredentials) extends HttpHeader {
    def name = "Authorization"
    def lowercaseName = "authorization"
    def value = credentials.value
  }

  object `Cache-Control` { def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(first +: more) }
  case class `Cache-Control`(directives: Seq[CacheDirective]) extends HttpHeader {
    def name = "Cache-Control"
    def lowercaseName = "cache-control"
    def value = directives.mkString(", ")
  }

  object Connection { def apply(first: String, more: String*): `Connection` = apply(first +: more) }
  case class Connection(connectionTokens: Seq[String]) extends HttpHeader {
    def name = "Connection"
    def lowercaseName = "connection"
    def value = connectionTokens.mkString(", ")
    def hasClose = connectionTokens.exists(_.toLowerCase == "close")
    def hasKeepAlive = connectionTokens.exists(_.toLowerCase == "keep-alive")
  }

  // see http://tools.ietf.org/html/rfc2183
  case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String]) extends HttpHeader {
    def name = "Content-Disposition"
    def lowercaseName = "content-disposition"
    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  case class `Content-Encoding`(encoding: HttpEncoding) extends HttpHeader {
    def name = "Content-Encoding"
    def lowercaseName = "content-encoding"
    def value = encoding.value
  }

  case class `Content-Length`(length: Int) extends HttpHeader {
    def name = "Content-Length"
    def lowercaseName = "content-length"
    def value = length.toString
  }

  case class `Content-Type`(contentType: ContentType) extends HttpHeader {
    def name = "Content-Type"
    def lowercaseName = "content-type"
    def value = contentType.value
  }

  object Cookie { def apply(first: HttpCookie, more: HttpCookie*): `Cookie` = apply(first +: more) }
  case class Cookie(cookies: Seq[HttpCookie]) extends HttpHeader {
    def name = "Cookie"
    def lowercaseName = "cookie"
    def value = cookies.mkString("; ")
  }

  case class Date(date: DateTime) extends HttpHeader {
    def name = "Date"
    def lowercaseName = "date"
    def value = date.toRfc1123DateTimeString
  }

  object Host { def apply(host: String, port: Int): Host = apply(host, Some(port)) }
  case class Host(host: String, port: Option[Int] = None) extends HttpHeader {
    def name = "Host"
    def lowercaseName = "host"
    def value = port.map(host + ':' + _).getOrElse(host)
  }

  case class `Last-Modified`(date: DateTime) extends HttpHeader {
    def name = "Last-Modified"
    def lowercaseName = "last-modified"
    def value = date.toRfc1123DateTimeString
  }

  case class Location(absoluteUri: String) extends HttpHeader {
    def name = "Location"
    def lowercaseName = "location"
    def value = absoluteUri
  }

  case class `Remote-Address`(ip: HttpIp) extends HttpHeader {
    def name = "Remote-Address"
    def lowercaseName = "remote-address"
    def value = ip.value
  }

  case class `Set-Cookie`(cookie: HttpCookie) extends HttpHeader {
    def name = "Set-Cookie"
    def lowercaseName = "set-cookie"
    def value = cookie.value
  }

  case class `Transfer-Encoding`(coding: HttpEncoding) extends HttpHeader {
    def name = "Transfer-Encoding"
    def lowercaseName = "transfer-encoding"
    def value = coding.value
  }

  object `WWW-Authenticate` { def apply(first: HttpChallenge, more: HttpChallenge*): `WWW-Authenticate` = apply(first +: more) }
  case class `WWW-Authenticate`(challenges: Seq[HttpChallenge]) extends HttpHeader {
    def name = "WWW-Authenticate"
    def lowercaseName = "www-authenticate"
    def value = challenges.mkString(", ")
  }

  object `X-Forwarded-For` { def apply(first: HttpIp, more: HttpIp*): `X-Forwarded-For` = apply((first +: more).map(Some(_))) }
  case class `X-Forwarded-For`(ips: Seq[Option[HttpIp]]) extends HttpHeader {
    def name = "X-Forwarded-For"
    def lowercaseName = "x-forwarded-for"
    def value = ips.map(_.getOrElse("unknown")).mkString(", ")
  }

  case class RawHeader(name: String, value: String) extends HttpHeader {
    val lowercaseName = name.toLowerCase
    override lazy val parsed: HttpHeader = HttpParser.parseHeader(this).fold(_ => this, identity)
  }

  val Empty = apply()
  def empty = Empty

  def apply(headers: HttpHeader*): HttpHeaders = new HttpHeaders(headers)

  implicit def canBuildFrom: CanBuildFrom[Traversable[HttpHeader], HttpHeader, HttpHeaders] =
    new CanBuildFrom[TraversableOnce[HttpHeader], HttpHeader, HttpHeaders] {
      def apply(from: TraversableOnce[HttpHeader]): mutable.Builder[HttpHeader, HttpHeaders] = newBuilder
      def apply(): mutable.Builder[HttpHeader, HttpHeaders] = newBuilder
    }

  private def newBuilder: mutable.Builder[HttpHeader, HttpHeaders] =
    mutable.ListBuffer.newBuilder[HttpHeader] mapResult (new HttpHeaders(_))

  object Keys {
    object Accept extends HttpHeaderKey[Accept] {
      protected[this] def collectHeader: PartialFunction[HttpHeader, Accept] = {
        case h: Accept => h
      }
    }
    
    object AcceptCharset extends HttpHeaderKey[`Accept-Charset`] {
      override protected[this] val name: String = "Accept-Charset"

      protected[this] def collectHeader: PartialFunction[HttpHeader, `Accept-Charset`] =  {
        case h: `Accept-Charset` => h
      }
    }
    object AcceptEncoding extends HttpHeaderKey[`Accept-Encoding`] {
      override protected[this] val name: String = "Accept-Encoding"

      protected[this] def collectHeader: PartialFunction[HttpHeader, `Accept-Encoding`] =  {
        case h: `Accept-Encoding` => h
      }
    }
    object AcceptLanguage extends HttpHeaderKey[`Accept-Language`] {
      override protected[this] val name: String = "Accept-Language"

      protected[this] def collectHeader: PartialFunction[HttpHeader, `Accept-Language`] =  {
        case h: `Accept-Language` => h
      }
    }
    object AcceptRanges extends HttpHeaderKey[`Accept-Ranges`] {
      override protected[this] val name: String = "Accept-Ranges"

      protected[this] def collectHeader: PartialFunction[HttpHeader, `Accept-Ranges`] =  {
        case h: `Accept-Ranges` => h
      }
    }
    object AcceptPatch extends DefaultHttpHeaderKey { override protected[this] val name: String = "Accept-Patch" }
    object AccessControlAllowCredentials extends DefaultHttpHeaderKey { override protected[this] val name: String = "Access-Control-Allow-Credentials" }
    object AccessControlAllowHeaders extends DefaultHttpHeaderKey { override protected[this] val name: String = "Access-Control-Allow-Headers" }
    object AccessControlAllowMethods extends DefaultHttpHeaderKey { override protected[this] val name: String = "Access-Control-Allow-Methods" }
    object AccessControlAllowOrigin extends DefaultHttpHeaderKey { override protected[this] val name: String = "Access-Control-Allow-Origin" }
    object AccessControlExposeHeaders extends DefaultHttpHeaderKey { override protected[this] val name: String = "Access-Control-Expose-Headers" }
    object AccessControlMaxAge extends DefaultHttpHeaderKey { override protected[this] val name: String = "Access-Control-Max-Age" }
    object AccessControlRequestHeaders extends DefaultHttpHeaderKey { override protected[this] val name: String = "Access-Control-Request-Headers" }
    object AccessControlRequestMethod extends DefaultHttpHeaderKey { override protected[this] val name: String = "Access-Control-Request-Method" }
    object Age extends DefaultHttpHeaderKey
    object Allow extends DefaultHttpHeaderKey
    object Authorization extends HttpHeaderKey[Authorization] {
      protected[this] def collectHeader: PartialFunction[HttpHeader, Authorization] = { case h: Authorization => h }
    }
    object CacheControl extends HttpHeaderKey[`Cache-Control`] {

      override protected[this] val name: String = "Cache-Control"

      protected[this] def collectHeader: PartialFunction[HttpHeader, `Cache-Control`] = { case h: `Cache-Control` => h }
    }
    object Connection extends HttpHeaderKey[Connection] {
      protected[this] def collectHeader: PartialFunction[HttpHeader, Connection] = { case h: Connection => h }
    }
    object ContentBase extends DefaultHttpHeaderKey { override protected[this] val name: String = "Content-Base" }
    object ContentDisposition extends HttpHeaderKey[`Content-Disposition`] {
      override protected[this] val name: String = "Content-Disposition"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `Content-Disposition`] = { case h: `Content-Disposition` => h}
    }
   object ContentEncoding extends HttpHeaderKey[`Content-Encoding`] {
      override protected[this] val name: String = "Content-Encoding"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `Content-Encoding`] = { case h: `Content-Encoding` => h}
    }
    object ContentLanguage extends DefaultHttpHeaderKey { override protected[this] val name: String = "Content-Language" }
    object ContentLength extends HttpHeaderKey[`Content-Length`] {
      override protected[this] val name: String = "Content-Length"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `Content-Length`] = { case h: `Content-Length` => h}
    }
    object ContentLocation extends DefaultHttpHeaderKey { override protected[this] val name: String = "Content-Location" }
    object ContentTransferEncoding extends DefaultHttpHeaderKey { override protected[this] val name: String = "Content-Transfer-Encoding" }
    object ContentMd5 extends DefaultHttpHeaderKey { override protected[this] val name: String = "Content-MD5" }
    object ContentRange extends DefaultHttpHeaderKey { override protected[this] val name: String = "Content-Range" }
    object ContentType extends HttpHeaderKey[`Content-Type`] {
      override protected[this] val name: String = "Content-Type"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `Content-Type`] = { case h: `Content-Type` => h}
    }
    object Cookie extends HttpHeaderKey[Cookie] {
      protected[this] def collectHeader: PartialFunction[HttpHeader, Cookie] = { case h: Cookie => h }
    }
    object Date extends HttpHeaderKey[Date] {
      protected[this] def collectHeader: PartialFunction[HttpHeader, Date] = { case h: Date => h }
    }
    object ETag extends DefaultHttpHeaderKey
    object Expect extends DefaultHttpHeaderKey
    object Expires extends DefaultHttpHeaderKey
    object From extends DefaultHttpHeaderKey
    object FrontEndHttps extends DefaultHttpHeaderKey { override protected[this] val name: String = "Front-End-Https" }
    object Host extends HttpHeaderKey[Host] {
      protected[this] def collectHeader: PartialFunction[HttpHeader, Host] = { case h: Host => h }
    }
    object IfMatch extends DefaultHttpHeaderKey { override protected[this] val name: String = "If-Match" }
    object IfModifiedSince extends DefaultHttpHeaderKey { override protected[this] val name: String = "If-Modified-Since" }
    object IfNoneMatch extends DefaultHttpHeaderKey { override protected[this] val name: String = "If-None-Match" }
    object IfRange extends DefaultHttpHeaderKey { override protected[this] val name: String = "If-Range" }
    object IfUnmodifiedSince extends DefaultHttpHeaderKey { override protected[this] val name: String = "If-Unmodified-Since" }
    object LastModified extends HttpHeaderKey[`Last-Modified`] {
      override protected[this] val name: String = "Last-Modified"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `Last-Modified`] = { case h: `Last-Modified` => h}
    }
    object Location extends HttpHeaderKey[Location] {
      protected[this] def collectHeader: PartialFunction[HttpHeader, Location] = { case h: Location => h }
    }
    object MaxForwards extends DefaultHttpHeaderKey { override protected[this] val name: String = "Max-Forwards" }
    object Origin extends DefaultHttpHeaderKey
    object Pragma extends DefaultHttpHeaderKey
    object ProxyAuthenticate extends DefaultHttpHeaderKey { override protected[this] val name: String = "Proxy-Authenticate" }
    object ProxyAuthorization extends DefaultHttpHeaderKey { override protected[this] val name: String = "Proxy-Authorization" }
    object Range extends DefaultHttpHeaderKey
    object Referer extends DefaultHttpHeaderKey
    object RemoteAddress extends HttpHeaderKey[`Remote-Address`] {
      override protected[this] val name: String = "Remote-Address"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `Remote-Address`] = { case h: `Remote-Address` => h }
    }
    object RetryAfter extends DefaultHttpHeaderKey { override protected[this] val name: String = "Retry-After" }
    object SecWebSocketKey extends DefaultHttpHeaderKey { override protected[this] val name: String = "Sec-WebSocket-Key" }
    object SecWebSocketKey1 extends DefaultHttpHeaderKey { override protected[this] val name: String = "Sec-WebSocket-Key1" }
    object SecWebSocketKey2 extends DefaultHttpHeaderKey { override protected[this] val name: String = "Sec-WebSocket-Key2" }
    object SecWebSocketLocation extends DefaultHttpHeaderKey { override protected[this] val name: String = "Sec-WebSocket-Location" }
    object SecWebSocketOrigin extends DefaultHttpHeaderKey { override protected[this] val name: String = "Sec-WebSocket-Origin" }
    object SecWebSocketProtocol extends DefaultHttpHeaderKey { override protected[this] val name: String = "Sec-WebSocket-Protocol" }
    object SecWebSocketVersion extends DefaultHttpHeaderKey { override protected[this] val name: String = "Sec-WebSocket-Version" }
    object SecWebSocketAccept extends DefaultHttpHeaderKey { override protected[this] val name: String = "Sec-WebSocket-Accept" }
    object Server extends DefaultHttpHeaderKey
    object SetCookie extends HttpHeaderKey[`Set-Cookie`] {
      override protected[this] val name: String = "Set-Cookie"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `Set-Cookie`] = { case h: `Set-Cookie` => h }
    }
    object SetCookie2 extends DefaultHttpHeaderKey { override protected[this] val name: String = "Set-Cookie2" }
    object TE extends DefaultHttpHeaderKey
    object Trailer extends DefaultHttpHeaderKey
    object TransferEncoding extends HttpHeaderKey[`Transfer-Encoding`] {
      override protected[this] val name: String = "Transfer-Encoding"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `Transfer-Encoding`] = { case h: `Transfer-Encoding` => h }
    }
    object Upgrade extends DefaultHttpHeaderKey
    object UserAgent extends DefaultHttpHeaderKey { override protected[this] val name: String = "User-Agent" }
    object Vary extends DefaultHttpHeaderKey
    object Via extends DefaultHttpHeaderKey
    object Warning extends DefaultHttpHeaderKey
    object WebSocketLocation extends DefaultHttpHeaderKey { override protected[this] val name: String = "WebSocket-Location" }
    object WebSocketOrigin extends DefaultHttpHeaderKey { override protected[this] val name: String = "WebSocket-Origin" }
    object WebSocketProtocol extends DefaultHttpHeaderKey { override protected[this] val name: String = "WebSocket-Protocol" }
    object WWWAuthenticate extends HttpHeaderKey[`WWW-Authenticate`] {
      override protected[this] val name: String = "WWW-Authenticate"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `WWW-Authenticate`] = { case h: `WWW-Authenticate` => h }
    }
    object XForwardedFor extends HttpHeaderKey[`X-Forwarded-For`] {
      override protected[this] val name: String = "X-Forwarded-For"
      protected[this] def collectHeader: PartialFunction[HttpHeader, `X-Forwarded-For`] = { case h: `X-Forwarded-For` => h }
    }
    object XForwardedProto extends DefaultHttpHeaderKey { override protected[this] val name: String = "X-Forwarded-Proto" }
    object XPoweredBy extends DefaultHttpHeaderKey { override protected[this] val name: String = "X-Powered-By" }

    def apply[T <: HttpHeader](nm: String, collector: PartialFunction[HttpHeader, T]): HttpHeaderKey[T] = new HttpHeaderKey[T] {
      override protected[this] val name: String = nm
      protected[this] def collectHeader: PartialFunction[HttpHeader, T] = collector
    }

    def apply(nm: String): HttpHeaderKey[HttpHeader] = new DefaultHttpHeaderKey {
      override protected[this] val name: String = nm
    }
  }

  object Names {

    val Accept = "Accept"
    val AcceptCharset = "Accept-Charset"
    val AcceptEncoding = "Accept-Encoding"
    val AcceptLanguage = "Accept-Language"
    val AcceptRanges = "Accept-Ranges"
    val AcceptPatch = "Accept-Patch"
    val AccessControlAllowCredentials = "Access-Control-Allow-Credentials"
    val AccessControlAllowHeaders = "Access-Control-Allow-Headers"
    val AccessControlAllowMethods = "Access-Control-Allow-Methods"
    val AccessControlAllowOrigin = "Access-Control-Allow-Origin"
    val AccessControlExposeHeaders = "Access-Control-Expose-Headers"
    val AccessControlMaxAge = "Access-Control-Max-Age"
    val AccessControlRequestHeaders = "Access-Control-Request-Headers"
    val AccessControlRequestMethod = "Access-Control-Request-Method"
    val Age = "Age"
    val Allow = "Allow"
    val Authorization = "Authorization"
    val CacheControl = "Cache-Control"
    val Connection = "Connection"
    val ContentBase = "Content-Base"
    val ContentDisposition = "Content-Disposition"
    val ContentEncoding = "Content-Encoding"
    val ContentLanguage = "Content-Language"
    val ContentLength = "Content-Length"
    val ContentLocation = "Content-Location"
    val ContentTransferEncoding = "Content-Transfer-Encoding"
    val ContentMd5 = "Content-MD5"
    val ContentRange = "Content-Range"
    val ContentType = "Content-Type"
    val Cookie = "Cookie"
    val Date = "Date"
    val ETag = "ETag"
    val Expect = "Expect"
    val Expires = "Expires"
    val From = "From"
    val FrontEndHttps = "Front-End-Https"
    val Host = "Host"
    val IfMatch = "If-Match"
    val IfModifiedSince = "If-Modified-Since"
    val IfNoneMatch = "If-None-Match"
    val IfRange = "If-Range"
    val IfUnmodifiedSince = "If-Unmodified-Since"
    val LastModified = "Last-Modified"
    val Location = "Location"
    val MaxForwards = "Max-Forwards"
    val Origin = "Origin"
    val Pragma = "Pragma"
    val ProxyAuthenticate = "Proxy-Authenticate"
    val ProxyAuthorization = "Proxy-Authorization"
    val Range = "Range"
    val Referer = "Referer"
    val RemoteAddress = "Remote-Address"
    val RetryAfter = "Retry-After"
    val SecWebSocketKey = "Sec-WebSocket-Key"
    val SecWebSocketKey1 = "Sec-WebSocket-Key1"
    val SecWebSocketKey2 = "Sec-WebSocket-Key2"
    val SecWebSocketLocation = "Sec-WebSocket-Location"
    val SecWebSocketOrigin = "Sec-WebSocket-Origin"
    val SecWebSocketProtocol = "Sec-WebSocket-Protocol"
    val SecWebSocketVersion = "Sec-WebSocket-Version"
    val SecWebSocketAccept = "Sec-WebSocket-Accept"
    val Server = "Server"
    val SetCookie = "Set-Cookie"
    val SetCookie2 = "Set-Cookie2"
    val TE = "TE"
    val Trailer = "Trailer"
    val TransferEncoding = "Transfer-Encoding"
    val Upgrade = "Upgrade"
    val UserAgent = "User-Agent"
    val Vary = "Vary"
    val Via = "Via"
    val Warning = "Warning"
    val WebSocketLocation = "WebSocket-Location"
    val WebSocketOrigin = "WebSocket-Origin"
    val WebSocketProtocol = "WebSocket-Protocol"
    val WWWAuthenticate = "WWW-Authenticate"
    val XForwardedFor = "X-Forwarded-For"
    val XForwardedProto = "X-Forwarded-Proto"
    val XPoweredBy = "X-Powered-By"

  }

}
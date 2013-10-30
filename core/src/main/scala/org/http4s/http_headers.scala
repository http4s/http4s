package org.http4s

import parser.HttpParser
import scala.collection.{mutable, immutable}
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import org.joda.time.DateTime

trait HttpHeaderKey[T <: HttpHeader] {
  private[this] val _cn = getClass.getName.split("\\.").last.split("\\$").last.replace("\\$$", "")

  def name = _cn

  override def toString: String = name

  def unapply(headers: HttpHeaders): Option[T] =
    (headers find (_ is name.toLowerCase) map (_.parsed)).collectFirst(collectHeader)

  def unapplySeq(headers: HttpHeaders): Option[Seq[T]] =
    Some((headers filter (_ is name.toLowerCase) map (_.parsed)).collect(collectHeader))

  def from(headers: HttpHeaders): Option[T] = unapply(headers)

  def findIn(headers: HttpHeaders): Seq[T] = unapplySeq(headers) getOrElse Seq.empty

  protected[this] def collectHeader: PartialFunction[HttpHeader, T]
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

class HttpHeaders private(headers: List[HttpHeader])
  extends immutable.Seq[HttpHeader]
  with collection.SeqLike[HttpHeader, HttpHeaders] {
  override protected[this] def newBuilder: mutable.Builder[HttpHeader, HttpHeaders] = HttpHeaders.newBuilder

  def length: Int = headers.length

  def apply(idx: Int): HttpHeader = headers(idx)

  def iterator: Iterator[HttpHeader] = headers.iterator

  def apply[T <: HttpHeader](key: HttpHeaderKey[T]) = get(key).get

  def get[T <: HttpHeader](key: HttpHeaderKey[T]): Option[T] = key from this

  def getAll[T <: HttpHeader](key: HttpHeaderKey[T]): Seq[T] = key findIn this

  def put(header: HttpHeader): HttpHeaders = {
    @tailrec
    def findNext(l: List[HttpHeader]): List[HttpHeader] = { // Get the list headed by the first match or Nil
      if (l.isEmpty) Nil
      else if (l.head.name == header.name) l
      else findNext(l.tail)
    }

    val firstMatch = findNext(headers)
    
    if (firstMatch.isEmpty) new HttpHeaders(header::headers)
    else {       // Copy the headers up to that point, and then append the remaining
      val builder = new ListBuffer[HttpHeader]
      builder += header
      @tailrec
      def go(l: List[HttpHeader]): List[HttpHeader] = {
        if (l eq firstMatch) builder.prependToList(l.tail) // We are at the header to drop
        else {
          builder += l.head
          go(l.tail)
        }
      }

      new HttpHeaders(go(headers))
    }
  }
}

object HttpHeaders {


  abstract class DefaultHttpHeaderKey extends HttpHeaderKey[HttpHeader] {

    protected[this] def collectHeader: PartialFunction[HttpHeader, HttpHeader] = {
      case h => h
    }

  }


  object Accept extends HttpHeaderKey[Accept] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Accept] = {
      case h: Accept => h
    }

    def apply(first: MediaRange, more: MediaRange*): Accept = apply(first +: more)
  }

  case class Accept(mediaRanges: Seq[MediaRange]) extends HttpHeader {
    def name = "Accept"

    def lowercaseName = "accept"

    def value = mediaRanges.map(_.value).mkString(", ")
  }

  object AcceptCharset extends HttpHeaderKey[AcceptCharset] {
    override val name: String = "Accept-Charset"

    protected[this] def collectHeader: PartialFunction[HttpHeader, AcceptCharset] = {
      case h: AcceptCharset => h
    }

    def apply(first: HttpCharsetRange, more: HttpCharsetRange*): AcceptCharset = apply(first +: more)
  }
  case class AcceptCharset(charsetRanges: Seq[HttpCharsetRange]) extends HttpHeader {
    def name = "Accept-Charset"

    def lowercaseName = "accept-charset"

    def value = charsetRanges.map(_.value).mkString(", ")
  }

  object AcceptEncoding extends HttpHeaderKey[AcceptEncoding] {
    override val name: String = "Accept-Encoding"

    protected[this] def collectHeader: PartialFunction[HttpHeader, AcceptEncoding] = {
      case h: AcceptEncoding => h
    }

    def apply(first: HttpEncodingRange, more: HttpEncodingRange*): AcceptEncoding = apply(first +: more)
  }

  case class AcceptEncoding(encodings: Seq[HttpEncodingRange]) extends HttpHeader {
    def name = "Accept-Encoding"

    def lowercaseName = "accept-encoding"

    def value = encodings.map(_.value).mkString(", ")
  }

  object AcceptLanguage extends HttpHeaderKey[AcceptLanguage] {
    override val name: String = "Accept-Language"

    protected[this] def collectHeader: PartialFunction[HttpHeader, AcceptLanguage] = {
      case h: AcceptLanguage => h
    }

    def apply(first: LanguageRange, more: LanguageRange*): AcceptLanguage = apply(first +: more)
  }

  case class AcceptLanguage(languageRanges: Seq[LanguageRange]) extends HttpHeader {
    def name = "Accept-Language"

    def lowercaseName = "accept-language"

    def value = languageRanges.map(_.value).mkString(", ")
  }

  object AcceptRanges extends HttpHeaderKey[AcceptRanges] {
    override val name: String = "Accept-Ranges"

    protected[this] def collectHeader: PartialFunction[HttpHeader, AcceptRanges] = {
      case h: AcceptRanges => h
    }

    def apply(first: RangeUnit, more: RangeUnit*): AcceptRanges = apply(first +: more)
  }

  case class AcceptRanges(rangeUnits: Seq[RangeUnit]) extends HttpHeader {
    def name = "Accept-Ranges"

    def lowercaseName = "accept-ranges"

    def value = if (rangeUnits.isEmpty) "none" else rangeUnits.mkString(", ")
  }

  object AcceptPatch extends DefaultHttpHeaderKey {
    override val name: String = "Accept-Patch"
  }

  object AccessControlAllowCredentials extends DefaultHttpHeaderKey {
    override val name: String = "Access-Control-Allow-Credentials"
  }

  object AccessControlAllowHeaders extends DefaultHttpHeaderKey {
    override val name: String = "Access-Control-Allow-Headers"
  }

  object AccessControlAllowMethods extends DefaultHttpHeaderKey {
    override val name: String = "Access-Control-Allow-Methods"
  }

  object AccessControlAllowOrigin extends DefaultHttpHeaderKey {
    override val name: String = "Access-Control-Allow-Origin"
  }

  object AccessControlExposeHeaders extends DefaultHttpHeaderKey {
    override val name: String = "Access-Control-Expose-Headers"
  }

  object AccessControlMaxAge extends DefaultHttpHeaderKey {
    override val name: String = "Access-Control-Max-Age"
  }

  object AccessControlRequestHeaders extends DefaultHttpHeaderKey {
    override val name: String = "Access-Control-Request-Headers"
  }

  object AccessControlRequestMethod extends DefaultHttpHeaderKey {
    override val name: String = "Access-Control-Request-Method"
  }

  object Age extends DefaultHttpHeaderKey


  object Allow extends DefaultHttpHeaderKey

  object Authorization extends HttpHeaderKey[Authorization] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Authorization] = {
      case h: Authorization => h
    }
  }
  case class Authorization(credentials: HttpCredentials) extends HttpHeader {
    def name = "Authorization"

    def lowercaseName = "authorization"

    def value = credentials.value
  }
  object CacheControl extends HttpHeaderKey[CacheControl] {

      override val name: String = "Cache-Control"

      protected[this] def collectHeader: PartialFunction[HttpHeader, CacheControl] = {
        case h: CacheControl => h
      }

    def apply(first: CacheDirective, more: CacheDirective*): CacheControl = apply(first +: more)
  }

  case class CacheControl(directives: Seq[CacheDirective]) extends HttpHeader {
    def name = "Cache-Control"

    def lowercaseName = "cache-control"

    def value = directives.mkString(", ")
  }

  object Connection extends HttpHeaderKey[Connection] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Connection] = {
      case h: Connection => h
    }
    def apply(first: String, more: String*): `Connection` = apply(first +: more)
  }

  case class Connection(connectionTokens: Seq[String]) extends HttpHeader {
    def name = "Connection"

    def lowercaseName = "connection"

    def value = connectionTokens.mkString(", ")

    def hasClose = connectionTokens.exists(_.toLowerCase == "close")

    def hasKeepAlive = connectionTokens.exists(_.toLowerCase == "keep-alive")
  }

  object ContentBase extends DefaultHttpHeaderKey {
    override val name: String = "Content-Base"
  }

  object ContentDisposition extends HttpHeaderKey[ContentDisposition] {
    override val name: String = "Content-Disposition"
    protected[this] def collectHeader: PartialFunction[HttpHeader, ContentDisposition] = { case h: ContentDisposition => h}
  }

  // see http://tools.ietf.org/html/rfc2183
  case class ContentDisposition(dispositionType: String, parameters: Map[String, String]) extends HttpHeader {
    def name = "Content-Disposition"

    def lowercaseName = "content-disposition"

    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  object ContentEncoding extends HttpHeaderKey[ContentEncoding] {
    override val name: String = "Content-Encoding"
    protected[this] def collectHeader: PartialFunction[HttpHeader, ContentEncoding] = { case h: ContentEncoding => h}
  }
  case class ContentEncoding(encoding: HttpEncoding) extends HttpHeader {
    def name = "Content-Encoding"

    def lowercaseName = "content-encoding"

    def value = encoding.value
  }

  object ContentLanguage extends DefaultHttpHeaderKey { override val name: String = "Content-Language" }
  object ContentLength extends HttpHeaderKey[ContentLength] {
    override val name: String = "Content-Length"
    protected[this] def collectHeader: PartialFunction[HttpHeader, ContentLength] = { case h: ContentLength => h }
  }

  case class ContentLength(length: Int) extends HttpHeader {
    def name = "Content-Length"

    def lowercaseName = "content-length"

    def value = length.toString
  }

  object ContentLocation extends DefaultHttpHeaderKey {
    override val name: String = "Content-Location"
  }

  object ContentTransferEncoding extends DefaultHttpHeaderKey {
    override val name: String = "Content-Transfer-Encoding"
  }

  object ContentMd5 extends DefaultHttpHeaderKey {
    override val name: String = "Content-MD5"
  }

  object ContentRange extends DefaultHttpHeaderKey {
    override val name: String = "Content-Range"
  }

  object ContentType extends HttpHeaderKey[ContentType] {
    override val name: String = "Content-Type"

    protected[this] def collectHeader: PartialFunction[HttpHeader, ContentType] = {
      case h: ContentType => h
    }
  }
  case class ContentType(contentType: org.http4s.ContentType) extends HttpHeader {
    def name = "Content-Type"

    def lowercaseName = "content-type"

    def value = contentType.value
  }


  object Cookie extends HttpHeaderKey[Cookie] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Cookie] = {
      case h: Cookie => h
    }

    def apply(first: HttpCookie, more: HttpCookie*): `Cookie` = apply(first +: more)
  }

  case class Cookie(cookies: Seq[HttpCookie]) extends HttpHeader {
    def name = "Cookie"

    def lowercaseName = "cookie"

    def value = cookies.mkString("; ")
  }

  object Date extends HttpHeaderKey[Date] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Date] = {
      case h: Date => h
    }
  }

  case class Date(date: DateTime) extends HttpHeader {
    def name = "Date"

    def lowercaseName = "date"

    def value = date.formatRfc1123
  }

  object ETag extends DefaultHttpHeaderKey

  object Expect extends DefaultHttpHeaderKey

  object Expires extends DefaultHttpHeaderKey

  object From extends DefaultHttpHeaderKey

  object FrontEndHttps extends DefaultHttpHeaderKey {
    override val name: String = "Front-End-Https"
  }

  object Host extends HttpHeaderKey[Host] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Host] = {
      case h: Host => h
    }
    def apply(host: String, port: Int): Host = apply(host, Some(port))
  }

  case class Host(host: String, port: Option[Int] = None) extends HttpHeader {
    def name = "Host"

    def lowercaseName = "host"

    def value = port.map(host + ':' + _).getOrElse(host)
  }

  object IfMatch extends DefaultHttpHeaderKey {
    override val name: String = "If-Match"
  }

  object IfModifiedSince extends DefaultHttpHeaderKey {
    override val name: String = "If-Modified-Since"
  }

  object IfNoneMatch extends DefaultHttpHeaderKey {
    override val name: String = "If-None-Match"
  }

  object IfRange extends DefaultHttpHeaderKey {
    override val name: String = "If-Range"
  }

  object IfUnmodifiedSince extends DefaultHttpHeaderKey {
    override val name: String = "If-Unmodified-Since"
  }

  object LastModified extends HttpHeaderKey[LastModified] {
    override val name: String = "Last-Modified"

    protected[this] def collectHeader: PartialFunction[HttpHeader, LastModified] = {
      case h: LastModified => h
    }
  }

  case class LastModified(date: DateTime) extends HttpHeader {
    def name = "Last-Modified"

    def lowercaseName = "last-modified"

    def value = date.formatRfc1123
  }

  object Location extends HttpHeaderKey[Location] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Location] = {
      case h: Location => h
    }
  }

  case class Location(absoluteUri: String) extends HttpHeader {
    def name = "Location"

    def lowercaseName = "location"

    def value = absoluteUri
  }

  object MaxForwards extends DefaultHttpHeaderKey {
    override val name: String = "Max-Forwards"
  }

  object Origin extends DefaultHttpHeaderKey

  object Pragma extends DefaultHttpHeaderKey

  object ProxyAuthenticate extends DefaultHttpHeaderKey {
    override val name: String = "Proxy-Authenticate"
  }

  object ProxyAuthorization extends DefaultHttpHeaderKey {
    override val name: String = "Proxy-Authorization"
  }

  object Range extends DefaultHttpHeaderKey

  object Referer extends DefaultHttpHeaderKey

  object RemoteAddress extends HttpHeaderKey[RemoteAddress] {
    override val name: String = "Remote-Address"

    protected[this] def collectHeader: PartialFunction[HttpHeader, RemoteAddress] = {
      case h: RemoteAddress => h
    }
  }

  case class RemoteAddress(ip: HttpIp) extends HttpHeader {
    def name = "Remote-Address"

    def lowercaseName = "remote-address"

    def value = ip.value
  }

  object RetryAfter extends DefaultHttpHeaderKey {
    override val name: String = "Retry-After"
  }

  object SecWebSocketKey extends DefaultHttpHeaderKey {
    override val name: String = "Sec-WebSocket-Key"
  }

  object SecWebSocketKey1 extends DefaultHttpHeaderKey {
    override val name: String = "Sec-WebSocket-Key1"
  }

  object SecWebSocketKey2 extends DefaultHttpHeaderKey {
    override val name: String = "Sec-WebSocket-Key2"
  }

  object SecWebSocketLocation extends DefaultHttpHeaderKey {
    override val name: String = "Sec-WebSocket-Location"
  }

  object SecWebSocketOrigin extends DefaultHttpHeaderKey {
    override val name: String = "Sec-WebSocket-Origin"
  }

  object SecWebSocketProtocol extends DefaultHttpHeaderKey {
    override val name: String = "Sec-WebSocket-Protocol"
  }

  object SecWebSocketVersion extends DefaultHttpHeaderKey {
    override val name: String = "Sec-WebSocket-Version"
  }

  object SecWebSocketAccept extends DefaultHttpHeaderKey {
    override val name: String = "Sec-WebSocket-Accept"
  }

  object Server extends DefaultHttpHeaderKey

  object SetCookie extends HttpHeaderKey[SetCookie] {
    override val name: String = "Set-Cookie"

    protected[this] def collectHeader: PartialFunction[HttpHeader, SetCookie] = {
      case h: SetCookie => h
    }
  }
  case class SetCookie(cookie: HttpCookie) extends HttpHeader {
    def name = "Set-Cookie"

    def lowercaseName = "set-cookie"

    def value = cookie.value
  }


  object SetCookie2 extends DefaultHttpHeaderKey {
    override val name: String = "Set-Cookie2"
  }

  object TE extends DefaultHttpHeaderKey

  object Trailer extends DefaultHttpHeaderKey

  object TransferEncoding extends HttpHeaderKey[TransferEncoding] {
    override val name: String = "Transfer-Encoding"

    protected[this] def collectHeader: PartialFunction[HttpHeader, TransferEncoding] = {
      case h: TransferEncoding => h
    }
  }
  case class TransferEncoding(coding: HttpEncoding) extends HttpHeader {
    def name = "Transfer-Encoding"

    def lowercaseName = "transfer-encoding"

    def value = coding.value
  }

  object Upgrade extends DefaultHttpHeaderKey

  object UserAgent extends DefaultHttpHeaderKey {
    override val name: String = "User-Agent"
  }

  object Vary extends DefaultHttpHeaderKey

  object Via extends DefaultHttpHeaderKey

  object Warning extends DefaultHttpHeaderKey

  object WebSocketLocation extends DefaultHttpHeaderKey {
    override val name: String = "WebSocket-Location"
  }

  object WebSocketOrigin extends DefaultHttpHeaderKey {
    override val name: String = "WebSocket-Origin"
  }

  object WebSocketProtocol extends DefaultHttpHeaderKey {
    override val name: String = "WebSocket-Protocol"
  }

  object WWWAuthenticate extends HttpHeaderKey[WWWAuthenticate] {
    override val name: String = "WWW-Authenticate"

    protected[this] def collectHeader: PartialFunction[HttpHeader, WWWAuthenticate] = {
      case h: WWWAuthenticate => h
    }
    def apply(first: HttpChallenge, more: HttpChallenge*): WWWAuthenticate = apply(first +: more)
  }

  case class WWWAuthenticate(challenges: Seq[HttpChallenge]) extends HttpHeader {
    def name = "WWW-Authenticate"

    def lowercaseName = "www-authenticate"

    def value = challenges.mkString(", ")
  }
  object XForwardedFor extends HttpHeaderKey[XForwardedFor] {
    override val name: String = "X-Forwarded-For"

    protected[this] def collectHeader: PartialFunction[HttpHeader, XForwardedFor] = {
      case h: XForwardedFor => h
    }

    def apply(first: HttpIp, more: HttpIp*): XForwardedFor = apply((first +: more).map(Some(_)))
  }

  case class XForwardedFor(ips: Seq[Option[HttpIp]]) extends HttpHeader {
    def name = "X-Forwarded-For"

    def lowercaseName = "x-forwarded-for"

    def value = ips.map(_.getOrElse("unknown")).mkString(", ")
  }

  object XForwardedProto extends DefaultHttpHeaderKey {
    override val name: String = "X-Forwarded-Proto"
  }

  object XPoweredBy extends DefaultHttpHeaderKey {
    override val name: String = "X-Powered-By"
  }

  case class RawHeader(name: String, value: String) extends HttpHeader {
    val lowercaseName = name.toLowerCase
    override lazy val parsed: HttpHeader = HttpParser.parseHeader(this).fold(_ => this, identity)
  }

  val Empty = apply()

  def empty = Empty

  def apply(headers: HttpHeader*): HttpHeaders =  new HttpHeaders(headers.toList)

  implicit def canBuildFrom: CanBuildFrom[Traversable[HttpHeader], HttpHeader, HttpHeaders] =
    new CanBuildFrom[TraversableOnce[HttpHeader], HttpHeader, HttpHeaders] {
      def apply(from: TraversableOnce[HttpHeader]): mutable.Builder[HttpHeader, HttpHeaders] = newBuilder

      def apply(): mutable.Builder[HttpHeader, HttpHeaders] = newBuilder
    }

  private def newBuilder: mutable.Builder[HttpHeader, HttpHeaders] =
    mutable.ListBuffer.newBuilder[HttpHeader] mapResult (b => new HttpHeaders(b.result()))

  object Key {

    def apply[T <: HttpHeader](nm: String, collector: PartialFunction[HttpHeader, T]): HttpHeaderKey[T] = new HttpHeaderKey[T] {
      override val name: String = nm

      protected[this] def collectHeader: PartialFunction[HttpHeader, T] = collector
    }

    def apply(nm: String): HttpHeaderKey[HttpHeader] = new DefaultHttpHeaderKey {
      override val name: String = nm
    }
  }


}
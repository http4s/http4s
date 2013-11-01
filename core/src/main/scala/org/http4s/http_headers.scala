package org.http4s

import parser.HttpParser
import scala.collection.{mutable, immutable}
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import org.joda.time.DateTime
import java.net.InetAddress
import java.util.Locale
import org.http4s.util.Lowercase
import scalaz.@@

trait HttpHeaderKey[T <: HttpHeader] {
  private[this] val _cn = getClass.getName.split("\\.").last.split("\\$").last.replace("\\$$", "")

  def name = _cn

  override def toString: String = name

  def unapply(headers: HttpHeaders): Option[T] =
    (headers find (_ is name.lowercase(Locale.US)) map (_.parsed)).collectFirst(collectHeader)

  def unapplySeq(headers: HttpHeaders): Option[Seq[T]] =
    Some((headers filter (_ is name.lowercase(Locale.US)) map (_.parsed)).collect(collectHeader))

  def from(headers: HttpHeaders): Option[T] = unapply(headers)

  def findIn(headers: HttpHeaders): Seq[T] = unapplySeq(headers) getOrElse Seq.empty

  protected[this] def collectHeader: PartialFunction[HttpHeader, T]
}

abstract class HttpHeader {
  def name: String

  def lowercaseName: String @@ Lowercase

  def value: String

  def is(name: String @@ Lowercase): Boolean = this.lowercaseName == name

  def isNot(name: String @@ Lowercase): Boolean = this.lowercaseName != name

  override def toString = name + ": " + value

  lazy val parsed = this
}

object HttpHeader {
  def unapply(header: HttpHeader): Option[(String, String)] = Some((header.lowercaseName, header.value))
}

sealed abstract class HttpHeaders
  extends immutable.LinearSeq[HttpHeader]
  with collection.LinearSeqOptimized[HttpHeader, HttpHeaders] {
  import HttpHeaders.{Cons, Nil}

  override protected[this] def newBuilder: mutable.Builder[HttpHeader, HttpHeaders] = HttpHeaders.newBuilder

  def apply[T <: HttpHeader](key: HttpHeaderKey[T]) = get(key).get

  def get[T <: HttpHeader](key: HttpHeaderKey[T]): Option[T] = key from this

  def getAll[T <: HttpHeader](key: HttpHeaderKey[T]): Seq[T] = key findIn this

  def replace(header: HttpHeader): HttpHeaders = {
    val builder = newBuilder
    @tailrec
    def loop(headers: HttpHeaders, replaced: Boolean): HttpHeaders = headers match {
      case Nil =>
        if (!replaced) builder += header
        builder.result()
      case Cons(h, rest) if h.name == header.name =>
        if (!replaced) builder += header
        loop(rest, true)
      case Cons(h, rest) =>
        builder += h
        loop(rest, replaced)
    }
    loop(this, false)
  }
}

object HttpHeaders {
  case object Nil extends HttpHeaders {
    override def isEmpty: Boolean = true
  }
  final case class Cons(override val head: HttpHeader, override val tail: HttpHeaders) extends HttpHeaders {
    override def isEmpty: Boolean = false
  }

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

    def lowercaseName = "accept".lowercase

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

    def lowercaseName = "accept-charset".lowercase

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

    def lowercaseName = "accept-encoding".lowercase

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

    def lowercaseName = "accept-language".lowercase

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

    def lowercaseName = "accept-ranges".lowercase

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

    def lowercaseName = "authorization".lowercase

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

    def lowercaseName = "cache-control".lowercase

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

    def lowercaseName = "connection".lowercase

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

    def lowercaseName = "content-disposition".lowercase

    def value = parameters.map(p => "; " + p._1 + "=\"" + p._2 + '"').mkString(dispositionType, "", "")
  }

  object ContentEncoding extends HttpHeaderKey[ContentEncoding] {
    override val name: String = "Content-Encoding"
    protected[this] def collectHeader: PartialFunction[HttpHeader, ContentEncoding] = { case h: ContentEncoding => h}
  }
  case class ContentEncoding(encoding: HttpEncoding) extends HttpHeader {
    def name = "Content-Encoding"

    def lowercaseName = "content-encoding".lowercase

    def value = encoding.value
  }

  object ContentLanguage extends DefaultHttpHeaderKey { override val name: String = "Content-Language" }
  object ContentLength extends HttpHeaderKey[ContentLength] {
    override val name: String = "Content-Length"
    protected[this] def collectHeader: PartialFunction[HttpHeader, ContentLength] = { case h: ContentLength => h }
  }

  case class ContentLength(length: Int) extends HttpHeader {
    def name = "Content-Length"

    def lowercaseName = "content-length".lowercase

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

    def lowercaseName = "content-type".lowercase

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

    def lowercaseName = "cookie".lowercase

    def value = cookies.mkString("; ")
  }

  object Date extends HttpHeaderKey[Date] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Date] = {
      case h: Date => h
    }
  }

  case class Date(date: DateTime) extends HttpHeader {
    def name = "Date"

    def lowercaseName = "date".lowercase

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

    def lowercaseName = "host".lowercase

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

    def lowercaseName = "last-modified".lowercase

    def value = date.formatRfc1123
  }

  object Location extends HttpHeaderKey[Location] {
    protected[this] def collectHeader: PartialFunction[HttpHeader, Location] = {
      case h: Location => h
    }
  }

  case class Location(absoluteUri: String) extends HttpHeader {
    def name = "Location"

    def lowercaseName = "location".lowercase

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

    def lowercaseName = "set-cookie".lowercase

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

    def lowercaseName = "transfer-encoding".lowercase

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

    def lowercaseName = "www-authenticate".lowercase

    def value = challenges.mkString(", ")
  }
  object XForwardedFor extends HttpHeaderKey[XForwardedFor] {
    override val name: String = "X-Forwarded-For"

    protected[this] def collectHeader: PartialFunction[HttpHeader, XForwardedFor] = {
      case h: XForwardedFor => h
    }

    def apply(first: InetAddress, more: InetAddress*): XForwardedFor = apply((first +: more).map(Some(_)))
  }

  case class XForwardedFor(ips: Seq[Option[InetAddress]]) extends HttpHeader {
    def name = "X-Forwarded-For"

    def lowercaseName = "x-forwarded-for".lowercase

    def value = ips.map(_.fold("unknown")(_.getHostAddress)).mkString(", ")
  }

  object XForwardedProto extends DefaultHttpHeaderKey {
    override val name: String = "X-Forwarded-Proto"
  }

  object XPoweredBy extends DefaultHttpHeaderKey {
    override val name: String = "X-Powered-By"
  }

  case class RawHeader(name: String, value: String) extends HttpHeader {
    val lowercaseName = name.lowercase(Locale.US)
    override lazy val parsed: HttpHeader = HttpParser.parseHeader(this).fold(_ => this, identity)
  }

  def apply(headers: HttpHeader*): HttpHeaders = if (headers.isEmpty) Nil else (newBuilder ++= headers).result

  val empty = Nil

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[HttpHeader], HttpHeader, HttpHeaders] =
    new CanBuildFrom[TraversableOnce[HttpHeader], HttpHeader, HttpHeaders] {
      def apply(from: TraversableOnce[HttpHeader]): mutable.Builder[HttpHeader, HttpHeaders] = newBuilder

      def apply(): mutable.Builder[HttpHeader, HttpHeaders] = newBuilder
    }

  private def newBuilder: mutable.Builder[HttpHeader, HttpHeaders] = new mutable.Builder[HttpHeader, HttpHeaders] {
    private val buffer = mutable.ArrayBuffer.newBuilder[HttpHeader]
    def +=(elem: HttpHeader): this.type = {
      buffer += elem
      this
    }
    def clear(): Unit = buffer.clear()
    def result(): HttpHeaders = buffer.result.foldRight(Nil: HttpHeaders)(Cons(_, _))
  }

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
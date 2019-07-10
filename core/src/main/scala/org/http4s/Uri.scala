package org.http4s

import cats.{Eq, Hash, Order, Show}
import cats.implicits._
import java.net.{Inet4Address, Inet6Address, InetAddress}
import java.nio.ByteBuffer
import java.nio.charset.{Charset => NioCharset, StandardCharsets}
import org.http4s.Uri._
import org.http4s.internal.parboiled2.{Parser => PbParser, _}
import org.http4s.internal.parboiled2.CharPredicate.{Alpha, Digit, HexDigit}
import org.http4s.parser._
import org.http4s.syntax.string._
import org.http4s.util._
import scala.collection.immutable
import scala.math.Ordered
import scala.reflect.macros.whitebox

/** Representation of the [[Request]] URI
  *
  * @param scheme     optional Uri Scheme. eg, http, https
  * @param authority  optional Uri Authority. eg, localhost:8080, www.foo.bar
  * @param path       url-encoded string representation of the path component of the Uri.
  * @param query      optional Query. url-encoded.
  * @param fragment   optional Uri Fragment. url-encoded.
  */
// TODO fix Location header, add unit tests
final case class Uri(
    scheme: Option[Scheme] = None,
    authority: Option[Authority] = None,
    path: Path = "",
    query: Query = Query.empty,
    fragment: Option[Fragment] = None)
    extends QueryOps
    with Renderable {
  import Uri._

  def withPath(path: Path): Uri = copy(path = path)

  def withFragment(fragment: Fragment): Uri = copy(fragment = Option(fragment))

  def withoutFragment: Uri = copy(fragment = Option.empty[Fragment])

  def /(newSegment: Path): Uri = {
    val encoded = UrlCodingUtils.pathEncode(newSegment)
    val newPath =
      if (path.isEmpty || path.last != '/') s"$path/$encoded"
      else s"$path$encoded"
    copy(path = newPath)
  }

  def host: Option[Host] = authority.map(_.host)
  def port: Option[Int] = authority.flatMap(_.port)
  def userInfo: Option[UserInfo] = authority.flatMap(_.userInfo)

  def resolve(relative: Uri): Uri = Uri.resolve(this, relative)

  /**
    * Representation of the query string as a map
    *
    * In case a parameter is available in query string but no value is there the
    * sequence will be empty. If the value is empty the the sequence contains an
    * empty string.
    *
    * =====Examples=====
    * <table>
    * <tr><th>Query String</th><th>Map</th></tr>
    * <tr><td><code>?param=v</code></td><td><code>Map("param" -> Seq("v"))</code></td></tr>
    * <tr><td><code>?param=</code></td><td><code>Map("param" -> Seq(""))</code></td></tr>
    * <tr><td><code>?param</code></td><td><code>Map("param" -> Seq())</code></td></tr>
    * <tr><td><code>?=value</code></td><td><code>Map("" -> Seq("value"))</code></td></tr>
    * <tr><td><code>?p1=v1&amp;p1=v2&amp;p2=v3&amp;p2=v3</code></td><td><code>Map("p1" -> Seq("v1","v2"), "p2" -> Seq("v3","v4"))</code></td></tr>
    * </table>
    *
    * The query string is lazily parsed. If an error occurs during parsing
    * an empty `Map` is returned.
    */
  def multiParams: Map[String, immutable.Seq[String]] = query.multiParams

  /**
    * View of the head elements of the URI parameters in query string.
    *
    * In case a parameter has no value the map returns an empty string.
    *
    * @see multiParams
    */
  def params: Map[String, String] = query.params

  override lazy val renderString: String =
    super.renderString

  override def render(writer: Writer): writer.type = {
    def renderScheme(s: Scheme): writer.type =
      writer << s << ':'

    this match {
      case Uri(Some(s), Some(a), _, _, _) =>
        renderScheme(s) << "//" << a

      case Uri(Some(s), None, _, _, _) =>
        renderScheme(s)

      case Uri(None, Some(a), _, _, _) =>
        writer << a

      case Uri(None, None, _, _, _) =>
    }
    writer << path
    if (query.nonEmpty) writer << '?' << query
    fragment.foreach { f =>
      writer << '#' << UrlCodingUtils.urlEncode(f, spaceIsPlus = false)
    }
    writer
  }

  /////////// Query Operations ///////////////
  override protected type Self = Uri

  override protected def self: Self = this

  override protected def replaceQuery(query: Query): Self = copy(query = query)
}

object Uri {
  class Macros(val c: whitebox.Context) {
    import c.universe._

    def uriLiteral(s: c.Expr[String]): Tree =
      s.tree match {
        case Literal(Constant(s: String)) =>
          Uri
            .fromString(s)
            .fold(
              e => c.abort(c.enclosingPosition, e.details),
              _ =>
                q"_root_.org.http4s.Uri.fromString($s).fold(throw _, _root_.scala.Predef.identity)"
            )
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"This method uses a macro to verify that a String literal is a valid URI. Use Uri.fromString if you have a dynamic String that you want to parse as a Uri."
          )
      }
  }

  /** Decodes the String to a [[Uri]] using the RFC 3986 uri decoding specification */
  def fromString(s: String): ParseResult[Uri] =
    new RequestUriParser(s, StandardCharsets.UTF_8).Uri
      .run()(PbParser.DeliveryScheme.Either)
      .leftMap(e => ParseFailure("Invalid URI", e.format(s)))

  /** Parses a String to a [[Uri]] according to RFC 3986.  If decoding
    *  fails, throws a [[ParseFailure]].
    *
    *  For totality, call [[#fromString]].  For compile-time
    *  verification of literals, call [[#uri]].
    */
  def unsafeFromString(s: String): Uri =
    fromString(s).valueOr(throw _)

  /** Decodes the String to a [[Uri]] using the RFC 7230 section 5.3 uri decoding specification */
  def requestTarget(s: String): ParseResult[Uri] =
    new RequestUriParser(s, StandardCharsets.UTF_8).RequestUri
      .run()(PbParser.DeliveryScheme.Either)
      .leftMap(e => ParseFailure("Invalid request target", e.format(s)))

  /** A [[org.http4s.Uri]] may begin with a scheme name that refers to a
    * specification for assigning identifiers within that scheme.
    *
    * If the scheme is defined, the URI is absolute.  If the scheme is
    * not defined, the URI is a relative reference.
    *
    * @see https://www.ietf.org/rfc/rfc3986.txt, Section 3.1
    */
  final class Scheme private (val value: String) extends Ordered[Scheme] {
    override def equals(o: Any) = o match {
      case that: Scheme => this.value.equalsIgnoreCase(that.value)
      case _ => false
    }

    private[this] var hash = 0
    override def hashCode(): Int = {
      if (hash == 0) {
        hash = hashLower(value)
      }
      hash
    }

    override def toString = s"Scheme($value)"

    override def compare(other: Scheme): Int =
      value.compareToIgnoreCase(other.value)
  }

  object Scheme {
    val http: Scheme = new Scheme("http")
    val https: Scheme = new Scheme("https")

    @deprecated("Renamed to fromString", "0.21.0-M2")
    def parse(s: String): ParseResult[Scheme] = fromString(s)

    def fromString(s: String): ParseResult[Scheme] =
      new Http4sParser[Scheme](s, "Invalid scheme") with Parser {
        def main = scheme
      }.parse

    /** Like `fromString`, but throws on invalid input */
    def unsafeFromString(s: String): Scheme =
      fromString(s).fold(throw _, identity)

    private[http4s] trait Parser { self: PbParser =>
      def scheme = rule {
        "https" ~ !Alpha ~ push(https) |
          "http" ~ !Alpha ~ push(http) |
          capture(Alpha ~ zeroOrMore(Alpha | Digit | "+" | "-" | ".")) ~> (new Scheme(_))
      }
    }

    implicit val http4sOrderForScheme: Order[Scheme] =
      Order.fromComparable
    implicit val http4sShowForScheme: Show[Scheme] =
      Show.fromToString
    implicit val http4sInstancesForScheme: HttpCodec[Scheme] =
      new HttpCodec[Scheme] {
        def parse(s: String): ParseResult[Scheme] =
          Scheme.fromString(s)

        def render(writer: Writer, scheme: Scheme): writer.type =
          writer << scheme.value
      }
  }

  type Path = String
  type Fragment = String

  final case class Authority(
      userInfo: Option[UserInfo] = None,
      host: Host = RegName("localhost"),
      port: Option[Int] = None)
      extends Renderable {

    override def render(writer: Writer): writer.type = this match {
      case Authority(Some(u), h, None) => writer << u << '@' << h
      case Authority(Some(u), h, Some(p)) => writer << u << '@' << h << ':' << p
      case Authority(None, h, Some(p)) => writer << h << ':' << p
      case Authority(_, h, _) => writer << h
      case _ => writer
    }
  }

  /** The userinfo subcomponent may consist of a user name and,
    * optionally, scheme-specific information about how to gain
    * authorization to access the resource.  The user information, if
    * present, is followed by a commercial at-sign ("@") that delimits
    * it from the host.
    *
    * @param username The username component, decoded.
    *
    * @param password The password, decoded.  Passing a password in
    * clear text in a URI is a security risk and deprecated by RFC
    * 3986, but preserved in this model for losslessness.
    *
    * @see https://www.ietf.org/rfc/rfc3986.txt#section-3.21.
    */
  final case class UserInfo private (username: String, password: Option[String])
      extends Ordered[UserInfo] {
    override def compare(that: UserInfo): Int =
      username.compareTo(that.username) match {
        case 0 => Ordering.Option[String].compare(password, that.password)
        case cmp => cmp
      }
  }

  object UserInfo {

    /** Parses a userInfo from a percent-encoded string. */
    def fromString(s: String): ParseResult[UserInfo] =
      fromStringWithCharset(s, StandardCharsets.UTF_8)

    /** Parses a userInfo from a string percent-encoded in a specific charset. */
    def fromStringWithCharset(s: String, cs: NioCharset): ParseResult[UserInfo] =
      new Http4sParser[UserInfo](s, "Invalid user info") with Rfc3986Parser {
        def main = userInfo
        def charset = cs
      }.parse

    private[http4s] trait Parser { self: Rfc3986Parser =>
      def userInfo: Rule1[UserInfo] = rule {
        capture(zeroOrMore(Unreserved | PctEncoded | SubDelims)) ~
          (":" ~ capture(zeroOrMore(Unreserved | PctEncoded | SubDelims | ":"))).? ~>
          (
              (
                  username: String,
                  password: Option[String]) => UserInfo(decode(username), password.map(decode)))
      }
    }

    implicit val http4sInstancesForUserInfo
      : HttpCodec[UserInfo] with Order[UserInfo] with Hash[UserInfo] with Show[UserInfo] =
      new HttpCodec[UserInfo] with Order[UserInfo] with Hash[UserInfo] with Show[UserInfo] {
        def parse(s: String): ParseResult[UserInfo] =
          UserInfo.fromString(s)
        def render(writer: Writer, userInfo: UserInfo): writer.type = {
          writer << encodeUsername(userInfo.username)
          userInfo.password.foreach(writer << ":" << encodePassword(_))
          writer
        }

        private val SkipEncodeInUsername =
          UrlCodingUtils.Unreserved ++ "!$&'()*+,;="

        private def encodeUsername(
            s: String,
            charset: NioCharset = StandardCharsets.UTF_8): String =
          UrlCodingUtils.urlEncode(s, charset, false, SkipEncodeInUsername)

        private val SkipEncodeInPassword =
          SkipEncodeInUsername ++ ":"

        private def encodePassword(
            s: String,
            charset: NioCharset = StandardCharsets.UTF_8): String =
          UrlCodingUtils.urlEncode(s, charset, false, SkipEncodeInPassword)

        def compare(x: UserInfo, y: UserInfo): Int = x.compareTo(y)

        def hash(x: UserInfo): Int = x.hashCode

        def show(x: UserInfo): String = x.toString
      }
  }

  sealed trait Host extends Renderable {
    def value: String

    override def render(writer: Writer): writer.type = this match {
      case RegName(n) => writer << n
      case a: Ipv4Address => writer << a.value
      case a: Ipv6Address => writer << '[' << a << ']'
      case _ => writer
    }
  }

  @deprecated("Renamed to Ipv4Address, modeled as case class of bytes", "0.21.0-M2")
  type IPv4 = Ipv4Address

  @deprecated("Renamed to Ipv4Address, modeled as case class of bytes", "0.21.0-M2")
  object IPv4 {
    @deprecated("Use Ipv4Address.fromString(ciString.value)", "0.21.0-M2")
    def apply(ciString: CaseInsensitiveString): ParseResult[Ipv4Address] =
      Ipv4Address.fromString(ciString.value)
  }

  final case class Ipv4Address(a: Byte, b: Byte, c: Byte, d: Byte)
      extends Host
      with Ordered[Ipv4Address]
      with Serializable {
    override def toString: String = s"Ipv4Address($value)"

    override def compare(that: Ipv4Address): Int = {
      var cmp = a.compareTo(that.a)
      if (cmp == 0) cmp = b.compareTo(that.b)
      if (cmp == 0) cmp = c.compareTo(that.c)
      if (cmp == 0) cmp = d.compareTo(that.d)
      cmp
    }

    def toByteArray: Array[Byte] =
      Array(a, b, c, d)

    def toInet4Address: Inet4Address =
      InetAddress.getByAddress(toByteArray).asInstanceOf[Inet4Address]

    def value: String =
      new StringBuilder()
        .append(a & 0xff)
        .append(".")
        .append(b & 0xff)
        .append(".")
        .append(c & 0xff)
        .append(".")
        .append(d & 0xff)
        .toString
  }

  object Ipv4Address {
    def fromString(s: String): ParseResult[Ipv4Address] =
      new Http4sParser[Ipv4Address](s, "Invalid scheme") with Parser with IpParser {
        def main = ipv4Address
      }.parse

    /** Like `fromString`, but throws on invalid input */
    def unsafeFromString(s: String): Ipv4Address =
      fromString(s).fold(throw _, identity)

    def fromByteArray(bytes: Array[Byte]): ParseResult[Ipv4Address] =
      bytes match {
        case Array(a, b, c, d) =>
          Right(Ipv4Address(a, b, c, d))
        case _ =>
          Left(ParseFailure("Invalid Ipv4Address", s"Byte array not exactly four bytes: ${bytes}"))
      }

    def fromInet4Address(address: Inet4Address): Ipv4Address =
      address.getAddress match {
        case Array(a, b, c, d) =>
          Ipv4Address(a, b, c, d)
        case array =>
          throw bug(s"Inet4Address.getAddress not exactly four bytes: ${array}")
      }

    private[http4s] trait Parser { self: PbParser with IpParser =>
      def ipv4Address: Rule1[Ipv4Address] = rule {
        // format: off
        decOctet ~ "." ~ decOctet ~ "." ~ decOctet ~ "." ~ decOctet ~>
        { (a: Byte, b: Byte, c: Byte, d: Byte) => new Ipv4Address(a, b, c, d) }
        // format:on
      }

      private def decOctet = rule { capture(DecOctet) ~> (_.toInt.toByte) }
    }

    implicit val http4sInstancesForIpv4Address
      : HttpCodec[Ipv4Address] with Order[Ipv4Address] with Hash[Ipv4Address] with Show[Ipv4Address] =
      new HttpCodec[Ipv4Address] with Order[Ipv4Address] with Hash[Ipv4Address] with Show[Ipv4Address] {
        def parse(s: String): ParseResult[Ipv4Address] =
          Ipv4Address.fromString(s)
        def render(writer: Writer, ipv4: Ipv4Address): writer.type =
          writer << ipv4.value

        def compare(x: Ipv4Address, y: Ipv4Address): Int = x.compareTo(y)

        def hash(x: Ipv4Address): Int = x.hashCode

        def show(x: Ipv4Address): String = x.toString
      }
  }

  @deprecated("Renamed to Ipv6Address, modeled as case class of bytes", "0.21.0-M2")
  type IPv6 = Ipv6Address

  @deprecated("Renamed to Ipv6Address, modeled as case class of bytes", "0.21.0-M2")
  object IPv6 {
    @deprecated("Use Ipv6Address.fromString(ciString.value)", "0.21.0-M2")
    def apply(ciString: CaseInsensitiveString): ParseResult[Ipv6Address] =
      Ipv6Address.fromString(ciString.value)
  }

  final case class Ipv6Address(a: Short, b: Short, c: Short, d: Short, e: Short, f: Short, g: Short, h: Short)
      extends Host
      with Ordered[Ipv6Address]
      with Serializable {
    override def toString: String = s"Ipv6Address($a,$b,$c,$d,$e,$f,$g,$h)"

    override def compare(that: Ipv6Address): Int = {
      var cmp = a.compareTo(that.a)
      if (cmp == 0) cmp = b.compareTo(that.b)
      if (cmp == 0) cmp = c.compareTo(that.c)
      if (cmp == 0) cmp = d.compareTo(that.d)
      if (cmp == 0) cmp = e.compareTo(that.e)
      if (cmp == 0) cmp = f.compareTo(that.f)
      if (cmp == 0) cmp = g.compareTo(that.g)
      if (cmp == 0) cmp = h.compareTo(that.h)
      cmp
    }

    def toByteArray: Array[Byte] = {
      val bb = ByteBuffer.allocate(16)
      bb.putShort(a)
      bb.putShort(b)
      bb.putShort(c)
      bb.putShort(d)
      bb.putShort(e)
      bb.putShort(f)
      bb.putShort(g)
      bb.putShort(h)
      bb.array
    }

    def toInet6Address: Inet6Address =
      InetAddress.getByAddress(toByteArray).asInstanceOf[Inet6Address]

    def value: String = {
      val hextets = Array(a, b, c, d, e, f, g, h)
      var zeroesStart = -1
      var maxZeroesStart = -1
      var maxZeroesLen = 0
      var lastWasZero = false
      for (i <- 0 until 8) {
        if (hextets(i) == 0) {
          if (!lastWasZero) {
            lastWasZero = true
            zeroesStart = i
          }
          else {
            val zeroesLen = i - zeroesStart
            if (zeroesLen > maxZeroesLen) {
              maxZeroesStart = zeroesStart
              maxZeroesLen = zeroesLen
            }
          }
        }
        else {
          lastWasZero = false
        }
      }
      val sb = new StringBuilder
      var i = 0
      while (i < 8) {
        if (i == maxZeroesStart) {
          sb.append("::")
          i += maxZeroesLen
          i += 1
        }
        else {
          sb.append(Integer.toString(hextets(i) & 0xffff, 16))
          i += 1
          if (i < 8 && i != maxZeroesStart) {
            sb.append(":")
          }
        }
      }
      sb.toString
    }
  }

  object Ipv6Address {
    def fromString(s: String): ParseResult[Ipv6Address] =
      new Http4sParser[Ipv6Address](s, "Invalid scheme") with Parser with IpParser {
        def main = ipv6Address
      }.parse

    /** Like `fromString`, but throws on invalid input */
    def unsafeFromString(s: String): Ipv6Address =
      fromString(s).fold(throw _, identity)

    def fromByteArray(bytes: Array[Byte]): ParseResult[Ipv6Address] =
      if (bytes.length == 16) {
        val bb = ByteBuffer.wrap(bytes)
        Right(Ipv6Address(bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort()))
      } else {
        Left(ParseFailure("Invalid Ipv6Address", s"Byte array not exactly 16 bytes: ${bytes.toSeq}"))
      }

    def fromInet6Address(address: Inet6Address): Ipv6Address = {
      val bytes = address.getAddress
      if (bytes.length == 16) {
        val bb = ByteBuffer.wrap(bytes)
        Ipv6Address(bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort(), bb.getShort())
      } else {
        throw bug(s"Inet4Address.getAddress not exactly 16 bytes: ${bytes.toSeq}")
      }
    }

    private[http4s] trait Parser { self: PbParser with IpParser =>
      // format: off
      def ipv6Address: Rule1[Ipv6Address] = rule {
        6.times(h16 ~ ":") ~ ls32 ~>
          { (ls: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(ls, Seq(r0, r1)) } |
        "::" ~ 5.times(h16 ~ ":") ~ ls32 ~>
          { (ls: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(ls, Seq(r0, r1)) } |
        optional(h16) ~ "::" ~ 4.times(h16 ~ ":") ~ ls32 ~>
          { (l: Option[Short], rs: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(l.toSeq, rs :+ r0 :+ r1) } |
        optional((1 to 2).times(h16).separatedBy(":")) ~ "::" ~ 3.times(h16 ~ ":") ~ ls32 ~>
          { (ls: Option[collection.Seq[Short]], rs: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(ls.getOrElse(Seq.empty), rs :+ r0 :+ r1) } |
        optional((1 to 3).times(h16).separatedBy(":")) ~ "::" ~ 2.times(h16 ~ ":") ~ ls32 ~>
          { (ls: Option[collection.Seq[Short]], rs: collection.Seq[Short], r0: Short, r1: Short) => toIpv6(ls.getOrElse(Seq.empty), rs :+ r0 :+ r1) } |
        optional((1 to 4).times(h16).separatedBy(":")) ~ "::" ~ h16 ~ ":" ~ ls32 ~>
          { (ls: Option[collection.Seq[Short]], r0: Short, r1: Short, r2: Short) => toIpv6(ls.getOrElse(Seq.empty), Seq(r0, r1, r2)) } |
        optional((1 to 5).times(h16).separatedBy(":")) ~ "::" ~ ls32 ~>
          { (ls: Option[collection.Seq[Short]], r0: Short, r1: Short) => toIpv6(ls.getOrElse(Seq.empty), Seq(r0, r1)) } |
        optional((1 to 6).times(h16).separatedBy(":")) ~ "::" ~ h16 ~>
          { (ls: Option[collection.Seq[Short]], r0: Short) => toIpv6(ls.getOrElse(Seq.empty), Seq(r0)) } |
        optional((1 to 7).times(h16).separatedBy(":")) ~ "::" ~>
          { (ls: Option[collection.Seq[Short]]) => toIpv6(ls.getOrElse(Seq.empty), Seq.empty) }
      }
      // format:on

      def ls32: Rule2[Short, Short] = rule {
        (h16 ~ ":" ~ h16) |
        (decOctet ~ "." ~ decOctet ~ "." ~ decOctet ~ "." ~ decOctet) ~> { (a: Byte, b: Byte, c: Byte, d: Byte) =>
          push(((a << 8) | b).toShort) ~ push(((c << 8) | d).toShort)
        }
      }

      def h16: Rule1[Short] = rule {
        capture((1 to 4).times(HexDigit)) ~> { s: String => java.lang.Integer.parseInt(s, 16).toShort }
      }
      // format:on

      private def toIpv6(lefts: collection.Seq[Short], rights: collection.Seq[Short]): Ipv6Address =
        (lefts ++ collection.Seq.fill(8 - lefts.size - rights.size)(0.toShort) ++ rights) match {
          case collection.Seq(a, b, c, d, e, f, g, h) =>
            Ipv6Address(a, b, c, d, e, f, g, h)
        }

      private def decOctet = rule { capture(DecOctet) ~> (_.toInt.toByte) }
    }

    implicit val http4sInstancesForIpv6Address
      : HttpCodec[Ipv6Address] with Order[Ipv6Address] with Hash[Ipv6Address] with Show[Ipv6Address] =
      new HttpCodec[Ipv6Address] with Order[Ipv6Address] with Hash[Ipv6Address] with Show[Ipv6Address] {
        def parse(s: String): ParseResult[Ipv6Address] =
          Ipv6Address.fromString(s)
        def render(writer: Writer, ipv6: Ipv6Address): writer.type =
          writer << ipv6.value

        def compare(x: Ipv6Address, y: Ipv6Address): Int = x.compareTo(y)

        def hash(x: Ipv6Address): Int = x.hashCode

        def show(x: Ipv6Address): String = x.toString
      }
  }

  final case class RegName(host: CaseInsensitiveString) extends Host {
    def value: String = host.toString
  }

  object RegName { def apply(name: String): RegName = new RegName(name.ci) }

  /**
    * Resolve a relative Uri reference, per RFC 3986 sec 5.2
    */
  def resolve(base: Uri, reference: Uri): Uri = {

    /* Merge paths per RFC 3986 5.2.3 */
    def merge(base: Path, reference: Path): Path =
      base.substring(0, base.lastIndexOf('/') + 1) + reference

    val target = (base, reference) match {
      case (_, Uri(Some(_), _, _, _, _)) => reference
      case (Uri(s, _, _, _, _), Uri(_, a @ Some(_), p, q, f)) => Uri(s, a, p, q, f)
      case (Uri(s, a, p, q, _), Uri(_, _, "", Query.empty, f)) => Uri(s, a, p, q, f)
      case (Uri(s, a, p, _, _), Uri(_, _, "", q, f)) => Uri(s, a, p, q, f)
      case (Uri(s, a, bp, _, _), Uri(_, _, p, q, f)) =>
        if (p.headOption.fold(false)(_ == '/')) Uri(s, a, p, q, f)
        else Uri(s, a, merge(bp, p), q, f)
    }

    target.withPath(removeDotSegments(target.path))
  }

  /**
    * Remove dot sequences from a Path, per RFC 3986 Sec 5.2.4
    * Adapted from"
    * https://github.com/Norconex/commons-lang/blob/c83fdeac7a60ac99c8602e0b47056ad77b08f570/norconex-commons-lang/src/main/java/com/norconex/commons/lang/url/URLNormalizer.java#L429
    */
  def removeDotSegments(path: String): String = {
    // (Bulleted comments are from RFC3986, section-5.2.4)

    // 1.  The input buffer is initialized with the now-appended path
    //     components and the output buffer is initialized to the empty
    //     string.
    val in = new StringBuilder(path)
    val out = new StringBuilder

    // 2.  While the input buffer is not empty, loop as follows:
    while (in.nonEmpty) {

      // A.  If the input buffer begins with a prefix of "../" or "./",
      //     then remove that prefix from the input buffer; otherwise,
      if (startsWith(in, "../"))
        deleteStart(in, "../")
      else if (startsWith(in, "./"))
        deleteStart(in, "./")

      // B.  if the input buffer begins with a prefix of "/./" or "/.",
      //     where "." is a complete path segment, then replace that
      //     prefix with "/" in the input buffer; otherwise,
      else if (startsWith(in, "/./"))
        replaceStart(in, "/./", "/")
      else if (equalStrings(in, "/."))
        replaceStart(in, "/.", "/")

      // C.  if the input buffer begins with a prefix of "/../" or "/..",
      //     where ".." is a complete path segment, then replace that
      //     prefix with "/" in the input buffer and remove the last
      //     segment and its preceding "/" (if any) from the output
      //     buffer; otherwise,
      else if (startsWith(in, "/../")) {
        replaceStart(in, "/../", "/")
        removeLastSegment(out)
      } else if (equalStrings(in, "/..")) {
        replaceStart(in, "/..", "/")
        removeLastSegment(out)
      }

      // D.  if the input buffer consists only of "." or "..", then remove
      //      that from the input buffer; otherwise,
      else if (equalStrings(in, ".."))
        deleteStart(in, "..")
      else if (equalStrings(in, "."))
        deleteStart(in, ".")

      // E.  move the first path segment in the input buffer to the end of
      //     the output buffer, including the initial "/" character (if
      //     any) and any subsequent characters up to, but not including,
      //     the next "/" character or the end of the input buffer.
      else
        in.indexOf("/", 1) match {
          case nextSlashIndex if nextSlashIndex > -1 =>
            out.append(in.substring(0, nextSlashIndex))
            in.delete(0, nextSlashIndex)
          case _ =>
            out.append(in)
            in.setLength(0)
        }
    }

    // 3.  Finally, the output buffer is returned as the result of
    //     remove_dot_segments.
    out.toString
  }

  // Helper functions for removeDotSegments
  private def startsWith(b: StringBuilder, str: String): Boolean =
    b.indexOf(str) == 0
  private def equalStrings(b: StringBuilder, str: String): Boolean =
    b.length == str.length && startsWith(b, str)
  private def deleteStart(b: StringBuilder, str: String): StringBuilder =
    b.delete(0, str.length)
  private def replaceStart(b: StringBuilder, target: String, replacement: String): StringBuilder = {
    deleteStart(b, target)
    b.insert(0, replacement)
  }
  private def removeLastSegment(b: StringBuilder): Unit =
    b.lastIndexOf("/") match {
      case -1 => b.setLength(0)
      case n => b.setLength(n)
    }

  /**
    * Literal syntax for URIs.  Invalid or non-literal arguments are rejected
    * at compile time.
    */
  @deprecated("""use uri"" string interpolation instead""", "0.20")
  def uri(s: String): Uri = macro Uri.Macros.uriLiteral

  implicit val http4sUriEq: Eq[Uri] = Eq.fromUniversalEquals
}

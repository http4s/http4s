/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/scalatra/rl/blob/v0.4.10/core/src/main/scala/rl/UrlCodingUtils.scala
 * Copyright (c) 2011 Mojolly Ltd.
 * See licenses/LICENSE_rl
 */

package org.http4s

import cats.{Eq, Hash, Order, Show}
import cats.syntax.either._
import com.github.ghik.silencer.silent
import java.net.{Inet4Address, Inet6Address, InetAddress}
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.{Charset => JCharset, StandardCharsets}
import org.http4s.internal.{bug, hashLower}
import cats.kernel.Semigroup
import org.http4s.internal.parboiled2.{Parser => PbParser, _}
import org.http4s.internal.parboiled2.CharPredicate.{Alpha, Digit, HexDigit}
import org.http4s.parser._
import org.http4s.util._
import org.typelevel.ci.CIString

import scala.collection.immutable
import scala.math.Ordered
import scala.reflect.macros.blackbox

/** Representation of the [[Request]] URI
  *
  * @param scheme     optional Uri Scheme. eg, http, https
  * @param authority  optional Uri Authority. eg, localhost:8080, www.foo.bar
  * @param path       url-encoded string representation of the path component of the Uri.
  * @param query      optional Query. url-encoded.
  * @param fragment   optional Uri Fragment. url-encoded.
  */
final case class Uri(
    scheme: Option[Uri.Scheme] = None,
    authority: Option[Uri.Authority] = None,
    path: Uri.Path = Uri.Path.empty,
    query: Query = Query.empty,
    fragment: Option[Uri.Fragment] = None)
    extends QueryOps
    with Renderable {

  /**
    * Adds the path exactly as described. Any path element must be urlencoded ahead of time.
    * @param path the path string to replace
    */
  @deprecated("Use {withPath(Uri.Path)} instead", "1.0.0-M1")
  def withPath(path: String): Uri = copy(path = Uri.Path.fromString(path))

  def withPath(path: Uri.Path): Uri = copy(path = path)

  def withFragment(fragment: Uri.Fragment): Uri = copy(fragment = Option(fragment))

  def withoutFragment: Uri = copy(fragment = Option.empty[Uri.Fragment])

  /**
    * Urlencodes and adds a path segment to the Uri
    *
    * @param newSegment the segment to add.
    * @return a new uri with the segment added to the path
    */
  def addSegment(newSegment: String): Uri = copy(path = toSegment(path, newSegment))

  /**
    * This is an alias to [[addSegment(Path)]]
    */
  def /(newSegment: String): Uri = addSegment(newSegment)

  /**
    * Splits the path segments and adds each of them to the path url-encoded.
    * A segment is delimited by /
    * @param morePath the path to add
    * @return a new uri with the segments added to the path
    */
  def addPath(morePath: String): Uri =
    copy(path = morePath.split("/").foldLeft(path)((p, segment) => toSegment(p, segment)))

  def host: Option[Uri.Host] = authority.map(_.host)
  def port: Option[Int] = authority.flatMap(_.port)
  def userInfo: Option[Uri.UserInfo] = authority.flatMap(_.userInfo)

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
    def renderScheme(s: Uri.Scheme): writer.type =
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

    this match {
      case Uri(_, Some(_), p, _, _) if p.nonEmpty && !p.absolute =>
        writer << "/" << p
      case Uri(_, _, p, _, _) =>
        writer << p
    }

    if (query.nonEmpty) writer << '?' << query
    fragment.foreach { f =>
      writer << '#' << Uri.encode(f, spaceIsPlus = false)
    }
    writer
  }

  /////////// Query Operations ///////////////
  override protected type Self = Uri

  override protected def self: Self = this

  override protected def replaceQuery(query: Query): Self = copy(query = query)

  private def toSegment(path: Uri.Path, newSegment: String): Uri.Path =
    path / Uri.Path.Segment(newSegment)
}

object Uri {
  class Macros(val c: blackbox.Context) {
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
    override def equals(o: Any) =
      o match {
        case that: Scheme => this.value.equalsIgnoreCase(that.value)
        case _ => false
      }

    private[this] var hash = 0
    override def hashCode(): Int = {
      if (hash == 0)
        hash = hashLower(value)
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

    @silent("deprecated")
    private[http4s] trait Parser { self: PbParser =>
      def scheme =
        rule {
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

  type Fragment = String

  final case class Authority(
      userInfo: Option[UserInfo] = None,
      host: Host = RegName("localhost"),
      port: Option[Int] = None)
      extends Renderable {
    override def render(writer: Writer): writer.type =
      this match {
        case Authority(Some(u), h, None) => writer << u << '@' << h
        case Authority(Some(u), h, Some(p)) => writer << u << '@' << h << ':' << p
        case Authority(None, h, Some(p)) => writer << h << ':' << p
        case Authority(_, h, _) => writer << h
        case _ => writer
      }
  }

  final class Path private (
      val segments: Vector[Path.Segment],
      val absolute: Boolean,
      val endsWithSlash: Boolean)
      extends Renderable {

    def isEmpty: Boolean = segments.isEmpty
    def nonEmpty: Boolean = segments.nonEmpty

    override def equals(obj: Any): Boolean =
      obj match {
        case p: Path => doEquals(p)
        case _ => false
      }

    private def doEquals(path: Path): Boolean =
      this.segments == path.segments && path.absolute == this.absolute && path.endsWithSlash == this.endsWithSlash

    override def hashCode(): Int = {
      var hash = segments.hashCode()
      hash += 31 * java.lang.Boolean.hashCode(absolute)
      hash += 31 * java.lang.Boolean.hashCode(endsWithSlash)
      hash
    }

    def render(writer: Writer): writer.type = {
      val start = if (absolute) "/" else ""
      writer << start << segments.iterator.mkString("/")
      if (endsWithSlash) writer << "/" else writer
    }

    override val renderString: String = super.renderString
    override def toString: String = renderString

    def /(segment: Path.Segment): Path = addSegment(segment)
    def addSegment(segment: Path.Segment): Path =
      addSegments(List(segment))
    def addSegments(value: Seq[Path.Segment]): Path =
      Path(this.segments ++ value, absolute = absolute || this.segments.isEmpty)

    def normalize: Path = Path(segments.filterNot(_.isEmpty))

    /* Merge paths per RFC 3986 5.2.3 */
    def merge(path: Path): Path = {
      val merge = if (isEmpty) segments else segments.init
      Path(merge ++ path.segments, absolute = absolute, endsWithSlash = path.endsWithSlash)
    }

    def concat(path: Path): Path =
      Path(segments ++ path.segments, absolute = absolute, endsWithSlash = path.endsWithSlash)

    def startsWith(path: Path): Boolean = segments.startsWith(path.segments)

    def startsWithString(path: String): Boolean = startsWith(Path.fromString(path))

    def indexOf(path: Path): Option[Int] =
      if (path.isEmpty) None else Some(segments.indexOfSlice(path.segments)).filterNot(_ == -1)

    def indexOfString(path: String): Option[Int] = indexOf(Path.fromString(path))

    def splitAt(idx: Int): (Path, Path) =
      if (idx < 0) (if (absolute) Path.Root else Path.empty, this)
      else {
        val (start, end) = segments.splitAt(idx + 1)
        Path(start, absolute = absolute) -> Path(end, true, endsWithSlash = endsWithSlash)
      }
    private def copy(
        segments: Vector[Path.Segment] = segments,
        absolute: Boolean = absolute,
        endsWithSlash: Boolean = endsWithSlash) =
      new Path(segments, absolute, endsWithSlash)

    def dropEndsWithSlash = copy(endsWithSlash = false)
    def addEndsWithSlash = copy(endsWithSlash = true)

    def toAbsolute = copy(absolute = true)
    def toRelative = copy(absolute = false)
  }

  object Path {
    val empty = Path(Vector.empty)
    val Root = Path(Vector.empty, absolute = true)

    final class Segment private (val encoded: String) {
      def isEmpty = encoded.isEmpty

      override def equals(obj: Any): Boolean =
        obj match {
          case s: Segment => s.encoded == encoded
        }

      override def hashCode(): Int = encoded.hashCode

      def decoded(
          charset: JCharset = StandardCharsets.UTF_8,
          plusIsSpace: Boolean = false,
          toSkip: Char => Boolean = Function.const(false)): String =
        Uri.decode(encoded, charset, plusIsSpace, toSkip)

      override val toString: String = encoded
    }

    object Segment extends (String => Segment) {
      def apply(value: String): Segment = new Segment(pathEncode(value))
      def encoded(value: String): Segment = new Segment(value)
    }

    /**
      * This constructor allows you to construct the path directly.
      * Each path segment needs to be encoded for it to be used here.
      *
      * @param segments the segments that this path consists of. MUST be Urlencoded.
      * @param absolute if the path is absolute. I.E starts with a "/"
      * @param endsWithSlash if the path is a "directory", ends with a "/"
      * @return a Uri.Path that can be used in Uri, or by itself.
      */
    def apply(
        segments: Vector[Segment],
        absolute: Boolean = false,
        endsWithSlash: Boolean = false): Path =
      new Path(segments, absolute, endsWithSlash)

    def unapply(path: Path): Some[(Vector[Segment], Boolean, Boolean)] =
      Some((path.segments, path.absolute, path.endsWithSlash))

    def fromString(fromPath: String): Path =
      fromPath match {
        case "" => empty
        case "/" => Root
        case pth =>
          val absolute = pth.startsWith("/")
          val relative = if (absolute) pth.substring(1) else pth
          Path(
            segments = relative
              .split("/")
              .foldLeft(Vector.empty[Segment])((path, segment) => path :+ Segment.encoded(segment)),
            absolute = absolute,
            endsWithSlash = relative.endsWith("/")
          )
      }

    implicit val eq: Eq[Path] = Eq.fromUniversalEquals[Path]
    implicit val semigroup: Semigroup[Path] = (a: Path, b: Path) => a.concat(b)
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
    def fromStringWithCharset(s: String, cs: JCharset): ParseResult[UserInfo] =
      new Http4sParser[UserInfo](s, "Invalid user info") with Rfc3986Parser {
        def main = userInfo
        def charset = cs
      }.parse

    private[http4s] trait Parser { self: Rfc3986Parser =>
      def userInfo: Rule1[UserInfo] =
        rule {
          capture(zeroOrMore(Unreserved | PctEncoded | SubDelims)) ~
            (":" ~ capture(zeroOrMore(Unreserved | PctEncoded | SubDelims | ":"))).? ~>
            ((username: String, password: Option[String]) =>
              UserInfo(decode(username), password.map(decode)))
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
          Unreserved ++ "!$&'()*+,;="

        private def encodeUsername(s: String, charset: JCharset = StandardCharsets.UTF_8): String =
          encode(s, charset, false, SkipEncodeInUsername)

        private val SkipEncodeInPassword =
          SkipEncodeInUsername ++ ":"

        private def encodePassword(s: String, charset: JCharset = StandardCharsets.UTF_8): String =
          encode(s, charset, false, SkipEncodeInPassword)

        def compare(x: UserInfo, y: UserInfo): Int = x.compareTo(y)

        def hash(x: UserInfo): Int = x.hashCode

        def show(x: UserInfo): String = x.toString
      }
  }

  sealed trait Host extends Renderable {
    def value: String

    override def render(writer: Writer): writer.type =
      this match {
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
    def apply(ciString: CIString): ParseResult[Ipv4Address] =
      Ipv4Address.fromString(ciString.toString)
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
      def ipv4Address: Rule1[Ipv4Address] =
        rule {
          // format: off
        decOctet ~ "." ~ decOctet ~ "." ~ decOctet ~ "." ~ decOctet ~>
        { (a: Byte, b: Byte, c: Byte, d: Byte) => new Ipv4Address(a, b, c, d) }
        // format:on
      }

      private def decOctet = rule {capture(DecOctet) ~> (_.toInt.toByte)}
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
    def apply(ciString: CIString): ParseResult[Ipv6Address] =
      Ipv6Address.fromString(ciString.toString)
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
        capture((1 to 4).times(HexDigit)) ~> { (s: String) => java.lang.Integer.parseInt(s, 16).toShort }
      }
      // format:on

      private def toIpv6(lefts: collection.Seq[Short], rights: collection.Seq[Short]): Ipv6Address =
        (lefts ++ collection.Seq.fill(8 - lefts.size - rights.size)(0.toShort) ++ rights) match {
          case collection.Seq(a, b, c, d, e, f, g, h) =>
            Ipv6Address(a, b, c, d, e, f, g, h)
        }

      private def decOctet = rule {capture(DecOctet) ~> (_.toInt.toByte)}
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

  final case class RegName(host: CIString) extends Host {
    def value: String = host.toString
  }

  object RegName { def apply(name: String): RegName = new RegName(CIString(name)) }

  /**
    * Resolve a relative Uri reference, per RFC 3986 sec 5.2
    */
  def resolve(base: Uri, reference: Uri): Uri = {
    val target = (base, reference) match {
      case (_, Uri(Some(_), _, _, _, _)) => reference
      case (Uri(s, _, _, _, _), Uri(_, a @ Some(_), p, q, f)) => Uri(s, a, p, q, f)
      case (Uri(s, a, p, q, _), Uri(_, _, pa, Query.empty, f)) if pa.isEmpty => Uri(s, a, p, q, f)
      case (Uri(s, a, p, _, _), Uri(_, _, pa, q, f)) if pa.isEmpty => Uri(s, a, p, q, f)
      case (Uri(s, a, bp, _, _), Uri(_, _, p, q, f)) =>
        if (p.absolute) Uri(s, a, p, q, f)
        else Uri(s, a, bp.merge(p), q, f)
    }

    target.withPath(removeDotSegments(target.path))
  }

  /**
    * Remove dot sequences from a Path, per RFC 3986 Sec 5.2.4
    * Adapted from"
    * https://github.com/Norconex/commons-lang/blob/c83fdeac7a60ac99c8602e0b47056ad77b08f570/norconex-commons-lang/src/main/java/com/norconex/commons/lang/url/URLNormalizer.java#L429
    */
  def removeDotSegments(path: Uri.Path): Uri.Path = {
    // (Bulleted comments are from RFC3986, section-5.2.4)

    // 1.  The input buffer is initialized with the now-appended path
    //     components and the output buffer is initialized to the empty
    //     string.
    val in = new StringBuilder(path.renderString)
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
    Uri.Path.fromString(out.toString)
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

  private[http4s] val Unreserved =
    CharPredicate.AlphaNum ++ "-_.~"

  private val toSkip =
    Unreserved ++ "!$&'()*+,;=:/?@"

  private val HexUpperCaseChars = (0 until 16).map { i =>
    Character.toUpperCase(Character.forDigit(i, 16))
  }

  /**
    * Percent-encodes a string.  Depending on the parameters, this method is
    * appropriate for URI or URL form encoding.  Any resulting percent-encodings
    * are normalized to uppercase.
    *
    * @param toEncode the string to encode
    * @param charset the charset to use for characters that are percent encoded
    * @param spaceIsPlus if space is not skipped, determines whether it will be
    * rendreed as a `"+"` or a percent-encoding according to `charset`.
    * @param toSkip a predicate of characters exempt from encoding.  In typical
    * use, this is composed of all Unreserved URI characters and sometimes a
    * subset of Reserved URI characters.
    */
  def encode(
      toEncode: String,
      charset: JCharset = StandardCharsets.UTF_8,
      spaceIsPlus: Boolean = false,
      toSkip: Char => Boolean = toSkip): String = {
    val in = charset.encode(toEncode)
    val out = CharBuffer.allocate((in.remaining() * 3).toInt)
    while (in.hasRemaining) {
      val c = in.get().toChar
      if (toSkip(c)) {
        out.put(c)
      } else if (c == ' ' && spaceIsPlus) {
        out.put('+')
      } else {
        out.put('%')
        out.put(HexUpperCaseChars((c >> 4) & 0xF))
        out.put(HexUpperCaseChars(c & 0xF))
      }
    }
    out.flip()
    out.toString
  }

  private val SkipEncodeInPath =
    Unreserved ++ ":@!$&'()*+,;="

  def pathEncode(s: String, charset: JCharset = StandardCharsets.UTF_8): String =
    encode(s, charset, false, SkipEncodeInPath)

  /**
    * Percent-decodes a string.
    *
    * @param toDecode the string to decode
    * @param charset the charset of percent-encoded characters
    * @param plusIsSpace true if `'+'` is to be interpreted as a `' '`
    * @param toSkip a predicate of characters whose percent-encoded form
    * is left percent-encoded.  Almost certainly should be left empty.
    */
  def decode(
      toDecode: String,
      charset: JCharset = StandardCharsets.UTF_8,
      plusIsSpace: Boolean = false,
      toSkip: Char => Boolean = Function.const(false)): String = {
    val in = CharBuffer.wrap(toDecode)
    // reserve enough space for 3-byte UTF-8 characters.  4-byte characters are represented
    // as surrogate pairs of characters, and will get a luxurious 6 bytes of space.
    val out = ByteBuffer.allocate(in.remaining() * 3)
    while (in.hasRemaining) {
      val mark = in.position()
      val c = in.get()
      if (c == '%') {
        if (in.remaining() >= 2) {
          val xc = in.get()
          val yc = in.get()
          val x = Character.digit(xc, 0x10)
          val y = Character.digit(yc, 0x10)
          if (x != -1 && y != -1) {
            val oo = (x << 4) + y
            if (!toSkip(oo.toChar)) {
              out.put(oo.toByte)
            } else {
              out.put('%'.toByte)
              out.put(xc.toByte)
              out.put(yc.toByte)
            }
          } else {
            out.put('%'.toByte)
            in.position(mark + 1)
          }
        } else {
          // This is an invalid encoding. Fail gracefully by treating the '%' as
          // a literal.
          out.put(c.toByte)
          while (in.hasRemaining) out.put(in.get().toByte)
        }
      } else if (c == '+' && plusIsSpace) {
        out.put(' '.toByte)
      } else {
        // normally `out.put(c.toByte)` would be enough since the url is %-encoded,
        // however there are cases where a string can be partially decoded
        // so we have to make sure the non us-ascii chars get preserved properly.
        if (this.toSkip(c)) {
          out.put(c.toByte)
        } else {
          out.put(charset.encode(String.valueOf(c)))
        }
      }
    }
    out.flip()
    charset.decode(out).toString
  }

  /**
    * Literal syntax for URIs.  Invalid or non-literal arguments are rejected
    * at compile time.
    */
  @deprecated("""use uri"" string interpolation instead""", "0.20")
  def uri(s: String): Uri = macro Uri.Macros.uriLiteral

  implicit val http4sUriEq: Eq[Uri] = Eq.fromUniversalEquals
}

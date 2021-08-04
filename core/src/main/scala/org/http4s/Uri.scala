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

import cats.{Eval, Hash, Order, Show}
import cats.data.NonEmptyList
import cats.kernel.Semigroup
import cats.parse.{Parser0, Parser => P}
import cats.syntax.all._
import com.comcast.ip4s
import java.net.{Inet4Address, Inet6Address, InetAddress}
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.{Charset => JCharset}
import java.nio.charset.StandardCharsets
import org.http4s.internal.{UriCoding, compareField, hashLower, reduceComparisons}
import org.http4s.internal.parsing.Rfc3986
import org.http4s.util.{Renderable, Writer}
import org.typelevel.ci.CIString
import scala.collection.immutable
import scala.math.Ordered

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

  /** Adds the path exactly as described. Any path element must be urlencoded ahead of time.
    * @param path the path string to replace
    */
  @deprecated("Use {withPath(Uri.Path)} instead", "0.22.0-M1")
  def withPath(path: String): Uri = copy(path = Uri.Path.unsafeFromString(path))

  def withPath(path: Uri.Path): Uri = copy(path = path)

  def withFragment(fragment: Uri.Fragment): Uri = copy(fragment = Option(fragment))

  def withoutFragment: Uri = copy(fragment = Option.empty[Uri.Fragment])

  /** Urlencodes and adds a path segment to the Uri
    *
    * @param newSegment the segment to add.
    * @return a new uri with the segment added to the path
    */
  def addSegment(newSegment: String): Uri = copy(path = toSegment(path, newSegment))

  /** This is an alias to [[#addSegment]]
    */
  def /(newSegment: String): Uri = addSegment(newSegment)

  /** Splits the path segments and adds each of them to the path url-encoded.
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

  /** Representation of the query string as a map
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

  /** View of the head elements of the URI parameters in query string.
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
        writer << "//" << a // https://stackoverflow.com/questions/64513631/uri-and-double-slashes/64513776#64513776

      case Uri(None, None, _, _, _) =>
    }

    this match {
      case Uri(_, Some(_), p, _, _) if p.nonEmpty && !p.absolute =>
        writer << "/" << p
      case Uri(None, None, p, _, _) =>
        if (!p.absolute && p.segments.headOption.fold(false)(_.toString.contains(":"))) {
          writer << "./" << p // https://tools.ietf.org/html/rfc3986#section-4.2 last paragraph
        } else {
          writer << p
        }
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

  /** Converts this request to origin-form, which is the absolute path and optional
    * query.  If the path is relative, it is assumed to be relative to the root.
    */
  def toOriginForm: Uri =
    Uri(path = path.toAbsolute, query = query)
}

object Uri extends UriPlatform {

  /** Decodes the String to a [[Uri]] using the RFC 3986 uri decoding specification */
  def fromString(s: String): ParseResult[Uri] =
    ParseResult.fromParser(Parser.uriReferenceUtf8, "Invalid URI")(s)

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
    ParseResult.fromParser(Parser.requestTargetParser, "Invalid request target")(s)

  /** A [[org.http4s.Uri]] may begin with a scheme name that refers to a
    * specification for assigning identifiers within that scheme.
    *
    * If the scheme is defined, the URI is absolute.  If the scheme is
    * not defined, the URI is a relative reference.
    *
    * @see [[https://tools.ietf.org/html/rfc3986#section-3.1 RFC 3986, Section 3.1, Scheme]]
    */
  final class Scheme private[http4s] (val value: String) extends Ordered[Scheme] {
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
      ParseResult.fromParser(Parser.scheme, "Invalid scheme")(s)

    /** Like `fromString`, but throws on invalid input */
    def unsafeFromString(s: String): Scheme =
      fromString(s).fold(throw _, identity)

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
      }
  }

  object Authority {

    implicit val catsInstancesForHttp4sAuthority
        : Hash[Authority] with Order[Authority] with Show[Authority] =
      new Hash[Authority] with Order[Authority] with Show[Authority] {
        override def hash(x: Authority): Int =
          x.hashCode

        override def compare(x: Authority, y: Authority): Int = {
          def compareAuthorities[A: Order](focus: Authority => A): Int =
            compareField(x, y, focus)

          reduceComparisons(
            compareAuthorities(_.userInfo),
            Eval.later(compareAuthorities(_.host)),
            Eval.later(compareAuthorities(_.port))
          )
        }

        override def show(a: Authority): String =
          a.renderString
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

    def startsWithString(path: String): Boolean = startsWith(Path.unsafeFromString(path))

    @deprecated("Misnamed, use findSplit(prefix) instead", since = "0.22.0-M1")
    def indexOf(prefix: Path): Option[Int] = findSplit(prefix)

    @deprecated("Misnamed, use findSplitOfString(prefix) instead", since = "0.22.0-M1")
    def indexOfString(path: String): Option[Int] = findSplit(Path.unsafeFromString(path))

    def findSplit(prefix: Path): Option[Int] =
      if (prefix.isEmpty) None
      else if (startsWith(prefix)) Some(prefix.segments.size)
      else None
    def findSplitOfString(path: String): Option[Int] = findSplit(Path.unsafeFromString(path))

    def splitAt(idx: Int): (Path, Path) =
      if (idx < 0) (if (absolute) Path.Root else Path.empty, this)
      else {
        val (start, end) = segments.splitAt(idx)
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
    lazy val Asterisk = Path(Vector(Segment("*")), absolute = false, endsWithSlash = false)

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

      val empty: Segment = Segment("")

      implicit val http4sInstancesForSegment: Order[Segment] =
        new Order[Segment] {
          def compare(x: Segment, y: Segment): Int =
            x.encoded.compare(y.encoded)
        }
    }

    /** This constructor allows you to construct the path directly.
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

    // def unapply(path: Path): Some[(Vector[Segment], Boolean, Boolean)] =
    //   Some((path.segments, path.absolute, path.endsWithSlash))

    @deprecated(message = "Use unsafeFromString instead", since = "0.22.0-M6")
    def fromString(fromPath: String): Path =
      unsafeFromString(fromPath)

    def unsafeFromString(fromPath: String): Path =
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

    implicit val http4sInstancesForPath: Order[Path] with Semigroup[Path] =
      new Order[Path] with Semigroup[Path] {
        def compare(x: Path, y: Path): Int = {
          def comparePaths[A: Order](focus: Path => A): Int =
            compareField(x, y, focus)
          reduceComparisons(
            comparePaths(_.absolute),
            Eval.later(comparePaths(_.segments)),
            Eval.later(comparePaths(_.endsWithSlash))
          )
        }

        def combine(x: Path, y: Path): Path = x.concat(y)
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
    * @see [[https://tools.ietf.org/html/rfc3986#section-3.2.1 RFC 3986, Section 3.2.1, User Information]]
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
      ParseResult.fromParser(Parser.userinfo(cs), "Invalid userinfo")(s)

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
        case RegName(n) => writer << encode(n.toString)
        case a: Ipv4Address => writer << a.value
        case a: Ipv6Address => writer << '[' << a << ']'
      }

    def toIpAddress: Option[ip4s.IpAddress] = this match {
      case Ipv4Address(a) => Some(a)
      case Ipv6Address(a) => Some(a)
      case RegName(_) => None
    }
  }

  object Host {

    implicit val catsInstancesForHttp4sUriHost: Hash[Host] with Order[Host] with Show[Host] =
      new Hash[Host] with Order[Host] with Show[Host] {
        override def hash(x: Host): Int =
          x match {
            case x: Ipv4Address =>
              x.hash
            case x: Ipv6Address =>
              x.hash
            case x: RegName =>
              x.hash
          }

        override def compare(x: Host, y: Host): Int =
          (x, y) match {
            case (x: Ipv4Address, y: Ipv4Address) =>
              x.compare(y)
            case (x: Ipv6Address, y: Ipv6Address) =>
              x.compare(y)
            case (x: RegName, y: RegName) =>
              x.compare(y)

            // Differing ADT constructors
            // Ipv4Address is arbitrarily considered > all Ipv6Address and RegName
            case (_: Ipv4Address, _) =>
              1
            case (_, _: Ipv4Address) =>
              -1

            // Ipv6Address is arbitrarily considered > all RegName
            case (_: Ipv6Address, _) =>
              1
            case (_, _: Ipv6Address) =>
              -1
          }

        override def show(a: Host): String =
          a.renderString
      }
  }

  final case class Ipv4Address(address: ip4s.Ipv4Address)
      extends Host
      with Ordered[Ipv4Address]
      with Serializable {
    override def toString: String = s"Ipv4Address($value)"

    override def compare(that: Ipv4Address): Int =
      this.address.compare(that.address)

    def toByteArray: Array[Byte] =
      address.toBytes

    def toInet4Address: Inet4Address =
      address.toInetAddress

    def value: String =
      address.toString
  }

  object Ipv4Address {
    def fromString(s: String): ParseResult[Ipv4Address] =
      ParseResult.fromParser(Parser.ipv4Address, "Invalid IPv4 Address")(s)

    /** Like `fromString`, but throws on invalid input */
    def unsafeFromString(s: String): Ipv4Address =
      fromString(s).fold(throw _, identity)

    def fromByteArray(bytes: Array[Byte]): ParseResult[Ipv4Address] =
      bytes match {
        case Array(a, b, c, d) =>
          Right(fromBytes(a, b, c, d))
        case _ =>
          Left(ParseFailure("Invalid Ipv4Address", s"Byte array not exactly four bytes: ${bytes}"))
      }

    def fromBytes(a: Byte, b: Byte, c: Byte, d: Byte): Ipv4Address =
      apply(ip4s.Ipv4Address.fromBytes(a.toInt, b.toInt, c.toInt, d.toInt))

    def fromInet4Address(address: Inet4Address): Ipv4Address =
      apply(ip4s.Ipv4Address.fromInet4Address(address))

    implicit val http4sInstancesForIpv4Address: HttpCodec[Ipv4Address]
      with Order[Ipv4Address]
      with Hash[Ipv4Address]
      with Show[Ipv4Address] =
      new HttpCodec[Ipv4Address]
        with Order[Ipv4Address]
        with Hash[Ipv4Address]
        with Show[Ipv4Address] {
        def parse(s: String): ParseResult[Ipv4Address] =
          Ipv4Address.fromString(s)
        def render(writer: Writer, ipv4: Ipv4Address): writer.type =
          writer << ipv4.value

        def compare(x: Ipv4Address, y: Ipv4Address): Int = x.compareTo(y)

        def hash(x: Ipv4Address): Int = x.hashCode

        def show(x: Ipv4Address): String = x.toString
      }
  }

  final case class Ipv6Address(address: ip4s.Ipv6Address)
      extends Host
      with Ordered[Ipv6Address]
      with Serializable {
    override def compare(that: Ipv6Address): Int =
      this.address.compare(that.address)

    def toByteArray: Array[Byte] =
      address.toBytes

    def toInetAddress: InetAddress =
      address.toInetAddress

    def value: String =
      address.toString
  }

  object Ipv6Address {
    def fromString(s: String): ParseResult[Ipv6Address] =
      ParseResult.fromParser(Parser.ipv6Address, "Invalid IPv6 address")(s)

    /** Like `fromString`, but throws on invalid input */
    def unsafeFromString(s: String): Ipv6Address =
      fromString(s).fold(throw _, identity)

    def fromByteArray(bytes: Array[Byte]): ParseResult[Ipv6Address] =
      ip4s.Ipv6Address.fromBytes(bytes) match {
        case Some(address) => Right(Ipv6Address(address))
        case None =>
          Left(
            ParseFailure("Invalid Ipv6Address", s"Byte array not exactly 16 bytes: ${bytes.toSeq}"))
      }

    def fromInet6Address(address: Inet6Address): Ipv6Address =
      apply(ip4s.Ipv6Address.fromInet6Address(address))

    def fromShorts(a: Short, b: Short, c: Short, d: Short, e: Short, f: Short, g: Short, h: Short)
        : Ipv6Address = {
      val bb = ByteBuffer.allocate(16)
      bb.putShort(a)
      bb.putShort(b)
      bb.putShort(c)
      bb.putShort(d)
      bb.putShort(e)
      bb.putShort(f)
      bb.putShort(g)
      bb.putShort(h)
      fromByteArray(bb.array).valueOr(throw _)
    }

    implicit val http4sInstancesForIpv6Address: HttpCodec[Ipv6Address]
      with Order[Ipv6Address]
      with Hash[Ipv6Address]
      with Show[Ipv6Address] =
      new HttpCodec[Ipv6Address]
        with Order[Ipv6Address]
        with Hash[Ipv6Address]
        with Show[Ipv6Address] {
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

    /** Converts this registered name to a Hostname. In the spec, for
      * generic schemes, a registered name need not be a valid host
      * name. In HTTP practice, this conversion should succeed.
      */
    def toHostname: Option[ip4s.Hostname] =
      ip4s.Hostname.fromString(host.toString)
  }

  object RegName {
    def apply(name: String): RegName = new RegName(CIString(name))

    def fromHostname(hostname: ip4s.Hostname): RegName =
      RegName(CIString(hostname.toString))

    implicit val catsInstancesForHttp4sUriRegName
        : Hash[RegName] with Order[RegName] with Show[RegName] =
      new Hash[RegName] with Order[RegName] with Show[RegName] {
        override def hash(x: RegName): Int =
          x.hashCode

        override def compare(x: RegName, y: RegName): Int =
          x.host.compare(y.host)

        override def show(a: RegName): String =
          a.toString
      }
  }

  /** Resolve a relative Uri reference, per RFC 3986 sec 5.2
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

  /** Remove dot sequences from a Path, per RFC 3986 Sec 5.2.4
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
    while (in.nonEmpty)
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

    // 3.  Finally, the output buffer is returned as the result of
    //     remove_dot_segments.
    Uri.Path.unsafeFromString(out.toString)
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

  private[http4s] def Unreserved = UriCoding.Unreserved

  /** Percent-encodes a string.  Depending on the parameters, this method is
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
      toSkip: Char => Boolean = toSkip): String =
    UriCoding.encode(toEncode, charset, spaceIsPlus, toSkip)

  private lazy val toSkip =
    UriCoding.Unreserved ++ "!$&'()*+,;=:/?@"

  private lazy val SkipEncodeInPath =
    UriCoding.Unreserved ++ ":@!$&'()*+,;="

  def pathEncode(s: String, charset: JCharset = StandardCharsets.UTF_8): String =
    encode(s, charset, false, SkipEncodeInPath)

  /** Percent-decodes a string.
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
        if (toSkip(c)) {
          out.put(c.toByte)
        } else {
          out.put(charset.encode(String.valueOf(c)))
        }
      }
    }
    out.flip()
    charset.decode(out).toString
  }

  implicit val catsInstancesForHttp4sUri: Hash[Uri] with Order[Uri] with Show[Uri] =
    new Hash[Uri] with Order[Uri] with Show[Uri] {
      override def hash(x: Uri): Int =
        x.hashCode

      override def compare(x: Uri, y: Uri): Int = {
        def compareUris[A: Order](focus: Uri => A): Int =
          compareField(x, y, focus)

        reduceComparisons(
          compareUris(_.scheme),
          Eval.later(compareUris(_.authority)),
          Eval.later(compareUris(_.path)),
          Eval.later(compareUris(_.query)),
          Eval.later(compareUris(_.fragment))
        )
      }

      override def show(t: Uri): String =
        t.renderString
    }

  private[http4s] object Parser {
    /* port        = *DIGIT
     *
     * Limitation: we only parse up to Int. The spec allows bigint!
     */
    private[http4s] val port: Parser0[Option[Int]] = {
      import Rfc3986.digit

      digit.rep0.string.mapFilter {
        case "" => Some(None)
        case s =>
          try Some(Some(s.toInt))
          catch { case _: NumberFormatException => None }
      }
    }

    /* reg-name    = *( unreserved / pct-encoded / sub-delims) */
    private[http4s] val regName: Parser0[Uri.RegName] = {
      import Rfc3986.{pctEncoded, subDelims, unreserved}

      unreserved
        .orElse(pctEncoded)
        .orElse(subDelims)
        .rep0
        .string
        .map(s => Uri.RegName(CIString(Uri.decode(s))))
    }

    private[http4s] val ipv6Address: P[Uri.Ipv6Address] =
      Rfc3986.ipv6Address.map(Uri.Ipv6Address(_))

    private[http4s] val ipv4Address: P[Uri.Ipv4Address] =
      Rfc3986.ipv4Address.map(Uri.Ipv4Address(_))

    /* host          = IP-literal / IPv4address / reg-name */
    private[http4s] val host: Parser0[Uri.Host] = {
      import cats.parse.Parser.char

      // TODO This isn't in the 0.21 model.
      /* IPvFuture     = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" ) */
      val ipVFuture: P[Nothing] = P.fail

      /* IP-literal    = "[" ( IPv6address / IPvFuture  ) "]" */
      val ipLiteral = char('[') *> ipv6Address.orElse(ipVFuture) <* char(']')

      ipLiteral.orElse(ipv4Address.backtrack).orElse(regName)
    }

    /* userinfo    = *( unreserved / pct-encoded / sub-delims / ":" ) */
    private[http4s] def userinfo(cs: JCharset): Parser0[Uri.UserInfo] = {
      import cats.parse.Parser.{char, charIn, oneOf}
      import Rfc3986.{pctEncoded, subDelims, unreserved}

      val username = oneOf(unreserved :: pctEncoded :: subDelims :: Nil).rep0.string
      val password = oneOf(unreserved :: pctEncoded :: subDelims :: charIn(':') :: Nil).rep0.string
      (username ~ (char(':') *> password).?).map { case (u, p) =>
        Uri.UserInfo(Uri.decode(u, cs), p.map(Uri.decode(_, cs)))
      }
    }

    /* segment       = *pchar */
    private[http4s] val segment: Parser0[Uri.Path.Segment] =
      Rfc3986.pchar.rep0.string.map(Uri.Path.Segment.encoded)

    /* segment-nz    = 1*pchar */
    private[http4s] val segmentNz: P[Uri.Path.Segment] =
      Rfc3986.pchar.rep.string.map(Uri.Path.Segment.encoded)

    /* segment-nz-nc = 1*( unreserved / pct-encoded / sub-delims / "@" )
                   ; non-zero-length segment without any colon ":" */
    private[http4s] val segmentNzNc: P[Uri.Path.Segment] =
      Rfc3986.unreserved
        .orElse(Rfc3986.pctEncoded)
        .orElse(Rfc3986.subDelims)
        .orElse(P.char('@'))
        .rep
        .string
        .map(Uri.Path.Segment.encoded(_))

    import cats.parse.Parser.{char, pure}

    /* path-abempty  = *( "/" segment ) */
    private[http4s] val pathAbempty: Parser0[Uri.Path] =
      (char('/') *> segment).rep0.map {
        case Nil => Uri.Path.empty
        case List(Uri.Path.Segment.empty) => Uri.Path.Root
        case segments =>
          val segmentsV = segments.toVector
          if (segmentsV.last.isEmpty)
            Uri.Path(segmentsV.dropRight(1), absolute = true, endsWithSlash = true)
          else
            Uri.Path(segmentsV, absolute = true, endsWithSlash = false)
      }

    /* path-absolute = "/" [ segment-nz *( "/" segment ) ] */
    private[http4s] val pathAbsolute: P[Uri.Path] =
      (char('/') *> (segmentNz ~ (char('/') *> segment).rep0).?).map {
        case Some((head, tail)) =>
          val segmentsV = head +: tail.toVector
          if (segmentsV.last.isEmpty)
            Uri.Path(segmentsV.dropRight(1), absolute = true, endsWithSlash = true)
          else
            Uri.Path(segmentsV, absolute = true, endsWithSlash = false)
        case None =>
          Uri.Path.Root
      }

    /* path-rootless = segment-nz *( "/" segment ) */
    private[http4s] val pathRootless: P[Uri.Path] =
      (segmentNz ~ (char('/') *> segment).rep0).map { case (head, tail) =>
        val segmentsV = head +: tail.toVector
        if (segmentsV.last.isEmpty)
          Uri.Path(segmentsV.dropRight(1), absolute = false, endsWithSlash = true)
        else
          Uri.Path(segmentsV, absolute = false, endsWithSlash = false)
      }

    /* path-empty    = 0<pchar> */
    private[http4s] val pathEmpty: Parser0[Uri.Path] =
      pure(Uri.Path.empty)

    /* path-noscheme = segment-nz-nc *( "/" segment ) */
    private[http4s] val pathNoscheme: P[Uri.Path] =
      (segmentNzNc ~ (char('/') *> segment).rep0).map { case (head, tail) =>
        val segmentsV = head +: tail.toVector
        if (segmentsV.last.isEmpty)
          Uri.Path(segmentsV.dropRight(1), absolute = false, endsWithSlash = true)
        else
          Uri.Path(segmentsV, absolute = false, endsWithSlash = false)
      }

    /* absolute-path = 1*( "/" segment ) */
    private[http4s] val absolutePath: P[Uri.Path] =
      (char('/') *> segment).rep.map {
        case NonEmptyList(Uri.Path.Segment.empty, Nil) => Uri.Path.Root
        case segments =>
          val segmentsV = segments.toList.toVector
          if (segmentsV.last.isEmpty)
            Uri.Path(segmentsV.dropRight(1), absolute = true, endsWithSlash = true)
          else
            Uri.Path(segmentsV, absolute = true, endsWithSlash = false)
      }

    /* authority   = [ userinfo "@" ] host [ ":" port ] */
    private[http4s] def authority(cs: JCharset): Parser0[Uri.Authority] =
      ((userinfo(cs) <* char('@')).backtrack.? ~ host ~ (char(':') *> port).?).map {
        case ((ui, h), p) => Uri.Authority(userInfo = ui, host = h, port = p.flatten)
      }

    /* fragment    = *( pchar / "/" / "?" )
     *
     * Not URL decoded.
     */
    private[http4s] val fragment: Parser0[Uri.Fragment] =
      Rfc3986.pchar.orElse(P.charIn("/?")).rep0.string

    /* scheme      = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) */
    private[http4s] val scheme: P[Uri.Scheme] = {
      import cats.parse.Parser.{charIn, not, string}
      import Rfc3986.{alpha, digit}

      val unary = alpha.orElse(digit).orElse(charIn("+-."))

      (string("https") <* not(unary))
        .as(Uri.Scheme.https)
        .backtrack
        .orElse((string("http") <* not(unary)).as(Uri.Scheme.http))
        .backtrack
        .orElse((alpha *> unary.rep0).string.map(new Uri.Scheme(_)))
    }

    /* request-target = origin-form
                      / absolute-form
                      / authority-form
                      / asterisk-form
     */
    private[http4s] val requestTargetParser: Parser0[Uri] = {
      import cats.parse.Parser.{char, oneOf0}
      import Query.{parser => query}

      /* origin-form    = absolute-path [ "?" query ] */
      val originForm: P[Uri] =
        (absolutePath ~ (char('?') *> query).?).map { case (p, q) =>
          Uri(scheme = None, authority = None, path = p, query = q.getOrElse(Query.empty))
        }

      /* absolute-form = absolute-URI */
      def absoluteForm: P[Uri] = absoluteUri(StandardCharsets.UTF_8)

      /* authority-form = authority */
      val authorityForm: Parser0[Uri] =
        authority(StandardCharsets.UTF_8).map(a => Uri(authority = Some(a)))

      /* asterisk-form = "*" */
      val asteriskForm: P[Uri] =
        char('*').as(Uri(path = Uri.Path.Asterisk))

      oneOf0(originForm :: absoluteForm :: authorityForm :: asteriskForm :: Nil)
    }

    /* hier-part   = "//" authority path-abempty
     *             / path-absolute
     *             / path-rootless
     *             / path-empty
     */
    def hierPart(cs: JCharset): Parser0[(Option[Uri.Authority], Uri.Path)] = {
      import P.string
      val rel: P[(Option[Uri.Authority], Uri.Path)] =
        (string("//") *> authority(cs) ~ pathAbempty).map { case (a, p) =>
          (Some(a), p)
        }
      P.oneOf0(
        rel :: pathAbsolute.map((None, _)) :: pathRootless.map((None, _)) :: pathEmpty.map(
          (None, _)) :: Nil)
    }

    /* absolute-URI  = scheme ":" hier-part [ "?" query ] */
    private[http4s] def absoluteUri(cs: JCharset): P[Uri] = {
      import cats.parse.Parser.char
      import Query.{parser => query}

      (scheme ~ (char(':') *> hierPart(cs)) ~ (char('?') *> query).?).map { case ((s, (a, p)), q) =>
        Uri(scheme = Some(s), authority = a, path = p, query = q.getOrElse(Query.empty))
      }
    }

    private[http4s] def uri(cs: JCharset): P[Uri] = {
      import cats.parse.Parser.char
      import Query.{parser => query}

      (scheme ~ (char(':') *> hierPart(cs)) ~ (char('?') *> query).? ~ (char('#') *> fragment).?)
        .map { case (((s, (a, p)), q), f) =>
          Uri(
            scheme = Some(s),
            authority = a,
            path = p,
            query = q.getOrElse(Query.empty),
            fragment = f)
        }
    }

    /* relative-part = "//" authority path-abempty
                     / path-absolute
                     / path-noscheme
                     / path-empty
     */
    private[http4s] def relativePart(cs: JCharset): Parser0[(Option[Uri.Authority], Uri.Path)] = {
      import cats.parse.Parser.string

      P.oneOf0(
        ((string("//") *> authority(cs) ~ pathAbempty).map { case (a, p) =>
          (Some(a), p)
        }) :: (pathAbsolute.map((None, _))) :: (pathNoscheme.map((None, _))) :: (pathEmpty.map(
          (None, _))) :: Nil)
    }

    /* relative-ref  = relative-part [ "?" query ] [ "#" fragment ] */
    private[http4s] def relativeRef(cs: JCharset): Parser0[Uri] = {
      import cats.parse.Parser.char
      import Query.{parser => query}

      (relativePart(cs) ~ (char('?') *> query).? ~ (char('#') *> fragment).?).map {
        case (((a, p), q), f) =>
          Uri(
            scheme = None,
            authority = a,
            path = p,
            query = q.getOrElse(Query.empty),
            fragment = f)
      }
    }

    private[http4s] val uriReferenceUtf8: Parser0[Uri] = uriReference(StandardCharsets.UTF_8)
    private[http4s] def uriReference(cs: JCharset): Parser0[Uri] =
      uri(cs).backtrack.orElse(relativeRef(cs))
  }
}

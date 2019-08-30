package org.http4s

import cats._
import cats.implicits.{catsSyntaxEither => _, _}
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.{Charset => JCharset}
import java.nio.charset.StandardCharsets
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.Uri._
import org.http4s.internal.parboiled2.CharPredicate.{Alpha, Digit}
import org.http4s.internal.parboiled2.{Parser => PbParser}
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
    val encoded = pathEncode(newSegment)
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
      writer << '#' << urlEncode(f, spaceIsPlus = false)
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

    def parse(s: String): ParseResult[Scheme] =
      new Http4sParser[Scheme](s, "Invalid scheme") with Parser {
        def main = scheme
      }.parse

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
          Scheme.parse(s)

        def render(writer: Writer, scheme: Scheme): writer.type =
          writer << scheme.value
      }
  }

  type UserInfo = String

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

  sealed trait Host extends Renderable {
    final def value: String = this match {
      case RegName(h) => h.toString
      case IPv4(a) => a.toString
      case IPv6(a) => a.toString
    }

    override def render(writer: Writer): writer.type = this match {
      case RegName(n) => writer << n
      case IPv4(a) => writer << a
      case IPv6(a) => writer << '[' << a << ']'
      case _ => writer
    }
  }

  final case class RegName(host: CaseInsensitiveString) extends Host
  final case class IPv4(address: CaseInsensitiveString) extends Host
  final case class IPv6(address: CaseInsensitiveString) extends Host

  object RegName { def apply(name: String): RegName = new RegName(name.ci) }
  object IPv4 { def apply(address: String): IPv4 = new IPv4(address.ci) }
  object IPv6 { def apply(address: String): IPv6 = new IPv6(address.ci) }

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
    * Taken from https://github.com/scalatra/rl/blob/v0.4.10/core/src/main/scala/rl/UrlCodingUtils.scala
    * Copyright (c) 2011 Mojolly Ltd.
    */
  private[http4s] val Unreserved =
    CharPredicate.AlphaNum ++ "-_.~"

  private val toSkip =
    Unreserved ++ "!$&'()*+,;=:/?@"

  // scalastyle:off magic.number
  private val HexUpperCaseChars = (0 until 16).map { i =>
    Character.toUpperCase(Character.forDigit(i, 16))
  }
  // scalastyle:on magic.number

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
  def urlEncode(
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
    urlEncode(s, charset, false, SkipEncodeInPath)

  /**
    * Percent-decodes a string.
    *
    * @param toDecode the string to decode
    * @param charset the charset of percent-encoded characters
    * @param plusIsSpace true if `'+'` is to be interpreted as a `' '`
    * @param toSkip a predicate of characters whose percent-encoded form
    * is left percent-encoded.  Almost certainly should be left empty.
    */
  def urlDecode(
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
          // scalastyle:off magic.number
          val x = Character.digit(xc, 0x10)
          val y = Character.digit(yc, 0x10)
          // scalastyle:on magic.number
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

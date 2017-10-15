package org.http4s

import cats.{Eq, Order, Show}
import cats.implicits.{catsSyntaxEither => _, _}
import java.net.{InetAddress, UnknownHostException}
import java.nio.charset.StandardCharsets
import macrocompat.bundle
import org.http4s.Uri._
import org.http4s.internal.parboiled2.{Parser => PbParser, Rule1}
import org.http4s.parser._
import org.http4s.util._
import org.http4s.util.UrlCodingUtils.{hostEncode, pathEncode, urlDecode, urlEncode}
import scala.language.experimental.macros
import scala.math.Ordered
import scala.reflect.macros.whitebox.Context

/** Representation of the [[Request]] URI
  * @param scheme     optional Uri Scheme. eg, http, https
  * @param authority  optional Uri Authority. eg, localhost:8080, www.foo.bar
  * @param path       url-encoded string representation of the path component of the Uri.
  * @param query      optional Query. url-encoded.
  * @param fragment   optional Uri Fragment. url-encoded.
  */
final case class Uri(
    scheme: Option[Scheme] = None,
    authority: Option[Authority] = None,
    path: Path = "",
    query: Query = Query.empty,
    fragment: Option[Fragment] = None)
    extends QueryOps
    with Renderable {

  def withSchemeOption(scheme: Option[Scheme]) = copy(scheme = scheme)
  def withScheme(scheme: Scheme): Uri = withSchemeOption(Some(scheme))
  def withoutScheme: Uri = withSchemeOption(None)

  def withPath(path: Path): Uri = copy(path = path)

  def withFragmentOption(fragment: Option[Fragment]) = copy(fragment = fragment)
  def withFragment(fragment: Fragment): Uri = withFragmentOption(Some(fragment))
  def withoutFragment: Uri = withFragmentOption(None)

  def /(newSegment: Path): Uri = {
    val encoded = pathEncode(newSegment)
    val newPath =
      if (path.isEmpty || path.last != '/') s"$path/$encoded"
      else s"$path$encoded"
    copy(path = newPath)
  }

  def withAuthorityOption(authority: Option[Authority]): Uri = copy(authority = authority)
  def withAuthority(authority: Authority): Uri = withAuthorityOption(Some(authority))
  def withoutAuthority: Uri = withAuthorityOption(None)

  def host: Option[Host] = authority.map(_.host)
  def withHostOption(host: Option[Host]) =
    withAuthorityOption(host.fold(Option.empty[Authority])(h =>
      Some(authority.fold(Authority(host = h))(_.withHost(h)))))
  def withHost(host: Host) = withHostOption(Some(host))

  def port: Option[Port] = authority.flatMap(_.port)
  def withoutPort: Uri = withAuthorityOption(authority.map(_.withoutPort))

  def userInfo: Option[UserInfo] = authority.flatMap(_.userInfo)
  def withoutUserInfo: Uri = withAuthorityOption(authority.map(_.withoutUserInfo))

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
  def multiParams: Map[String, Seq[String]] = query.multiParams

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
      writer << '#' << UrlCodingUtils.urlEncode(f.value, spaceIsPlus = false)
    }
    writer
  }

  /////////// Query Operations ///////////////
  override protected type Self = Uri

  override protected def self: Self = this

  override protected def replaceQuery(query: Query): Self = copy(query = query)
}

object Uri extends UriFunctions {

  /** Each [[org.http4s.Uri]] begins with a scheme name that refers to a
    * specification for assigning identifiers within that scheme.
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
      import Rfc3986Predicates._
      def scheme = rule {
        "https" ~ push(https) |
          "http" ~ push(http) |
          capture(ALPHA ~ zeroOrMore(schemeChar)) ~> (new Scheme(_))
      }
    }

    implicit val http4sInstancesForScheme: Show[Scheme] with HttpCodec[Scheme] with Order[Scheme] =
      new Show[Scheme] with HttpCodec[Scheme] with Order[Scheme] {
        def show(s: Scheme): String = s.toString

        def parse(s: String): ParseResult[Scheme] =
          Scheme.parse(s)

        def render(writer: Writer, scheme: Scheme): writer.type =
          writer << scheme.value

        def compare(x: Scheme, y: Scheme) =
          x.compareTo(y)
      }
  }

  final class Port private (val toInt: Int) extends AnyVal with Ordered[Port] {
    override def toString = s"Port($toInt)"

    override def compare(other: Port): Int =
      toInt.compare(other.toInt)
  }

  object Port {
    val ephemeral = new Port(0)
    val http = new Port(80)
    val https = new Port(443)
    val `http-alt` = new Port(8080)

    def parse(s: String): ParseResult[Port] =
      new Http4sParser[Port](s, "Invalid port") with Parser {
        def main = port
      }.parse

    def fromInt(i: Int): Either[InvalidPortNumber, Port] =
      if (i >= 0 && i < 65536) Right(new Port(i))
      else Left(InvalidPortNumber(i))

    final case class InvalidPortNumber(i: Int)
        extends IllegalArgumentException(s"Invalid port number: ${i}")

    private[http4s] trait Parser { self: PbParser =>
      import Rfc3986Predicates._
      def port = rule {
        capture((1 to 5).times(DIGIT)) ~ run { s: String =>
          val i = s.toInt
          if (i < 65536) push(new Port(i))
          else MISMATCH
        }
      }
    }

    implicit val http4sInstancesForPort: Show[Port] with HttpCodec[Port] with Order[Port] =
      new Show[Port] with HttpCodec[Port] with Order[Port] {
        def show(s: Port): String = s.toString

        def parse(s: String): ParseResult[Port] =
          Port.parse(s)

        def render(writer: Writer, port: Port): writer.type =
          writer << port.toInt

        def compare(x: Port, y: Port) =
          x.compareTo(y)
      }
  }

  /** The userinfo subcomponent may consist of a user name and, optionally,
    * scheme-specific information about how to gain authorization to access the
    * resource.
    *
    * Password is transmitted in clear text.  If you use it, you are bad and
    * you should feel bad.
    *
    * @see https://www.ietf.org/rfc/rfc3986.txt, Section 3.2.1
    */
  final case class UserInfo(username: String, password: Option[String] = None)
      extends Ordered[UserInfo] {
    // password intentionally omitted
    override def toString = s"UserInfo($username,<REDACTED>)"

    override def compare(other: UserInfo): Int =
      username.compare(other.username) match {
        case 0 =>
          (password, other.password) match {
            case (Some(p0), Some(p1)) => p0.compare(p1)
            case (Some(_), None) => 1
            case (None, Some(_)) => -1
            case (None, None) => 0
          }
        case nonZero => nonZero
      }

    def withUsername(username: String): UserInfo = copy(username = username)

    def withPasswordOption(password: Option[String]) = copy(password = password)
    def withPassword(password: String): UserInfo = withPasswordOption(Some(password))
    def withoutPassword: UserInfo = withPasswordOption(None)
  }

  object UserInfo {
    def parse(s: String): ParseResult[UserInfo] =
      new Http4sParser[UserInfo](s, "Invalid user info") with Parser {
        def main = userinfo
      }.parse

    private[http4s] trait Parser extends Rfc3986Rules { self: PbParser =>
      import Rfc3986Predicates._
      def userinfo = rule {
        run(sb.setLength(0)) ~
          oneOrMore(userInfoChar ~ appendSB() | `pct-encoded`) ~ push {
          val s = sb.toString
          val sep = s.indexOf(':')
          if (sep < 0) UserInfo(urlDecode(s))
          else {
            val username = urlDecode(s.take(sep))
            val password = urlDecode(s.drop(sep + 1))
            UserInfo(username, Some(password))
          }
        }
      }
    }

    implicit val http4sInstancesForUserInfo
      : Show[UserInfo] with HttpCodec[UserInfo] with Order[UserInfo] =
      new Show[UserInfo] with HttpCodec[UserInfo] with Order[UserInfo] {
        def show(s: UserInfo): String = s.toString

        def parse(s: String): ParseResult[UserInfo] =
          UserInfo.parse(s)

        def render(writer: Writer, userInfo: UserInfo): writer.type = {
          writer << urlEncode(userInfo.username)
          userInfo.password.foreach(p => writer << ":" << urlEncode(p))
          writer
        }

        def compare(x: UserInfo, y: UserInfo) =
          x.compareTo(y)
      }
  }

  sealed abstract class Host {
    def inetAddress: Either[UnknownHostException, InetAddress]
    def value: String
    def render(w: Writer): w.type

    final override def equals(o: Any) = o match {
      case that: Host => this.value.equalsIgnoreCase(that.value)
      case _ => false
    }

    final override def hashCode(): Int = value.##

    override final def toString = s"Host($value)"
  }

  object Host {
    private def fromName(name: String) = new Host {
      def inetAddress =
        try Right(InetAddress.getByName(name))
        catch { case e: UnknownHostException => Left(e) }
      def value = name
      def render(w: Writer): w.type = w << hostEncode(value)
    }

    private def fromIPv4(address: InetAddress) = new Host {
      def inetAddress = Right(address)
      def value = util.toAddrString(address)
      def render(w: Writer): w.type = w << value
    }

    private def fromIPv6(address: InetAddress) = new Host {
      def inetAddress = Right(address)
      def value = s"[${util.toAddrString(address)}]"
      def render(w: Writer): w.type = w << value
    }

    private def fromIPvFuture(s: String) = new Host {
      def inetAddress = Left(new UnknownHostException("Can't resolve host from future IP format"))
      def value = s"[$s]"
      def render(w: Writer): w.type = w << value
    }

    val localhost: Host = fromName("localhost")
    val `*` : Host = new Host {
      def inetAddress = Left(new UnknownHostException("* is not a hostname"))
      def value = "*"
      def render(w: Writer): w.type = w << "*"
    }

    def parse(s: String): ParseResult[Host] =
      new Http4sParser[Host](s, "Invalid host") with Parser {
        def main = host
      }.parse

    private[http4s] trait Parser extends IpParser with Rfc3986Rules { self: PbParser =>
      import Rfc3986Predicates._
      def host: Rule1[Host] = rule {
        `IP-literal` |
          capture(IPv4Address) ~> { s: String =>
            fromIPv4(InetAddress.getByName(s))
          } |
          `reg-name`
      }

      def `IP-literal`: Rule1[Host] = rule {
        "[" ~ (
          capture(IPv6Address) ~> { s: String =>
            fromIPv6(InetAddress.getByName(s))
          } |
            IPvFuture
        ) ~ "]"
      }

      def IPvFuture: Rule1[Host] = rule {
        capture("v" ~ oneOrMore(HEXDIG) ~ "." ~ oneOrMore(unreserved | `sub-delims` | ':')) ~> {
          s: String =>
            fromIPvFuture(s)
        }
      }

      def `reg-name`: Rule1[Host] = rule {
        run(sb.setLength(0)) ~
          zeroOrMore(unreserved ~ appendSB() | `pct-encoded` | `sub-delims` ~ appendSB()) ~
          run(push(fromName(urlDecode(sb.toString))))
      }
    }

    implicit val http4sInstancesForHost: Show[Host] with HttpCodec[Host] with Eq[Host] =
      new Show[Host] with HttpCodec[Host] with Eq[Host] {
        def show(s: Host): String = s.toString

        def parse(s: String): ParseResult[Host] = Host.parse(s)

        def render(writer: Writer, host: Host): writer.type = host.render(writer)

        def eqv(a: Host, b: Host): Boolean = a == b
      }
  }

  final case class Authority(
      userInfo: Option[UserInfo] = None,
      host: Host = Host.localhost,
      port: Option[Port] = None) {
    def withUserInfoOption(userInfo: Option[UserInfo]): Authority = copy(userInfo = userInfo)
    def withUserInfo(userInfo: UserInfo): Authority = withUserInfoOption(Some(userInfo))
    def withoutUserInfo: Authority = withUserInfoOption(None)

    def withHost(host: Host): Authority = copy(host = host)

    def withPortOption(port: Option[Port]): Authority = copy(port = port)
    def withPort(port: Port): Authority = withPortOption(Some(port))
    def withoutPort: Authority = withPortOption(None)
  }

  object Authority {
    def parse(s: String): ParseResult[Authority] =
      new Http4sParser[Authority](s, "Invalid authority") with Parser {
        def main = authority
      }.parse

    private[http4s] trait Parser extends Host.Parser with UserInfo.Parser with Port.Parser {
      self: PbParser =>
      def authority: Rule1[Authority] = rule {
        optional(userinfo ~ "@") ~ host ~ optional(":" ~ port) ~> (Authority(_, _, _))
      }
    }

    implicit val http4sInstancesForAuthority
      : Show[Authority] with HttpCodec[Authority] with Eq[Authority] =
      new Show[Authority] with HttpCodec[Authority] with Eq[Authority] {
        def show(s: Authority): String = s.toString

        def parse(s: String): ParseResult[Authority] = Authority.parse(s)

        def render(writer: Writer, authority: Authority): writer.type =
          authority match {
            case Authority(Some(u), h, None) => writer << u << '@' << h
            case Authority(Some(u), h, Some(p)) => writer << u << '@' << h << ':' << p
            case Authority(None, h, Some(p)) => writer << h << ':' << p
            case Authority(None, h, None) => writer << h
          }

        def eqv(a: Authority, b: Authority): Boolean = a == b
      }
  }

  /** The fragment identifier component of a [[org.http4s.Uri]] allows indirect
    * identification of a secondary resource by reference to a primary resource
    * and additional identifying information.
    *
    * @see https://www.ietf.org/rfc/rfc3986.txt, Section 3.5
    */
  final class Fragment private (val value: String) extends AnyVal with Ordered[Fragment] {
    override def toString(): String = s"Fragment($value)"

    override def compare(other: Fragment): Int =
      value.compare(other.value)
  }

  object Fragment {
    val empty: Fragment = new Fragment("")

    /** Construct a fragment from its decoded value */
    def apply(s: String): Fragment = new Fragment(s)

    /** Parse a fragment from its encoded value */
    def parse(s: String): ParseResult[Fragment] =
      new Http4sParser[Fragment](s, "fragment Invalid") with Parser {
        def main = fragment
      }.parse

    private[http4s] trait Parser extends Rfc3986Rules { self: PbParser =>
      import Rfc3986Predicates._
      def fragment = rule {
        run(sb.setLength(0)) ~
          (oneOrMore(fragmentCharNoPct ~ appendSB() | `pct-encoded`) ~ push(
            Fragment(urlDecode(sb.toString))) |
            push(Fragment.empty))
      }
    }

    implicit val http4sInstancesForFragment
      : Show[Fragment] with HttpCodec[Fragment] with Order[Fragment] =
      new Show[Fragment] with HttpCodec[Fragment] with Order[Fragment] {
        def show(s: Fragment): String = s.toString

        def parse(s: String): ParseResult[Fragment] =
          Fragment.parse(s)

        def render(writer: Writer, fragment: Fragment): writer.type =
          writer << urlEncode(fragment.value)

        def compare(x: Fragment, y: Fragment) =
          x.compareTo(y)
      }
  }

  @bundle
  class Macros(val c: Context) {
    import c.universe._

    def uriLiteral(s: c.Expr[String]): Tree =
      s.tree match {
        case Literal(Constant(s: String)) =>
          Uri
            .fromString(s)
            .fold(
              e => c.abort(c.enclosingPosition, e.details),
              qValue => q"_root_.org.http4s.Uri.fromString($s).fold(throw _, identity)"
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

  type Path = String
}

trait UriFunctions {

  /**
    * Literal syntax for URIs.  Invalid or non-literal arguments are rejected
    * at compile time.
    */
  def uri(s: String): Uri = macro Uri.Macros.uriLiteral

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
    */
  def removeDotSegments(path: Path): Path = {
    def loop(input: List[Char], output: List[Char], depth: Int): Path = input match {
      case Nil => output.reverse.mkString
      case '.' :: '.' :: '/' :: rest => loop(rest, output, depth) // remove initial ../
      case '.' :: '/' :: rest => loop(rest, output, depth) // remove initial ./
      case '/' :: '.' :: '/' :: rest => loop('/' :: rest, output, depth) // collapse initial /./
      case '/' :: '.' :: Nil => loop('/' :: Nil, output, depth) // collapse /.
      case '/' :: '.' :: '.' :: '/' :: rest => // collapse /../ and pop dir
        if (depth == 0) loop('/' :: rest, output, depth)
        else loop('/' :: rest, output.dropWhile(_ != '/').drop(1), depth - 1)
      case '/' :: '.' :: '.' :: Nil => // collapse /.. and pop dir
        if (depth == 0) loop('/' :: Nil, output, depth)
        else loop('/' :: Nil, output.dropWhile(_ != '/').drop(1), depth - 1)
      case ('.' :: Nil) | ('.' :: '.' :: Nil) => // drop orphan . or ..
        output.reverse.mkString
      case ('/' :: rest) => // move "/segment"
        val (take, leave) = rest.span(_ != '/')
        loop(leave, ('/' :: take).reverse ++ output, depth + 1)
      case _ => // move "segment"
        val (take, leave) = input.span(_ != '/')
        loop(leave, take.reverse ++ output, depth + 1)
    }
    loop(path.toList, Nil, 0)
  }
}

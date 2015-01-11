package org.http4s

import java.nio.charset.StandardCharsets

import scala.language.experimental.macros
import scala.reflect.macros.Context

import Uri._

import org.http4s.parser.{ ScalazDeliverySchemes, RequestUriParser }
import org.http4s.util.{ Writer, Renderable, CaseInsensitiveString }
import org.http4s.util.string.ToCaseInsensitiveStringSyntax

/** Representation of the [[Request]] URI
  * Structure containing information related to a Uri. All fields except the
  * query are expected to be url decoded.
  * @param scheme     optional Uri Scheme. eg, http, https
  * @param authority  optional Uri Authority. eg, localhost:8080, www.foo.bar
  * @param path       the Uri path
  * @param query      optional Query. Note that the query should _NOT_ be url decoded
  * @param fragment   optional Uri Fragment. Note that the fragment should _NOT_ be url decoded
  */
// TODO fix Location header, add unit tests
case class Uri(
  scheme: Option[CaseInsensitiveString] = None,
  authority: Option[Authority] = None,
  path: Path = "/",
  query: Query = Query.empty,
  fragment: Option[Fragment] = None)
  extends QueryOps with Renderable
{
  def withPath(path: Path): Uri = copy(path = path)

  def host: Option[Host] = authority.map(_.host)
  def port: Option[Int] = authority.flatMap(_.port)
  def userInfo: Option[UserInfo] = authority.flatMap(_.userInfo)

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
  lazy val multiParams: Map[String, Seq[String]] = query.multiParams

  /**
   * View of the head elements of the URI parameters in query string.
   *
   * In case a parameter has no value the map returns an empty string.
   *
   * @see multiParams
   */
  lazy val params: Map[String, String] = query.paramsView

  override lazy val renderString: String =
    super.renderString

  override def render(writer: Writer): writer.type = this match {
    case Uri(Some(s), Some(a), "/", q, None) if q.isEmpty =>
      renderSchemeAndAuthority(writer, s, a)

    case Uri(Some(s), Some(a), path, params, fragment) =>
      renderSchemeAndAuthority(writer, s, a)
      writer.append(path)
      renderParamsAndFragment(writer, params, fragment)

    case Uri(Some(s), None, path, params, fragment) =>
      renderScheme(writer, s)
      writer.append(path)
      renderParamsAndFragment(writer, params, fragment)

    case Uri(None, Some(a), path, params, fragment) =>
      writer << a << path
      renderParamsAndFragment(writer, params, fragment)

    case Uri(None, None, path, params, fragment) =>
      writer.append(path)
      renderParamsAndFragment(writer, params, fragment)
  }

  /////////// Query Operations ///////////////
  override protected type Self = Uri

  override protected def self: Self = this

  override protected def replaceQuery(query: Query): Self = copy(query = query)
}

object Uri extends UriFunctions {
  object macros {
    def uriLiteral(c: Context)(s: c.Expr[String]): c.Expr[Uri] = {
      import c.universe._

      s.tree match {
        case Literal(Constant(s: String)) =>
          Uri.fromString(s).fold(
            e => c.abort(c.enclosingPosition, e.details),
            qValue => c.Expr(q"Uri.fromString($s).valueOr(e => throw new org.http4s.ParseException(e))")
          )
        case _ =>
          c.abort(c.enclosingPosition, s"only supports literal Strings")
      }
    }
  }

  /** Decodes the String to a [[Uri]] using the RFC 3986 uri decoding specification */
  def fromString(s: String): ParseResult[Uri] = new RequestUriParser(s, StandardCharsets.UTF_8).RequestUri
    .run()(ScalazDeliverySchemes.Disjunction)

  type Scheme = CaseInsensitiveString

  type UserInfo = String

  type Path = String
  type Fragment = String

  case class Authority(
    userInfo: Option[UserInfo] = None,
    host: Host = RegName("localhost"),
    port: Option[Int] = None) extends Renderable {

    override def render(writer: Writer): writer.type = this match {
      case Authority(Some(u), h, None)    => writer << u << '@' << h
      case Authority(Some(u), h, Some(p)) => writer << u << '@' << h << ':' << p
      case Authority(None, h, Some(p))    => writer << h << ':' << p
      case Authority(_, h, _)             => writer << h
      case _                              => writer
    }
  }

  sealed trait Host extends Renderable {
    final def value: String = this match {
      case RegName(h) => h.toString
      case IPv4(a)    => a.toString
      case IPv6(a)    => a.toString
    }

    override def render(writer: Writer): writer.type = this match {
      case RegName(n) => writer << n
      case IPv4(a)    => writer << a
      case IPv6(a)    => writer << '[' << a << ']'
      case _          => writer
    }
  }

  case class RegName(host: CaseInsensitiveString) extends Host
  case class IPv4(address: CaseInsensitiveString) extends Host
  case class IPv6(address: CaseInsensitiveString) extends Host

  object RegName { def apply(name: String) = new RegName(name.ci) }
  object IPv4 { def apply(address: String) = new IPv4(address.ci) }
  object IPv6 { def apply(address: String) = new IPv6(address.ci) }

  private def renderScheme(writer: Writer, s: Scheme): writer.type =
    writer << s << ':'

  private def renderSchemeAndAuthority(writer: Writer, s: Scheme, a: Authority): writer.type =
    renderScheme(writer, s) << "//" << a


  private def renderParamsAndFragment(writer: Writer, p: Query, f: Option[Fragment]): writer.type = {
    if (p.nonEmpty) writer << '?' << p
    if (f.isDefined) writer << '#' << f.get
    writer
  }
}

trait UriFunctions {
  /**
   * Literal syntax for URIs.  Invalid or non-literal arguments are rejected
   * at compile time.
   */
  def uri(s: String): Uri = macro Uri.macros.uriLiteral
}

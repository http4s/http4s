package org.http4s

import java.nio.charset.StandardCharsets

import scala.language.experimental.macros
import scala.reflect.macros.Context

import org.http4s.Uri._

import org.http4s.parser.{ ScalazDeliverySchemes, RequestUriParser }
import org.http4s.util.{ Writer, Renderable, CaseInsensitiveString, UrlCodingUtils, UrlFormCodec }
import org.http4s.util.string.ToCaseInsensitiveStringSyntax
import org.http4s.util.option.ToOptionOps


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
  path: Path = "",
  query: Query = Query.empty,
  fragment: Option[Fragment] = None)
  extends QueryOps with Renderable
{
  def withPath(path: Path): Uri = copy(path = path)

  def /(newFragment: Path): Uri = {
    val newPath = if (path.isEmpty || path.last != '/') path + "/" + newFragment else path + newFragment
    copy(path = newPath)
  }

  def host: Option[Host] = authority.map(_.host)
  def port: Option[Int] = authority.flatMap(_.port)
  def userInfo: Option[UserInfo] = authority.flatMap(_.userInfo)

  def resolve(relative: Uri): Uri = Uri.resolve(this,relative)

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
            qValue => c.Expr(q"org.http4s.Uri.fromString($s).valueOr(e => throw new org.http4s.ParseException(e))")
          )
        case _ =>
          c.abort(c.enclosingPosition, s"only supports literal Strings")
      }
    }
  }

  /** Decodes the String to a [[Uri]] using the RFC 3986 uri decoding specification */
  def fromString(s: String): ParseResult[Uri] = new RequestUriParser(s, StandardCharsets.UTF_8).Uri
    .run()(ScalazDeliverySchemes.Disjunction)

  /** Decodes the String to a [[Uri]] using the RFC 7230 section 5.3 uri decoding specification */
  def requestTarget(s: String): ParseResult[Uri] = new RequestUriParser(s, StandardCharsets.UTF_8).RequestUri
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
    if (f.isDefined) writer << '#' << UrlCodingUtils.urlEncode(f.get, spaceIsPlus = false)
    writer
  }
}

trait UriFunctions {
  /**
   * Literal syntax for URIs.  Invalid or non-literal arguments are rejected
   * at compile time.
   */
  def uri(s: String): Uri = macro Uri.macros.uriLiteral

  /**
   * Resolve a relative Uri reference, per RFC 3986 sec 5.2
   */
  def resolve(base: Uri, reference: Uri): Uri = {

    /** Merge paths per RFC 3986 5.2.3 */
    def merge(base: Path, reference: Path): Path =
      base.substring(0, base.lastIndexOf('/')+1) + reference

    val target = (base,reference) match {
      case (_,               Uri(Some(_),_,_,_,_))      => reference
      case (Uri(s,_,_,_,_),  Uri(_,a@Some(_),p,q,f))    => Uri(s,a,p,q,f)
      case (Uri(s,a,p,q,_),  Uri(_,_,"",Query.empty,f)) => Uri(s,a,p,q,f)
      case (Uri(s,a,p,_,_),  Uri(_,_,"",q,f))           => Uri(s,a,p,q,f)
      case (Uri(s,a,bp,_,_), Uri(_,_,p,q,f)) =>
        if (p.headOption.contains('/')) Uri(s,a,p,q,f)
        else Uri(s,a,merge(bp,p),q,f)
    }

    target.withPath(removeDotSequences(target.path))
  }

  /**
   * Remove dot sequences from a Path, per RFC 3986 Sec 5.2.4
   */
  private[http4s] def removeDotSequences(path: Path): Path = {
    def loop(input: List[Char], output: List[Char], depth: Int = 0): Path = input match {
      case Nil                              => output.reverse.mkString
      case '.' :: '.' :: '/' :: rest        => loop(rest, output, depth)        // remove initial ../
      case '.' :: '/' :: rest               => loop(rest, output, depth)        // remove initial ./
      case '/' :: '.' :: '/' :: rest        => loop('/' :: rest, output, depth) // collapse initial /./
      case '/' :: '.' :: Nil                => loop('/' :: Nil, output, depth)  // collapse /.
      case '/' :: '.' :: '.' :: '/' :: rest =>                                 // collapse /../ and pop dir
        if (depth == 0) loop('/' :: rest, output, depth)
        else loop('/' :: rest, output.dropWhile(_ != '/').drop(1), depth-1)
      case '/' :: '.' :: '.' :: Nil =>                                         // collapse /.. and pop dir
        if (depth == 0) loop('/' :: Nil, output, depth)
        else loop('/' :: Nil, output.dropWhile(_ != '/').drop(1), depth-1)
      case ('.' :: Nil) | ('.' :: '.' :: Nil) =>                               // drop orphan . or ..
        output.reverse.mkString
      case ('/' :: rest) =>                                                    // move "/segment"
        val (take,leave) = rest.span(_ != '/')
        loop(leave, ('/' :: take).reverse ++ output, depth+1)
      case _ =>                                                                // move "segment"
        val (take,leave) = input.span(_ != '/')
        loop(leave, take.reverse ++ output, depth+1)
    }
    loop(path.toList, Nil, 0)
  }
}

package org.http4s

import java.nio.charset.StandardCharsets

import scala.language.experimental.macros
import scala.reflect.macros.Context

import Uri._

import scala.collection.mutable

import org.http4s.parser.{ ScalazDeliverySchemes, RequestUriParser }
import org.http4s.util.{ Writer, Renderable, CaseInsensitiveString }
import org.http4s.util.string.ToCaseInsensitiveStringSyntax

import scalaz.Maybe
import scalaz.syntax.std.option._

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
  fragment: Option[Fragment] = None) extends Renderable {
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
  lazy val multiParams: Map[String, Seq[String]] = query.asMap

  /**
   * View of the head elements of the URI parameters in query string.
   *
   * In case a parameter has no value the map returns an empty string.
   *
   * @see multiParams
   */
  lazy val params: Map[String, String] = new ParamsView(multiParams)

  override lazy val renderString: String =
    super.renderString

  private class ParamsView(wrapped: Map[String, Seq[String]]) extends Map[String, String] {
    override def +[B1 >: String](kv: (String, B1)): Map[String, B1] = {
      val m = wrapped + (kv)
      m.asInstanceOf[Map[String, B1]]
    }

    override def -(key: String): Map[String, String] = new ParamsView(wrapped - key)

    override def iterator: Iterator[(String, String)] =
      wrapped.iterator.map { case (k, s) => (k, s.headOption.getOrElse("")) }

    override def get(key: String): Option[String] =
      wrapped.get(key).flatMap(_.headOption)
  }

  /** alias for containsQueryParam */
  def ?[K: QueryParamKeyLike](name: K): Boolean =
    _containsQueryParam(QueryParamKeyLike[K].getKey(name))

  /** alias for setQueryParams */
  def =?[T: QueryParamEncoder](q: Map[String, Seq[T]]): Uri =
    setQueryParams(q)

  /** alias for withQueryParam */
  def +?[T: QueryParam]: Uri =
    _withQueryParam(QueryParam[T].key, Nil)

  /** alias for withQueryParam */
  def +*?[T: QueryParam : QueryParamEncoder](value: T): Uri =
    _withQueryParam(QueryParam[T].key, QueryParamEncoder[T].encode(value)::Nil)

  /** alias for withQueryParam */
  def +*?[T: QueryParam : QueryParamEncoder](values: Seq[T]): Uri =
    _withQueryParam(QueryParam[T].key, values.map(QueryParamEncoder[T].encode))

  /** alias for withQueryParam */
  def +?[K: QueryParamKeyLike, T: QueryParamEncoder](name: K, value: T): Uri =
    +?(name, value::Nil)

  /** alias for withQueryParam */
  def +?[K: QueryParamKeyLike](name: K): Uri =
    _withQueryParam(QueryParamKeyLike[K].getKey(name), Nil)

  /** alias for withQueryParam */
  def +?[K: QueryParamKeyLike, T: QueryParamEncoder](name: K, values: Seq[T]): Uri =
    _withQueryParam(QueryParamKeyLike[K].getKey(name), values.map(QueryParamEncoder[T].encode))

  /** alias for withMaybeQueryParam */
  def +??[K: QueryParamKeyLike, T: QueryParamEncoder](name: K, value: Maybe[T]): Uri =
    _withMaybeQueryParam(QueryParamKeyLike[K].getKey(name), value map QueryParamEncoder[T].encode)

  /** alias for withMaybeQueryParam */
  def +??[T: QueryParam : QueryParamEncoder](value: Maybe[T]): Uri =
    _withMaybeQueryParam(QueryParam[T].key, value map QueryParamEncoder[T].encode)

  /** alias for withOptionQueryParam */
  def +??[K: QueryParamKeyLike, T: QueryParamEncoder](name: K, value: Option[T]): Uri =
    _withMaybeQueryParam(QueryParamKeyLike[K].getKey(name), value.toMaybe map QueryParamEncoder[T].encode)

  /** alias for withOptionQueryParam */
  def +??[T: QueryParam : QueryParamEncoder](value: Option[T]): Uri =
    _withMaybeQueryParam(QueryParam[T].key, value.toMaybe map QueryParamEncoder[T].encode)

  /** alias for removeQueryParam */
  def -?[T](implicit key: QueryParam[T]): Uri =
    _removeQueryParam(key.key)

  /** alias for removeQueryParam */
  def -?[K: QueryParamKeyLike](key: K): Uri =
    _removeQueryParam(QueryParamKeyLike[K].getKey(key))

  /**
   * Checks if a specified parameter exists in query string. A parameter
   * without a name can be checked with an empty string.
   */
  def containsQueryParam[T](implicit key: QueryParam[T]): Boolean =
    _containsQueryParam(key.key)

  def containsQueryParam[K: QueryParamKeyLike](key: K): Boolean =
    _containsQueryParam(QueryParamKeyLike[K].getKey(key))

  private def _containsQueryParam(name: QueryParameterKey): Boolean = {
    if (query.isEmpty) false
    else query.exists { case (k, _) => k == name.value }
  }

  /**
   * Creates maybe a new `Uri` without the specified parameter in query string.
   * If no parameter with the given `key` exists the same `Uri` will be
   * returned. If the parameter to be removed is not present the existing `Uri`
   * instance of `Uri` will be returned.
   */
  def removeQueryParam[K: QueryParamKeyLike](key: K): Uri =
    _removeQueryParam(QueryParamKeyLike[K].getKey(key))

  private def _removeQueryParam(name: QueryParameterKey): Uri = {
    if (query.isEmpty) this
    else {
      val newQuery = query.filterNot { case (n, _) => n == name.value }
      copy(query = newQuery)
    }
  }

  /**
   * Creates maybe a new `Uri` with the specified parameters. The entire
   * query string will be replaced with the given one. If the given parameters
   * equal the existing the same `Uri` instance will be returned.
   */
  def setQueryParams[T: QueryParamEncoder](query: Map[String, Seq[T]]): Uri = {
    if (multiParams == query) this
    else {
      val enc = QueryParamEncoder[T]
      val b = Query.newBuilder
      query.foreach {
        case (k, Seq()) => b +=  ((k, None))
        case (k, vs)    => vs.foreach(v => b += ((k, Some(enc.encode(v).value))))
      }
      copy(query = b.result())
    }
  }

  /**
   * Creates a new `Uri` with the specified parameter in query string.
   * If a parameter with the given `QueryParam.key` already exists the values will be
   * replaced with an empty list.
   */
  def withQueryParam[T: QueryParam]: Uri =
    _withQueryParam(QueryParam[T].key, Nil)

  /**
   * Creates a new `Uri` with the specified parameter in query string.
   * If a parameter with the given `key` already exists the values will be
   * replaced with an empty list.
   */
  def withQueryParam[K: QueryParamKeyLike](key: K): Uri =
    _withQueryParam(QueryParamKeyLike[K].getKey(key), Nil)

  /**
   * Creates maybe a new `Uri` with the specified parameter in query string.
   * If a parameter with the given `key` already exists the values will be
   * replaced. If the parameter to be added equal the existing entry the same
   * instance of `Uri` will be returned.
   */
  def withQueryParam[T: QueryParamEncoder, K: QueryParamKeyLike](key: K, value: T): Uri =
    _withQueryParam(QueryParamKeyLike[K].getKey(key), QueryParamEncoder[T].encode(value)::Nil)

  /**
   * Creates maybe a new `Uri` with the specified parameters in query string.
   * If a parameter with the given `key` already exists the values will be
   * replaced. If the parameter to be added equal the existing entry the same
   * instance of `Uri` will be returned.
   */
  def withQueryParam[T: QueryParamEncoder, K: QueryParamKeyLike](key: K, values: Seq[T]): Uri =
    _withQueryParam(QueryParamKeyLike[K].getKey(key), values.map(QueryParamEncoder[T].encode))

  private def _withQueryParam(name: QueryParameterKey, values: Seq[QueryParameterValue]): Uri = {
    lazy val stringValues = values.map(_.value)
    if (multiParams.contains(name.value) && multiParams.getOrElse(name.value, Nil) == stringValues) this
    else {
      val p = multiParams updated (name.value, stringValues)
      copy(query = Query.fromMap(p))
    }
  }

  /**
   * Creates maybe a new `Uri` with the specified parameter in query string.
   * If the value is empty or if the parameter to be added equal the existing
   * entry the same instance of `Uri` will be returned.
   * If a parameter with the given `key` already exists the values will be
   * replaced.
   */
  def withMaybeQueryParam[T: QueryParamEncoder, K: QueryParamKeyLike](key: K, value: Maybe[T]): Uri =
    _withMaybeQueryParam(QueryParamKeyLike[K].getKey(key), value map QueryParamEncoder[T].encode)

  /**
   * Creates maybe a new `Uri` with the specified parameter in query string.
   * If the value is empty or if the parameter to be added equal the existing
   * entry the same instance of `Uri` will be returned.
   * If a parameter with the given `name` already exists the values will be
   * replaced.
   */
  def withMaybeQueryParam[T: QueryParam: QueryParamEncoder](value: Maybe[T]): Uri =
    _withMaybeQueryParam(QueryParam[T].key, value map QueryParamEncoder[T].encode)

  /**
   * Creates maybe a new `Uri` with the specified parameter in query string.
   * If the value is empty or if the parameter to be added equal the existing
   * entry the same instance of `Uri` will be returned.
   * If a parameter with the given `key` already exists the values will be
   * replaced.
   */
  def withOptionQueryParam[T: QueryParamEncoder, K: QueryParamKeyLike](key: K, value: Option[T]): Uri =
    _withMaybeQueryParam(QueryParamKeyLike[K].getKey(key), value.toMaybe map QueryParamEncoder[T].encode)

  /**
   * Creates maybe a new `Uri` with the specified parameter in query string.
   * If the value is empty or if the parameter to be added equal the existing
   * entry the same instance of `Uri` will be returned.
   * If a parameter with the given `name` already exists the values will be
   * replaced.
   */
  def withOptionQueryParam[T: QueryParam: QueryParamEncoder](value: Option[T]): Uri =
    _withMaybeQueryParam(QueryParam[T].key, value.toMaybe map QueryParamEncoder[T].encode)

  private def _withMaybeQueryParam(name: QueryParameterKey, value: Maybe[QueryParameterValue]): Uri =
    value.cata(v => _withQueryParam(name, v::Nil), this)

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

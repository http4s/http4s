package org.http4s

import Uri._

import scala.collection.{ immutable, mutable }
import mutable.ListBuffer
import scala.util.Try

import org.http4s.parser.{ QueryParser, RequestUriParser }
import org.http4s.util.CaseInsensitiveString
import org.http4s.util.string.ToCaseInsensitiveStringSyntax

/** Representation of the [[Request]] URI  */
case class Uri(
  scheme: Option[CaseInsensitiveString] = None,
  authority: Option[Authority] = None,
  path: Path = "/",
  query: Option[Query] = None,
  fragment: Option[Fragment] = None) {
  def withPath(path: Path): Uri = copy(path = path)

  def host: Option[Host] = authority.map(_.host)
  def port: Option[Int] = authority.flatMap(_.port)
  def userInfo: Option[UserInfo] = authority.flatMap(_.userInfo)

  /**
   * Representation of the query as a Map[String, Seq[String]]
   *
   * The query string is lazily parsed. If an error occurs during parsing
   * an empty Map is returned
   */
  lazy val multiParams: Map[String, Seq[String]] = {
    query.fold(Map.empty[String, Seq[String]]) { query =>
      QueryParser.parseQueryString(query) match {
        case Right(params) =>
          val m = mutable.Map.empty[String, ListBuffer[String]]
          params.foreach {
            case (k, None) => m.getOrElseUpdate(k, new ListBuffer)
            case (k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
          }
          m.map { case (k, lst) => (k, lst.toSeq) }.toMap
        case Left(e) => throw e
      }
    }
  }

  /**
   * View of the head elements of multiParams
   * @see multiParams
   */
  def params: Map[String, String] = new ParamsView(multiParams)

  private class ParamsView(wrapped: Map[String, Seq[String]]) extends Map[String, String] {
    override def +[B1 >: String](kv: (String, B1)): Map[String, B1] = {
      val b = immutable.Map.newBuilder[String, B1]
      wrapped.foreach { case (k, s) => b += ((k, s.head)) }
      b += kv
      b.result()
    }

    override def -(key: String): Map[String, String] = new ParamsView(wrapped - key)

    override def iterator: Iterator[(String, String)] =
      wrapped.iterator.map { case (k, s) => (k, s.headOption.getOrElse("")) }

    override def get(key: String): Option[String] =
      wrapped.get(key).map(_.headOption.getOrElse(""))
  }

  override lazy val toString =
    renderUri(this)

  /**
   * Checks if a specified parameter exists in query string. A parameter
   * without a name can be checked with an empty string.
   */
  def ?(name: String): Boolean =
    containsQueryParam(name)

  /**
   * Creates maybe a new `Uri` with the specified parameters. The entire
   * query string will be replaced with the given one. If a the given
   * parameters equal the existing one the same `Uri` instance will be
   * returned.
   */
  def =?(q: Map[String, Seq[String]]): Uri =
    setQueryParams(q)

  /**
   * Creates maybe a new `Uri` with the specified parameter in query string.
   * If a parameter with the given `name` already exists the value will be
   * replaced. If the parameter to be added equal the existing entry the same
   * instance of `Uri` will be returned.
   */
  def +?(name: String, value: String*): Uri =
    withQueryParam(name, value.toSeq)

  /**
   * Creates maybe a new `Uri` without the specified parameter in query string.
   * If no parameter with the given `name` exists the same `Uri` will be
   * returned. If the parameter to be removed is not present the existing `Uri`
   * instance of `Uri` will be returned.
   */
  def -?(name: String): Uri =
    removeQueryParam(name)

  /**
   * Checks if a specified parameter exists in query string. A parameter
   * without a name can be checked with an empty string.
   */
  def containsQueryParam(name: String): Boolean = query match {
    case Some("") => if (name == "") true else false
    case Some(_) => multiParams.contains(name)
    case None => false
  }

  /**
   * Creates maybe a new `Uri` without the specified parameter in query string.
   * If no parameter with the given `name` exists the same `Uri` will be
   * returned. If the parameter to be removed is not present the existing `Uri`
   * instance of `Uri` will be returned.
   */
  def removeQueryParam(name: String): Uri = query match {
    case Some("") =>
      if (name == "") copy(query = None)
      else this
    case Some(_) =>
      if (!multiParams.contains(name)) this
      else copy(query = renderQueryString(multiParams - name))
    case None =>
      this
  }

  /**
   * Creates maybe a new `Uri` with the specified parameters. The entire
   * query string will be replaced with the given one. If a the given
   * parameters equal the existing one the same `Uri` instance will be
   * returned.
   */
  def setQueryParams(query: Map[String, Seq[String]]): Uri = {
    if (multiParams == query) this
    else copy(query = renderQueryString(query))
  }

  /**
   * Creates maybe a new `Uri` with the specified parameter in query string.
   * If a parameter with the given `name` already exists the value will be
   * replaced. If the parameter to be added equal the existing entry the same
   * instance of `Uri` will be returned.
   */
  def withQueryParam(name: String, values: Seq[String]): Uri = {
    if (multiParams.contains(name) && multiParams.getOrElse(name, Nil) == values) this
    else {
      val p = multiParams updated (name, values)
      copy(query = renderQueryString(p))
    }
  }

}

object Uri {

  def fromString(s: String): Try[Uri] = (new RequestUriParser(s, CharacterSet.`UTF-8`.charset)).RequestUri.run()

  type Scheme = CaseInsensitiveString

  case class Authority(
    userInfo: Option[UserInfo] = None,
    host: Host = RegName("localhost"),
    port: Option[Int] = None) {
  }

  sealed trait Host
  case class RegName(host: CaseInsensitiveString) extends Host
  case class IPv4(address: CaseInsensitiveString) extends Host
  case class IPv6(address: CaseInsensitiveString) extends Host

  object RegName { def apply(name: String) = new RegName(name.ci) }
  object IPv4 { def apply(address: String) = new IPv4(address.ci) }
  object IPv6 { def apply(address: String) = new IPv6(address.ci) }

  type UserInfo = String

  type Path = String
  type Query = String
  type Fragment = String

  protected def renderAuthority(a: Authority): String = a match {
    case Authority(Some(u), h, None) => u + "@" + renderHost(h)
    case Authority(Some(u), h, Some(p)) => u + "@" + renderHost(h) + ":" + p
    case Authority(None, h, Some(p)) => renderHost(h) + ":" + p
    case Authority(_, h, _) => renderHost(h)
    case _ => ""
  }

  protected def renderHost(h: Host): String = h match {
    case RegName(n) => n.toString
    case IPv4(a) => a.toString
    case IPv6(a) => "[" + a.toString + "]"
    case _ => ""
  }

  protected def renderScheme(s: Scheme): String =
    s + ":"

  protected def renderSchemeAndAuthority(s: Scheme, a: Authority): String =
    renderScheme(s) + "//" + renderAuthority(a)

  protected def renderParamsAndFragment(p: Option[Query], f: Option[Fragment]): String = {
    val b = new StringBuilder
    if (p.isDefined) {
      b.append("?")
      b.append(p.get)
    }
    if (f.isDefined) {
      b.append("#")
      b.append(f.get)
    }
    b.toString
  }

  protected def renderUri(uri: Uri): String = {
    val b = new StringBuilder
    uri match {
      case Uri(Some(s), Some(a), "/", None, None) =>
        b.append(renderSchemeAndAuthority(s, a))
      case Uri(Some(s), Some(a), path, params, fragment) =>
        b.append(renderSchemeAndAuthority(s, a))
        b.append(path)
        b.append(renderParamsAndFragment(params, fragment))
      case Uri(Some(s), None, path, params, fragment) =>
        b.append(renderScheme(s))
        b.append(path)
        b.append(renderParamsAndFragment(params, fragment))
      case Uri(None, None, path, params, fragment) =>
        b.append(path)
        b.append(renderParamsAndFragment(params, fragment))
      case _ =>
    }
    b.toString
  }

  protected def renderQueryString(params: Map[String, Seq[String]]): Option[String] = {
    if (params.isEmpty) None
    else {
      val b = new StringBuilder
      params.foreach {
        case (n, vs) =>
          if (vs.isEmpty) {
            if (b.nonEmpty) b.append("&")
            b.append(n)
          } else {
            vs.foldLeft(b) { (b, v) =>
              if (b.nonEmpty) b.append("&")
              b.append(n + "=" + v)
            }
          }
      }
      Some(b.toString)
    }
  }

}

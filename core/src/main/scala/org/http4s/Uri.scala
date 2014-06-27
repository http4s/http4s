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

    override def iterator: Iterator[(String, String)] = wrapped.iterator.map { case (k, s) => (k, s.head) }

    override def get(key: String): Option[String] = wrapped.get(key).map(_.head)
  }

  override lazy val toString =
    renderUri(this)

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

}

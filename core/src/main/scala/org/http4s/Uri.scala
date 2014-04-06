package org.http4s

import org.http4s.util.CaseInsensitiveString

import Uri._
import org.http4s.parser.{QueryParser, RequestUriParser}
import org.http4s.util.string._
import scala.collection.mutable.ListBuffer
import scala.collection.{immutable, mutable}
import scala.util.Try

/** Representation of the [[Request]] URI  */
case class Uri (
  scheme: Option[CaseInsensitiveString] = None,
  authority: Option[Authority] = None,
  path: Path = "/",
  query: Option[Query] = None,
  fragment: Option[Fragment] = None) {
  def withPath(path: Path): Uri = copy(path = path)

  def host: Option[Host] = authority.map(_.host)
  def port: Option[Int] = authority.flatMap(_.port)

  /** Representation of the query as a Map[String, Seq[String]]
    *
    * The query string is lazily parsed. If an error occurs during parsing
    * an empty Map is returned
    */
  lazy val multiParams: Map[String, Seq[String]] = {
    query.fold(Map.empty[String,Seq[String]]){ query =>
      QueryParser.parseQueryString(query) match {
        case Right(params) =>
          val m = mutable.Map.empty[String, ListBuffer[String]]
          params.foreach { case (k,v) => m.getOrElseUpdate(k, new ListBuffer) += v }
          m.map{ case (k, lst) => (k, lst.toSeq) }.toMap

        case Left(e) => throw e
      }
    }
  }

  /** View of the head elements of multiParams
    * @see multiParams
    */
  def params: Map[String, String] = new ParamsView(multiParams)

  private class ParamsView(wrapped: Map[String, Seq[String]]) extends AnyRef with Map[String, String] {
    override def +[B1 >: String](kv: (String, B1)): Map[String, B1] = {
      val b = immutable.Map.newBuilder[String, B1]
      wrapped.foreach { case (k, s) => b += ((k, s.head))}
      b += kv
      b.result()
    }

    override def -(key: String): Map[String, String] = new ParamsView(wrapped - key)

    override def iterator: Iterator[(String, String)] = wrapped.iterator.map{ case (k, s) => (k, s.head)}

    override def get(key: String): Option[String] = wrapped.get(key).map(_.head)
  }
}

object Uri {

  def fromString(s: String): Try[Uri] = (new RequestUriParser(s, CharacterSet.`UTF-8`.charset)).RequestUri.run()

  type Scheme = CaseInsensitiveString

  case class Authority(
    userInfo: Option[UserInfo] = None,
    host: Host = "localhost".ci,
    port: Option[Int] = None
  )

  type Host = CaseInsensitiveString
  type UserInfo = String

  type Path = String
  type Query = String
  type Fragment = String
}

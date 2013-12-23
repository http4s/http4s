package org.http4s

import org.http4s.util.CaseInsensitiveString
import scalaz.NonEmptyList
import java.nio.charset.Charset
import org.http4s.parser.RequestUriParser

sealed trait RequestUri {
  def pathString: String
  def withPath(path: String): RequestUri
  def hostOption: Option[RequestUri.Host]
  def portOption: Option[Int]
  def queryString: String
}

object RequestUri {
  def fromString(s: String): RequestUri =
    (new RequestUriParser(s, Charset.forName("UTF-8"))).RequestUri.run().get

  case object `*` extends RequestUri {
    val pathString: String = "*"
    def withPath(path: String): RequestUri = {
      val segments = path.split('/')
      OriginForm(AbsolutePath(NonEmptyList(segments.head, segments.tail: _*)))
    }
    val hostOption: Option[RequestUri.Host] = None
    val portOption: Option[Int] = None
    val queryString: String = ""
  }

  case class AbsoluteUri (
    scheme: Scheme = "http".ci,
    authority: Option[Authority] = None,
    path: Path = EmptyPath,
    query: Option[Query] = None
  ) extends RequestUri {
    def pathString: String = path.toString
    def withPath(path: String): RequestUri = {
      val segments = (if (path.startsWith("/")) path.substring(1) else path).split('/')
      copy(path = AbsolutePath(NonEmptyList(segments.head, segments.tail: _*)))
    }
    def hostOption: Option[Host] = authority.map(_.host)
    def portOption: Option[Int] = authority.flatMap(_.port)
    def queryString: String = query.getOrElse("")
  }

  type Scheme = CaseInsensitiveString

  case class Authority(
    userInfo: Option[UserInfo] = None,
    host: Host = "localhost".ci,
    port: Option[Int] = None
  ) extends RequestUri {
    val pathString: String = "/"
    def withPath(path: String): RequestUri = {
      val segments = path.split('/')
      val absPath = AbsolutePath(NonEmptyList(segments.head, segments.tail: _*))
      AbsoluteUri(authority = Some(this), path = absPath)
    }
    def hostOption: Option[RequestUri.Host] = Some(host)
    def portOption: Option[Int] = port
    val queryString: String = ""
  }

  type Host = CaseInsensitiveString
  type UserInfo = String

  sealed trait Path
  case class AbsolutePath(segments: NonEmptyList[Segment]) extends Path {
    override def toString: String = segments.list.mkString("/", "/", "")
  }
  object AbsolutePath {
    val Root = AbsolutePath(NonEmptyList(""))
  }
  case class RelativePath(segments: NonEmptyList[Segment]) extends Path {
    override def toString: String = segments.list.mkString("/")
  }
  case object EmptyPath extends Path {
    override val toString: String = "/"
  }
  type Segment = String

  /**
   * Term from http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-25.
   */
  case class OriginForm(path: AbsolutePath, query: Option[Query] = None) extends RequestUri {
    def pathString: String = path.toString
    def withPath(path: String): RequestUri = {
      val segments = path.split('/')
      copy(path = AbsolutePath(NonEmptyList(segments.head, segments.tail: _*)))
    }
    val hostOption: Option[Host] = None
    val portOption: Option[Int] = None
    def queryString: String = query.getOrElse("")
  }
  object OriginForm {
    val empty: OriginForm = OriginForm(AbsolutePath.Root)
  }

  type Query = String
  type Fragment = String
}






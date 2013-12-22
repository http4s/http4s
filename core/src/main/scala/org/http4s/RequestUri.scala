package org.http4s

import org.http4s.util.CaseInsensitiveString
import scalaz.NonEmptyList

sealed trait RequestUri

object RequestUri {
  case object `*` extends RequestUri

  case class AbsoluteUri (
    scheme: Scheme = "http".ci,
    authority: Option[Authority] = None,
    path: Path = EmptyPath,
    query: Option[Query] = None
  ) extends RequestUri

  type Scheme = CaseInsensitiveString

  case class Authority(
    userInfo: Option[UserInfo] = None,
    host: Host = "localhost".ci,
    port: Option[Int] = None
  ) extends RequestUri

  type Host = CaseInsensitiveString
  type UserInfo = String

  sealed trait Path
  case class AbsolutePath(segments: NonEmptyList[Segment]) extends Path
  case class RelativePath(segments: NonEmptyList[Segment]) extends Path
  case object EmptyPath extends Path
  type Segment = String

  /**
   * Term from http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-25.
   */
  case class OriginForm(path: AbsolutePath, query: Option[Query]) extends RequestUri

  type Query = String
  type Fragment = String
}






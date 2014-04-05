package org.http4s

import org.http4s.util.CaseInsensitiveString

import Uri._
import org.http4s.parser.RequestUriParser
import org.http4s.util.string._

case class Uri (
  scheme: Option[CaseInsensitiveString] = None,
  authority: Option[Authority] = None,
  path: Path = "/",
  query: Option[Query] = None,
  fragment: Option[Fragment] = None
) {
  def withPath(path: Path): Uri = copy(path = path)

  def host: Option[Host] = authority.map(_.host)
  def port: Option[Int] = authority.flatMap(_.port)
}

object Uri {
  def fromString(s: String): Uri = (new RequestUriParser(s, CharacterSet.`UTF-8`.charset)).RequestUri.run().get

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

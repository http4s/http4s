package org.http4s.optics

import monocle.function._
import monocle.macros.{GenLens, GenPrism}
import monocle.std.list._
import monocle.{Iso, Lens, Prism, Traversal}
import org.http4s.Header.Raw
import org.http4s.Uri.{Authority, Fragment, Path}
import org.http4s._
import org.http4s.util.CaseInsensitiveString

object message {
  val request: Prism[Message, Request] = GenPrism[Message, Request]
  val response: Prism[Message, Response] = GenPrism[Message, Response]
}

object request {
  val method: Lens[Request, Method] = GenLens[Request](_.method)
  val uri: Lens[Request, Uri] = GenLens[Request](_.uri)
  val httpVersion: Lens[Request, HttpVersion] = GenLens[Request](_.httpVersion)
  val headers: Lens[Request, Headers] = GenLens[Request](_.headers)
  val body: Lens[Request, EntityBody] = GenLens[Request](_.body)
  val attributes: Lens[Request, AttributeMap] = GenLens[Request](_.attributes)
}

object response {
  val method: Lens[Response, Status] = GenLens[Response](_.status)
  val httpVersion: Lens[Response, HttpVersion] = GenLens[Response](_.httpVersion)
  val headers: Lens[Response, Headers] = GenLens[Response](_.headers)
  val body: Lens[Response, EntityBody] = GenLens[Response](_.body)
  val attributes: Lens[Response, AttributeMap] = GenLens[Response](_.attributes)
}

object method {
  val DELETE: Prism[Method, Unit] = Prism.only(Method.DELETE)
  val GET: Prism[Method, Unit] = Prism.only(Method.GET)
  val PUT: Prism[Method, Unit] = Prism.only(Method.PUT)
  val POST: Prism[Method, Unit] = Prism.only(Method.POST)
}

object httpversion {
  val `HTTP/1.0`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/1.0`)
  val `HTTP/1.1`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/1.1`)
  val `HTTP/2.0`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/2.0`)
}

object headers {
  val headersToList: Iso[Headers, List[Header]] = Iso[Headers, List[Header]](_.toList)(Headers(_))

  implicit val atHeaders: At[Headers, CaseInsensitiveString, String] = new At[Headers, CaseInsensitiveString, String] {
    override def at(i: CaseInsensitiveString): Lens[Headers, Option[String]] =
      Lens[Headers, Option[String]](_.get(i).map(_.value)){
        case None    => hs => Headers(hs.toList.filterNot(_.name == i))
        case Some(v) => _.put(Raw(i, v))
      }
  }

  implicit val indexHeaders: Index[Headers, CaseInsensitiveString, String] =
    Index.atIndex(atHeaders)

  implicit val eachHeaders: Each[Headers, String] = new Each[Headers, String] {
    override def each: Traversal[Headers, String] =
      headersToList composeTraversal Each.each composeLens header.value
  }

}

object header {
  val name: Lens[Header, CaseInsensitiveString] = Lens[Header, CaseInsensitiveString](_.name)(n => h => Raw(n, h.value))
  val value: Lens[Header, String] = Lens[Header, String](_.value)(v => h => Header(h.name.value, v))
}

object uri {
  val scheme: Lens[Uri, Option[CaseInsensitiveString]] = GenLens[Uri](_.scheme)
  val authority: Lens[Uri, Option[Authority]] = GenLens[Uri](_.authority)
  val path: Lens[Uri, Path] = GenLens[Uri](_.path)
  val query: Lens[Uri, Query] = GenLens[Uri](_.query)
  val fragment: Lens[Uri, Option[Fragment]] = GenLens[Uri](_.fragment)
}
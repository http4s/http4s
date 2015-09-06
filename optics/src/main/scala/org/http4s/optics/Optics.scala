package org.http4s.optics

import monocle.{Iso, Lens, Prism}
import monocle.function._
import monocle.macros.{GenLens, GenPrism}
import org.http4s.Uri.{Fragment, Path, Authority}
import org.http4s._
import org.http4s.util.CaseInsensitiveString

object message {
  val _request: Prism[Message, Request] = GenPrism[Message, Request]
  val _response: Prism[Message, Response] = GenPrism[Message, Response]
}

object request {
  val _method: Lens[Request, Method] = GenLens[Request](_.method)
  val _uri: Lens[Request, Uri] = GenLens[Request](_.uri)
  val _httpVersion: Lens[Request, HttpVersion] = GenLens[Request](_.httpVersion)
  val _headers: Lens[Request, Headers] = GenLens[Request](_.headers)
  val _body: Lens[Request, EntityBody] = GenLens[Request](_.body)
  val _attributes: Lens[Request, AttributeMap] = GenLens[Request](_.attributes)
}

object response {
  val _method: Lens[Response, Status] = GenLens[Response](_.status)
  val _httpVersion: Lens[Response, HttpVersion] = GenLens[Response](_.httpVersion)
  val _headers: Lens[Response, Headers] = GenLens[Response](_.headers)
  val _body: Lens[Response, EntityBody] = GenLens[Response](_.body)
  val _attributes: Lens[Response, AttributeMap] = GenLens[Response](_.attributes)
}

object method {
  val _DELETE: Prism[Method, Unit] = Prism.only(Method.DELETE)
  val _GET: Prism[Method, Unit] = Prism.only(Method.GET)
  val _PUT: Prism[Method, Unit] = Prism.only(Method.PUT)
  val _POST: Prism[Method, Unit] = Prism.only(Method.POST)
}

object httpversion {
  val `_HTTP/1.0`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/1.0`)
  val `_HTTP/1.1`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/1.1`)
  val `_HTTP/2.0`: Prism[HttpVersion, Unit] = Prism.only(HttpVersion.`HTTP/2.0`)
}

object headers {
  val headersToList: Iso[Headers, List[Header]] = Iso[Headers, List[Header]](_.toList)(Headers(_))

  implicit val atHeaders: At[Headers, CaseInsensitiveString, Header] = new At[Headers, CaseInsensitiveString, Header] {
    override def at(i: CaseInsensitiveString): Lens[Headers, Option[Header]] =
      Lens[Headers, Option[Header]](_.get(i)){
        case None    => hs => Headers(hs.toList.filterNot(_.name == i))
        case Some(h) => _.put(h)
      }
  }

  implicit val indexHeaders: Index[Headers, CaseInsensitiveString, Header] =
    Index.atIndex(atHeaders)
}

object header {
  val _value: Lens[Header, String] = Lens[Header, String](_.value)(v => h => Header(h.name.value, v))
}

object uri {
  val _scheme: Lens[Uri, Option[CaseInsensitiveString]] = GenLens[Uri](_.scheme)
  val _authority: Lens[Uri, Option[Authority]] = GenLens[Uri](_.authority)
  val _path: Lens[Uri, Path] = GenLens[Uri](_.path)
  val _query: Lens[Uri, Query] = GenLens[Uri](_.query)
  val _fragment: Lens[Uri, Option[Fragment]] = GenLens[Uri](_.fragment)
}
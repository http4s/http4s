package org.http4s.optics

import monocle.function._
import monocle.macros.{GenLens, GenPrism}
import monocle.std.list._
import monocle.std.map._
import monocle.{Iso, Lens, Prism, Traversal}
import org.http4s.Header.Raw
import org.http4s.Uri.{Authority, Fragment, Path}
import org.http4s._
import org.http4s.util.CaseInsensitiveString

import scalaz.NonEmptyList

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

  val headersToMap: Iso[Headers, Map[CaseInsensitiveString, NonEmptyList[String]]] =
    Iso[Headers, Map[CaseInsensitiveString, NonEmptyList[String]]](
      _.foldLeft(Map.empty[CaseInsensitiveString, NonEmptyList[String]])((acc, h) =>
        acc + (h.name -> acc.get(h.name).fold(NonEmptyList(h.value))(_.<::(h.value)))
      )
    )(_.foldLeft(Headers.empty){case (acc, (name, values)) => acc ++ Headers(values.list.map(Raw(name, _)))})

  implicit val atHeaders: At[Headers, CaseInsensitiveString, NonEmptyList[String]] = new At[Headers, CaseInsensitiveString, NonEmptyList[String]] {
    override def at(i: CaseInsensitiveString): Lens[Headers, Option[NonEmptyList[String]]] =
      headersToMap composeLens At.at(i)
  }

  implicit val indexHeaders: Index[Headers, CaseInsensitiveString, NonEmptyList[String]] =
    Index.atIndex(atHeaders)

  implicit val eachHeaders: Each[Headers, NonEmptyList[String]] = new Each[Headers, NonEmptyList[String]] {
    override def each: Traversal[Headers, NonEmptyList[String]] =
      headersToMap composeTraversal Each.each
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
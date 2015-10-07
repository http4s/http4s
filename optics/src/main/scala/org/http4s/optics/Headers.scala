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




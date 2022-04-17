/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.core.h2

import cats._
import cats.data._
import cats.syntax.all._
import org.http4s._

/** HTTP/2 pseudo headers */
private[h2] object PseudoHeaders {
  // Request pseudo headers
  val METHOD = ":method"
  val SCHEME = ":scheme"
  val PATH = ":path"
  val AUTHORITY = ":authority"
  val requestPsedo: Set[String] = Set(
    METHOD,
    SCHEME,
    PATH,
    AUTHORITY,
  )

  import org.http4s.Request
  def requestToHeaders[F[_]](req: Request[F]): NonEmptyList[(String, String, Boolean)] = {
    val path = {
      val s = req.uri.path.renderString
      if (s.isEmpty) "/"
      else s
    }
    val l = NonEmptyList.of(
      (METHOD, req.method.toString, false),
      (SCHEME, req.uri.scheme.map(_.value).getOrElse("https"), false) ::
        (PATH, path, false) ::
        (AUTHORITY, req.uri.authority.map(_.toString).getOrElse(""), false) ::
        req.headers.headers.map(raw =>
          (
            raw.name.toString.toLowerCase(),
            raw.value,
            org.http4s.Headers.SensitiveHeaders.contains(raw.name),
          )
        ): _*
    )
    l
  }

  def headersToRequestNoBody(hI: NonEmptyList[(String, String)]): Option[Request[fs2.Pure]] = {
    def toRawHeader(k: String, v: String): Header.ToRaw =
      Header.ToRaw.rawToRaw(Header.Raw(org.typelevel.ci.CIString(k), v))

    def isLowerCase(str: String): Boolean =
      str.forall(c => c.toUpper === c)

    // TODO This can be a 1 pass operation. This is not...
    val headers: List[(String, String)] = hI.toList
    val pseudos = headers.takeWhile(_._1.startsWith(":"))
    val method = findWithNoDuplicates(pseudos)(_._1 === METHOD)
      .map(_._2)
      .flatMap(Method.fromString(_).toOption)
    val scheme =
      findWithNoDuplicates(pseudos)(_._1 === SCHEME).map(_._2).map(Uri.Scheme.unsafeFromString(_))
    val path = findWithNoDuplicates(pseudos)(_._1 === PATH).map(_._2)
    val authority = extractAuthority(pseudos)

    val noOtherPseudo = pseudos.forall(t => requestPsedo.contains(t._1))

    val teIsCorrectAndNoConnection = !headers.exists { case (k, v) =>
      (k === "te" && v =!= "trailers") ||
      k === "connection"
    }

    val reqHeaders = headers.dropWhile(_._1.startsWith(":"))

    def noOtherPseudosNorUpperCased = reqHeaders.forall { case (k, _) =>
      !k.startsWith(":") && isLowerCase(k)
    }

    for {
      _ <- Alternative[Option].guard(
        noOtherPseudo && teIsCorrectAndNoConnection && noOtherPseudosNorUpperCased
      )
      m <- method
      p <- path
      _ <- Alternative[Option].guard(p.nonEmpty) // Not Allowed to be empty in http/2

      u <- Uri.fromString(p).toOption
      s <- scheme // Required
    } yield {
      val h = Headers(reqHeaders.map(Function.tupled(toRawHeader)): _*)
      Request(m, u.copy(scheme = s.some, authority = authority), HttpVersion.`HTTP/2`, h)
    }
  }

  def extractAuthority(headers: List[(String, String)]): Option[Uri.Authority] =
    headers.collectFirstSome {
      case (PseudoHeaders.AUTHORITY, value) =>
        val index = value.indexOf(":")
        if (index > 0 && index < value.length) {
          Option(
            Uri.Authority(
              userInfo = None,
              host = Uri.RegName(value.take(index)),
              port = value.drop(index + 1).toInt.some,
            )
          )
        } else Option.empty
      case (_, _) => None
    }

  // Response pseudo header
  val STATUS = ":status"

  def responseToHeaders[F[_]](response: Response[F]): NonEmptyList[(String, String, Boolean)] =
    NonEmptyList(
      (STATUS, response.status.code.toString, false),
      response.headers.headers
        .map(raw =>
          (
            raw.name.toString.toLowerCase,
            raw.value,
            org.http4s.Headers.SensitiveHeaders.contains(raw.name),
          )
        ),
    )

  def headersToResponseNoBody(
      headers: NonEmptyList[(String, String)]
  ): Option[Response[fs2.Pure]] = {
    // TODO Duplicate Check
    val statusO =
      findWithNoDuplicates(headers.toList)(_._1 === PseudoHeaders.STATUS).map(_._2).flatMap {
        value =>
          Status.fromInt(value.toInt).toOption
      }
    val h = Headers(
      headers
        .filterNot(t => t._1 == PseudoHeaders.STATUS)
        .map(t => Header.Raw(org.typelevel.ci.CIString(t._1), t._2))
        .map(Header.ToRaw.rawToRaw): _*
    )
    statusO.map(s => Response(status = s, httpVersion = HttpVersion.`HTTP/2`, headers = h))
  }

  def findWithNoDuplicates[A](l: List[A])(bool: A => Boolean): Option[A] =
    l.foldLeft(Either.right[Unit, Option[A]](None)) {
      case (Left(e), _) => Left(e)
      case (Right(Some(a)), next) =>
        if (bool(next)) Left(())
        else Right(Some(a))
      case (Right(None), next) =>
        if (bool(next)) Right(Some(next))
        else Right(None)
    }.toOption
      .flatten

}

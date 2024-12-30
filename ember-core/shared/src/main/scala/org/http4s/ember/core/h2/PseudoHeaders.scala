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
import org.typelevel.ci.CIString

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
    // RFC 7540 §8.1.2.3 specifies :path includes path and query
    val path = {
      val p = req.uri.path.renderString
      val q = req.queryString
      val query = if (q.isEmpty) q else s"?$q"
      if (p.isEmpty) {
        if (req.method === Method.OPTIONS && req.uri.query.isEmpty) "*" else s"/$query"
      } else s"$p$query"
    }
    val l = NonEmptyList.of(
      (METHOD, req.method.toString, false),
      (SCHEME, req.uri.scheme.map(_.value).getOrElse("https"), false) ::
        (PATH, path, false) ::
        (AUTHORITY, req.uri.authority.map(_.toString).getOrElse(""), false) ::
        req.headers.headers
          .filterNot(p => connectionHeadersFields.contains(p.name))
          .map(raw =>
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

    val noOtherPseudo = pseudos
      .filterNot(t => requestPsedo.contains(t._1))
      .isEmpty

    val teIsCorrect = headers.find(_._1 === "te").headOption.fold(true)(_._2 === "trailers")
    val connectionIsAbsent = headers.find(_._1 === "connection").isEmpty

    val reqHeaders = headers.dropWhile(_._1.startsWith(":"))

    val noOtherPseudos = !reqHeaders.exists(_._1.startsWith(":"))
    val noUppercaseHeaders = reqHeaders.map(_._1).forall(s => s === s.toLowerCase)

    val h = Headers(
      reqHeaders
        .map(t => Header.Raw(org.typelevel.ci.CIString(t._1), t._2))
        .map(Header.ToRaw.rawToRaw): _*
    )
    for {
      _ <- Alternative[Option].guard(
        noOtherPseudo && teIsCorrect && connectionIsAbsent && noOtherPseudos && noUppercaseHeaders
      )
      m <- method
      p <- path
      _ <- Alternative[Option].guard(p.nonEmpty) // Not Allowed to be empty in http/2

      u <- Uri.fromString(p).toOption
      s <- scheme // Required
    } yield Request(m, u.copy(scheme = s.some, authority = authority), HttpVersion.`HTTP/2`, h)
  }

  def extractAuthority(headers: List[(String, String)]): Option[Uri.Authority] =
    headers.collectFirstSome {
      case (PseudoHeaders.AUTHORITY, value) =>
        Uri.fromString(value).toOption.flatMap(_.authority)
      case (_, _) => None
    }

  // Response pseudo header
  val STATUS = ":status"

  def responseToHeaders[F[_]](response: Response[F]): NonEmptyList[(String, String, Boolean)] =
    NonEmptyList(
      (STATUS, response.status.code.toString, false),
      response.headers.headers
        .filterNot(p => connectionHeadersFields.contains(p.name))
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

  // Connection Specific Headers should be removed when doing h2
  // This is because connection mechanisms are handled by h2
  // rather than request/response cycle.
  // https://httpwg.org/specs/rfc7540.html#rfc.section.8.1.2.2
  val connectionHeadersFields: Set[CIString] = Set(
    org.http4s.headers.`Transfer-Encoding`.headerInstance.name,
    org.http4s.headers.Connection.headerInstance.name,
    org.http4s.headers.Upgrade.headerInstance.name,
    org.http4s.headers.`Keep-Alive`.headerInstance.name,
    CIString("Proxy-Connection"),
  )
}

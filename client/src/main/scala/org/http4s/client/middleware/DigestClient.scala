/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.client.middleware

import java.util.Base64

import cats.data.NonEmptyList
import cats.effect.{Concurrent, Resource}
import cats.implicits.{none, _}
import org.http4s.Status.Unauthorized
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.{Authorization, `WWW-Authenticate`}
import org.http4s.middleware.authentication.DigestUtil
import org.http4s.parser.HttpHeaderParser
import org.log4s.getLogger

import scala.util.Random

/**
  * Digest Middleware for digest auth
  */
object DigestClient {
  private[this] val logger = getLogger

  def apply[F[_]](
      username: String,
      password: String
  )(client: Client[F])(implicit F: Concurrent[F]): Client[F] =
    apply[F](username, password, "auth")(client)

  def apply[F[_]](
      username: String,
      password: String,
      qop: String
  )(client: Client[F])(implicit F: Concurrent[F]): Client[F] = {
    def digestAuthHeader(req: Request[F], challenge: Challenge): Header = {
      val realm = challenge.realm
      val method = req.method.name
      val uri = req.uri.path.toString
      val nc = "00000001"
      val cnonce = {
        val bytes = new Array[Byte](16)
        Random.nextBytes(bytes)
        Base64.getEncoder.encodeToString(bytes)
      }
      val nonce = challenge.params("nonce")

      val response =
        DigestUtil.computeResponse(method, username, realm, password, uri, nonce, nc, cnonce, qop)
      val params: NonEmptyList[(String, String)] = NonEmptyList.of(
        "username" -> username,
        "realm" -> realm,
        "nonce" -> nonce,
        "uri" -> uri,
        "qop" -> qop,
        "nc" -> nc,
        "cnonce" -> cnonce,
        "response" -> response,
        "method" -> method
      )
      Authorization(Credentials.AuthParams(AuthScheme.Digest, params))
    }

    def prepareLoop(req: Request[F]): F[Resource[F, Response[F]]] =
      F.continual(client.run(req).allocated) {
        case Right((resp, dispose)) if !hasDigestAuth(req.headers) && resp.status == Unauthorized =>
          extractChallenge(resp.headers) match {
            case Some(challenge) =>
              dispose >> prepareLoop(req.putHeaders(digestAuthHeader(req, challenge)))
            case None =>
              Resource.make(resp.pure[F])(_ => dispose).pure[F]
          }
        case Right((resp, dispose)) => Resource.make(resp.pure[F])(_ => dispose).pure[F]
        case Left(e) => F.raiseError(e)
      }

    Client(req => Resource.suspend(prepareLoop(req)))
  }

  private def extractChallenge(headers: Headers) =
    for {
      challenge <- headers.get(`WWW-Authenticate`).map(_.value)
      res <-
        HttpHeaderParser
          .WWW_AUTHENTICATE(challenge)
          .fold(
            _ => {
              logger.info(s"Couldn't parse challenge: $challenge")
              none
            },
            v => Some(v.values.head))
    } yield res

  private def hasDigestAuth(headers: Headers): Boolean =
    headers
      .get(Authorization)
      .exists(_.credentials.authScheme == AuthScheme.Digest)

}

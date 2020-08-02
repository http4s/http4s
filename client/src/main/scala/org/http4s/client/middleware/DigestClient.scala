/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.client.middleware

import cats.data.NonEmptyList
import cats.effect.{Concurrent, Resource, Timer}
import cats.implicits._
import org.http4s.Status.Unauthorized
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.digest.NonceCounter
import org.http4s.headers.{Authorization, `WWW-Authenticate`}
import org.http4s.internal.DigestUtil
import org.http4s.parser.HttpHeaderParser
import org.log4s.getLogger

/**
  * Digest Middleware for digest auth from RFC 7616
  */
object DigestClient {
  private[this] val logger = getLogger

  def apply[F[_]](
      username: String,
      password: String
  )(client: Client[F])(implicit F: Concurrent[F], T: Timer[F]): Client[F] =
    apply[F](username, password, "auth")(client)

  def apply[F[_]](
      username: String,
      password: String,
      qop: String
  )(client: Client[F])(implicit F: Concurrent[F], T: Timer[F]): Client[F] =
    apply[F](
      username = username,
      password = password,
      qop = qop,
      nonceCounter = NonceCounter.defaultNonceCounter[F].pure[F]
    )(client)

  def apply[F[_]](
      username: String,
      password: String,
      qop: String,
      nonceCounter: F[NonceCounter[F]]
  )(client: Client[F])(implicit F: Concurrent[F]): Client[F] = {
    def digestAuthHeader(req: Request[F], challenge: Challenge): F[Header] =
      for {
        nonce <- F.fromOption(
          challenge.params.get("nonce"),
          new IllegalStateException(s"No nonce param in challenge, $challenge"))
        nc <- nonceCounter
        (cnonce, ncLong) <- nc.next(nonce)
      } yield {
        val realm = challenge.realm
        val method = req.method.name
        val uri = req.uri.path

        val nc = ncLong.formatted("%08X")
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
              dispose >> digestAuthHeader(req, challenge) >>= (h => prepareLoop(req.putHeaders(h)))
            case None =>
              Resource.make(resp.pure[F])(_ => dispose).pure[F]
          }
        case Right((resp, dispose)) => Resource.make(resp.pure[F])(_ => dispose).pure[F]
        case Left(e) => F.raiseError(e)
      }

    Client(req => Resource.suspend(prepareLoop(req)))
  }

  private def extractChallenge(headers: Headers): Option[Challenge] =
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

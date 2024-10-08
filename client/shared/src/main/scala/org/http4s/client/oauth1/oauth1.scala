/*
 * Copyright 2014 http4s.org
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

package org.http4s
package client

import cats.Monad
import cats.MonadThrow
import cats.Show
import cats.data.NonEmptyList
import cats.effect.SyncIO
import cats.instances.order._
import cats.syntax.all._
import org.http4s.client.oauth1.ProtocolParameter.Callback
import org.http4s.client.oauth1.ProtocolParameter.Custom
import org.http4s.client.oauth1.ProtocolParameter.Nonce
import org.http4s.client.oauth1.ProtocolParameter.Realm
import org.http4s.client.oauth1.ProtocolParameter.SignatureMethod
import org.http4s.client.oauth1.ProtocolParameter.Timestamp
import org.http4s.client.oauth1.ProtocolParameter.Verifier
import org.http4s.client.oauth1.ProtocolParameter.Version
import org.http4s.headers.Authorization

import scala.annotation.nowarn
import scala.collection.immutable
import scala.collection.mutable.ListBuffer

/** Basic OAuth1 message signing support
  *
  * This feature is not considered stable.
  */
package object oauth1 {

  private val OutOfBand = "oob"

  /** Sign the request with an OAuth Authorization header
    *
    * __WARNING:__ POST requests with application/x-www-form-urlencoded bodies
    *            will be entirely buffered due to signing requirements.
    */
  @deprecated(
    "Preserved for binary compatibility - use the other `signRequest` function which passes a signature method",
    "0.22.3",
  )
  def signRequest[F[_]](
      req: Request[F],
      consumer: Consumer,
      callback: Option[Uri],
      verifier: Option[String],
      token: Option[Token],
  )(implicit F: MonadThrow[F], W: EntityDecoder[F, UrlForm]): F[Request[F]] =
    getUserParams(req).flatMap { case (req, params) =>
      genAuthHeader(req.method, req.uri, params, consumer, callback, verifier, token, HmacSha1)
        .map(auth => req.putHeaders(auth))
    }

  def signRequest[F[_]](
      req: Request[F],
      consumer: ProtocolParameter.Consumer,
      token: Option[ProtocolParameter.Token],
      realm: Option[Realm],
      signatureMethod: SignatureMethod = ProtocolParameter.SignatureMethod(),
      timestampGenerator: F[Timestamp],
      version: ProtocolParameter.Version = Version(),
      nonceGenerator: F[Nonce],
      callback: Option[Callback] = None,
      verifier: Option[Verifier] = None,
  )(implicit F: MonadThrow[F], W: EntityDecoder[F, UrlForm]): F[Request[F]] =
    for {
      reqParams <- getUserParams(req)
      // Working around lack of withFilter
      (req, params) = reqParams
      auth <- genAuthHeader(
        req.method,
        req.uri,
        consumer,
        token,
        realm,
        signatureMethod,
        timestampGenerator,
        version,
        nonceGenerator,
        callback,
        verifier,
        params.map { case (k, v) =>
          Custom(k, v)
        },
      )
    } yield req.putHeaders(auth)

  def takeSigHeaders[F[_]: Monad](
      consumer: ProtocolParameter.Consumer,
      token: Option[ProtocolParameter.Token],
      signatureMethod: SignatureMethod,
      timestampGenerator: F[Timestamp],
      version: ProtocolParameter.Version,
      nonceGenerator: F[Nonce],
      callback: Option[Callback],
      verifier: Option[Verifier],
  ): F[immutable.Seq[ProtocolParameter]] =
    for {
      timestamp <- timestampGenerator
      nonce <- nonceGenerator
    } yield {
      val headers =
        List(consumer, signatureMethod, timestamp, nonce, version)
      headers ++ List(token, callback, verifier).flatten
    }

  private[oauth1] def genAuthHeader[F[_]: MonadThrow](
      method: Method,
      uri: Uri,
      consumer: ProtocolParameter.Consumer,
      token: Option[ProtocolParameter.Token],
      realm: Option[Realm],
      signatureMethod: SignatureMethod,
      timestampGenerator: F[Timestamp],
      version: ProtocolParameter.Version,
      nonceGenerator: F[Nonce],
      callback: Option[Callback],
      verifier: Option[Verifier],
      queryParams: Seq[ProtocolParameter],
  ): F[Authorization] =
    takeSigHeaders(
      consumer,
      token,
      signatureMethod,
      timestampGenerator,
      version,
      nonceGenerator,
      callback,
      verifier,
    ).flatMap { headers =>
      val baseStr = mkBaseString(
        method,
        uri,
        (headers ++ queryParams).sorted.iterator.map(Show[ProtocolParameter].show).mkString("&"),
      )
      val alg = SignatureAlgorithm.unsafeFromMethod(signatureMethod)
      makeSHASig(baseStr, consumer.secret, token.map(_.secret), alg).map { sig =>
        val creds = Credentials.AuthParams(
          AuthScheme.OAuth,
          NonEmptyList(
            "oauth_signature" -> encode(sig),
            realm.fold(headers.map(_.toTuple))(_.toTuple +: headers.map(_.toTuple)).toList,
          ),
        )

        Authorization(creds)
      }
    }

  // Generate an authorization header with the provided user params and OAuth requirements.
  // Warning: Fixed to HMAC-SHA1
  @deprecated("Preserved for binary compatibility", "0.22.3")
  private[oauth1] def genAuthHeader(
      method: Method,
      uri: Uri,
      userParams: immutable.Seq[(String, String)],
      consumer: Consumer,
      callback: Option[Uri],
      verifier: Option[String],
      token: Option[Token],
  ): Authorization =
    genAuthHeader[SyncIO](method, uri, userParams, consumer, callback, verifier, token, HmacSha1)
      .unsafeRunSync()

  private[oauth1] def genAuthHeader[F[_]: MonadThrow](
      method: Method,
      uri: Uri,
      userParams: immutable.Seq[(String, String)],
      consumer: Consumer,
      callback: Option[Uri],
      verifier: Option[String],
      token: Option[Token],
      algorithm: SignatureAlgorithm,
  ): F[Authorization] = {
    val params = {
      val params = new ListBuffer[(String, String)]
      params += "oauth_consumer_key" -> encode(consumer.key)
      params += "oauth_signature_method" -> algorithm.name
      params += "oauth_timestamp" -> (System.currentTimeMillis / 1000).toString
      params += "oauth_nonce" -> System.nanoTime.toString
      params += "oauth_version" -> "1.0"
      params += "oauth_callback" -> callback.map(c => encode(c.renderString)).getOrElse(OutOfBand)
      token.foreach { t =>
        params += "oauth_token" -> encode(t.value)
      }
      verifier.foreach { v =>
        params += "oauth_verifier" -> encode(v)
      }
      params.result()
    }

    val baseString = genBaseString(
      method,
      uri,
      params ++ userParams.map { case (k, v) =>
        (encode(k), encode(v))
      },
    )
    makeSHASig(baseString, consumer.secret, token.map(_.secret), algorithm).map { sig =>
      val creds =
        Credentials.AuthParams(
          AuthScheme.OAuth,
          NonEmptyList("oauth_signature" -> encode(sig), params),
        )

      Authorization(creds)
    }
  }

  // baseString must already be encoded, consumer and token must not be
  // Warning: Defaults to HMAC-SHA1
  @deprecated("Preserved for binary compatibility", "0.22.3")
  private[oauth1] def makeSHASig(
      baseString: String,
      consumer: Consumer,
      token: Option[Token],
  ): String =
    makeSHASig(baseString, consumer.secret, token.map(_.secret))

  // Warning: Defaults to HMAC-SHA1
  @deprecated("Preserved for binary compatibility", "0.22.3")
  private[oauth1] def makeSHASig(
      baseString: String,
      consumerSecret: String,
      tokenSecret: Option[String],
  ): String =
    makeSHASig[SyncIO](baseString, consumerSecret, tokenSecret, HmacSha1).unsafeRunSync()

  private[oauth1] def makeSHASig[F[_]: MonadThrow](
      baseString: String,
      consumerSecret: String,
      tokenSecret: Option[String],
      algorithm: SignatureAlgorithm,
  ): F[String] = {

    val key = encode(consumerSecret) + "&" + tokenSecret.map(t => encode(t)).getOrElse("")
    algorithm.generate[F](baseString, key): @nowarn("cat=deprecation")
  }

  // Needs to have all params already encoded
  private[oauth1] def genBaseString(
      method: Method,
      uri: Uri,
      params: immutable.Seq[(String, String)],
  ): String = {
    val paramsStr = params.map { case (k, v) => k + "=" + v }.sorted.mkString("&")
    mkBaseString(method, uri, paramsStr)
  }

  def mkBaseString(method: Method, uri: Uri, paramsStr: String): String =
    immutable
      .Seq(
        method.name,
        encode(uri.copy(query = Query.empty, fragment = None).renderString),
        encode(paramsStr),
      )
      .mkString("&")

  private[oauth1] def encode(str: String): String =
    Uri.encode(str, spaceIsPlus = false, toSkip = Uri.Unreserved)

  private[oauth1] def getUserParams[F[_]](req: Request[F])(implicit
      F: MonadThrow[F],
      W: EntityDecoder[F, UrlForm],
  ): F[(Request[F], Vector[(String, String)])] = {
    val qparams = req.uri.query.pairs.map { case (k, ov) => (k, ov.getOrElse("")) }

    req.contentType match {
      case Some(t)
          if (req.method == Method.POST || req.method == Method.PUT) &&
            t.mediaType == MediaType.application.`x-www-form-urlencoded` =>
        req.as[UrlForm].map { urlform =>
          val bodyparams = urlform.values.toSeq
            .flatMap { case (k, vs) => if (vs.isEmpty) Seq(k -> "") else vs.toList.map((k, _)) }

          implicit val charset: Charset = req.charset.getOrElse(Charset.`UTF-8`)
          req.withEntity(urlform) -> (qparams ++ bodyparams)
        }

      case _ => F.pure(req -> qparams)
    }
  }

}

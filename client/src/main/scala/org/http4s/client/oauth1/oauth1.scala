/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client

import cats.{Monad, MonadError, Show}
import cats.data.NonEmptyList
import cats.implicits._
import java.nio.charset.StandardCharsets

import javax.crypto
import org.http4s.client.oauth1.ProtocolParameter.{
  Callback,
  Custom,
  Nonce,
  Realm,
  SignatureMethod,
  Timestamp,
  Verifier,
  Version
}
import org.http4s.headers.Authorization
import org.http4s.syntax.string._
import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

/** Basic OAuth1 message signing support
  *
  * This feature is not considered stable.
  */
package object oauth1 {
  private val SHA1 = "HmacSHA1"
  private def UTF_8 = StandardCharsets.UTF_8
  private val OutOfBand = "oob"

  /** Sign the request with an OAuth Authorization header
    *
    * __WARNING:__ POST requests with application/x-www-form-urlencoded bodies
    *            will be entirely buffered due to signing requirements. */
  def signRequest[F[_]](
      req: Request[F],
      consumer: Consumer,
      callback: Option[Uri],
      verifier: Option[String],
      token: Option[Token])(
      implicit F: MonadError[F, Throwable],
      W: EntityDecoder[F, UrlForm]): F[Request[F]] =
    getUserParams(req).map {
      case (req, params) =>
        val auth = genAuthHeader(req.method, req.uri, params, consumer, callback, verifier, token)
        req.putHeaders(auth)
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
      verifier: Option[Verifier] = None
  )(implicit F: MonadError[F, Throwable], W: EntityDecoder[F, UrlForm]): F[Request[F]] =
    for {
      (req, params) <- getUserParams(req)
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
        params.map {
          case (k, v) => Custom(k, v)
        }
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
      verifier: Option[Verifier]
  ): F[immutable.Seq[ProtocolParameter]] =
    for {
      timestamp <- timestampGenerator
      nonce <- nonceGenerator
    } yield {
      val headers =
        List(consumer, signatureMethod, timestamp, nonce, version)
      headers ++ List(token, callback, verifier).flatten
    }

  private[oauth1] def genAuthHeader[F[_]: Monad](
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
      queryParams: Seq[ProtocolParameter]): F[Authorization] =
    takeSigHeaders(
      consumer,
      token,
      signatureMethod,
      timestampGenerator,
      version,
      nonceGenerator,
      callback,
      verifier
    ).map { headers =>
      val baseStr = mkBaseString(
        method,
        uri,
        (headers ++ queryParams).sorted.map(Show[ProtocolParameter].show).mkString("&"))
      val sig = makeSHASig(baseStr, consumer.secret, token.map(_.secret))
      val creds = Credentials.AuthParams(
        "OAuth".ci,
        NonEmptyList(
          "oauth_signature" -> encode(sig),
          realm.fold(headers.map(_.toTuple))(_.toTuple +: headers.map(_.toTuple)) toList)
      )

      Authorization(creds)
    }

  // Generate an authorization header with the provided user params and OAuth requirements.
  private[oauth1] def genAuthHeader(
      method: Method,
      uri: Uri,
      userParams: immutable.Seq[(String, String)],
      consumer: Consumer,
      callback: Option[Uri],
      verifier: Option[String],
      token: Option[Token]): Authorization = {
    val params = {
      val params = new ListBuffer[(String, String)]
      params += "oauth_consumer_key" -> encode(consumer.key)
      params += "oauth_signature_method" -> "HMAC-SHA1"
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

    val baseString = genBaseString(method, uri, params ++ userParams.map {
      case (k, v) => (encode(k), encode(v))
    })
    val sig = makeSHASig(baseString, consumer, token)
    val creds =
      Credentials.AuthParams("OAuth".ci, NonEmptyList("oauth_signature" -> encode(sig), params))

    Authorization(creds)
  }

  // baseString must already be encoded, consumer and token must not be
  private[oauth1] def makeSHASig(
      baseString: String,
      consumer: Consumer,
      token: Option[Token]): String =
    makeSHASig(baseString, consumer.secret, token.map(_.secret))

  private[oauth1] def makeSHASig(
      baseString: String,
      consumerSecret: String,
      tokenSecret: Option[String]): String = {
    val sha1 = crypto.Mac.getInstance(SHA1)
    val key = encode(consumerSecret) + "&" + tokenSecret.map(t => encode(t)).getOrElse("")
    sha1.init(new crypto.spec.SecretKeySpec(bytes(key), SHA1))

    val sigBytes = sha1.doFinal(bytes(baseString))
    java.util.Base64.getEncoder.encodeToString(sigBytes)
  }

  // Needs to have all params already encoded
  private[oauth1] def genBaseString(
      method: Method,
      uri: Uri,
      params: immutable.Seq[(String, String)]): String = {
    val paramsStr = params.map { case (k, v) => k + "=" + v }.sorted.mkString("&")
    mkBaseString(method, uri, paramsStr)
  }

  def mkBaseString(method: Method, uri: Uri, paramsStr: String) =
    immutable
      .Seq(
        method.name,
        encode(uri.copy(query = Query.empty, fragment = None).renderString),
        encode(paramsStr))
      .mkString("&")

  private[oauth1] def encode(str: String): String =
    Uri.encode(str, spaceIsPlus = false, toSkip = Uri.Unreserved)

  private[oauth1] def getUserParams[F[_]](req: Request[F])(
      implicit F: MonadError[F, Throwable],
      W: EntityDecoder[F, UrlForm]): F[(Request[F], immutable.Seq[(String, String)])] = {
    val qparams = req.uri.query.pairs.map { case (k, ov) => (k, ov.getOrElse("")) }

    req.contentType match {
      case Some(t)
          if (req.method == Method.POST || req.method == Method.PUT) &&
            t.mediaType == MediaType.application.`x-www-form-urlencoded` =>
        req.as[UrlForm].map { urlform =>
          val bodyparams = urlform.values.toSeq
            .flatMap { case (k, vs) => if (vs.isEmpty) Seq(k -> "") else vs.toList.map((k, _)) }

          implicit val charset = req.charset.getOrElse(Charset.`UTF-8`)
          req.withEntity(urlform) -> (qparams ++ bodyparams)
        }

      case _ => F.pure(req -> qparams)
    }
  }

  private def bytes(str: String) = str.getBytes(UTF_8)
}

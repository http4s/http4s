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

import cats.effect.Async
import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all._
import fs2.Stream
import fs2.io.readInputStream
import fs2.io.writeOutputStream
import org.http4s.internal.BackendBuilder
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.typelevel.ci.CIString

import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import scala.concurrent.ExecutionContext
import scala.concurrent.blocking
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

/** Builder for a [[Client]] backed by on `java.net.HttpUrlConnection`.
  *
  * The java.net client adds no dependencies beyond `http4s-client`.
  * This client is generally not production grade, but convenient for
  * exploration in a REPL.
  *
  * All I/O operations in this client are blocking.
  *
  * @param blockingExecutionContext An `ExecutionContext` on which
  * blocking operations will be performed.
  * @define WHYNOSHUTDOWN Creation of the client allocates no
  * resources, and any resources allocated while using this client
  * are reclaimed by the JVM at its own leisure.
  */
sealed abstract class JavaNetClientBuilder[F[_]] private (
    val connectTimeout: Duration,
    val readTimeout: Duration,
    val proxy: Option[Proxy],
    val hostnameVerifier: Option[HostnameVerifier],
    val sslSocketFactory: Option[SSLSocketFactory],
    val blocker: Blocker
)(implicit protected val F: Async[F], cs: ContextShift[F])
    extends BackendBuilder[F, Client[F]] {
  private def copy(
      connectTimeout: Duration = connectTimeout,
      readTimeout: Duration = readTimeout,
      proxy: Option[Proxy] = proxy,
      hostnameVerifier: Option[HostnameVerifier] = hostnameVerifier,
      sslSocketFactory: Option[SSLSocketFactory] = sslSocketFactory,
      blocker: Blocker = blocker
  ): JavaNetClientBuilder[F] =
    new JavaNetClientBuilder[F](
      connectTimeout = connectTimeout,
      readTimeout = readTimeout,
      proxy = proxy,
      hostnameVerifier = hostnameVerifier,
      sslSocketFactory = sslSocketFactory,
      blocker = blocker
    ) {}

  def withConnectTimeout(connectTimeout: Duration): JavaNetClientBuilder[F] =
    copy(connectTimeout = connectTimeout)

  def withReadTimeout(readTimeout: Duration): JavaNetClientBuilder[F] =
    copy(readTimeout = readTimeout)

  def withProxyOption(proxy: Option[Proxy]): JavaNetClientBuilder[F] =
    copy(proxy = proxy)
  def withProxy(proxy: Proxy): JavaNetClientBuilder[F] =
    withProxyOption(Some(proxy))
  def withoutProxy: JavaNetClientBuilder[F] =
    withProxyOption(None)

  def withHostnameVerifierOption(
      hostnameVerifier: Option[HostnameVerifier]): JavaNetClientBuilder[F] =
    copy(hostnameVerifier = hostnameVerifier)
  def withHostnameVerifier(hostnameVerifier: HostnameVerifier): JavaNetClientBuilder[F] =
    withHostnameVerifierOption(Some(hostnameVerifier))
  def withoutHostnameVerifier: JavaNetClientBuilder[F] =
    withHostnameVerifierOption(None)

  def withSslSocketFactoryOption(
      sslSocketFactory: Option[SSLSocketFactory]): JavaNetClientBuilder[F] =
    copy(sslSocketFactory = sslSocketFactory)
  def withSslSocketFactory(sslSocketFactory: SSLSocketFactory): JavaNetClientBuilder[F] =
    withSslSocketFactoryOption(Some(sslSocketFactory))
  def withoutSslSocketFactory: JavaNetClientBuilder[F] =
    withSslSocketFactoryOption(None)

  def withBlocker(blocker: Blocker): JavaNetClientBuilder[F] =
    copy(blocker = blocker)

  @deprecated("Use withBlocker instead", "0.21.0")
  def withBlockingExecutionContext(
      blockingExecutionContext: ExecutionContext): JavaNetClientBuilder[F] =
    copy(blocker = Blocker.liftExecutionContext(blockingExecutionContext))

  /** Creates a [[Client]].
    *
    * The shutdown of this client is a no-op. $WHYNOSHUTDOWN
    */
  def create: Client[F] =
    Client { (req: Request[F]) =>
      def respond(conn: HttpURLConnection): F[Response[F]] =
        for {
          _ <- configureSsl(conn)
          _ <- F.delay(conn.setConnectTimeout(timeoutMillis(connectTimeout)))
          _ <- F.delay(conn.setReadTimeout(timeoutMillis(readTimeout)))
          _ <- F.delay(conn.setRequestMethod(req.method.renderString))
          _ <- F.delay(req.headers.foreach { case Header.Raw(name, value) =>
            conn.setRequestProperty(name.toString, value)
          })
          _ <- F.delay(conn.setInstanceFollowRedirects(false))
          _ <- F.delay(conn.setDoInput(true))
          resp <- blocker.blockOn(blocking(fetchResponse(req, conn)))
        } yield resp

      for {
        url <- Resource.eval(F.delay(new URL(req.uri.toString)))
        conn <- Resource.make(openConnection(url)) { conn =>
          F.delay(conn.getInputStream().close()).recoverWith { case _: IOException =>
            F.delay(Option(conn.getErrorStream()).foreach(_.close()))
          }
        }
        resp <- Resource.eval(respond(conn))
      } yield resp
    }

  def resource: Resource[F, Client[F]] =
    Resource.make(F.delay(create))(_ => F.unit)

  private def fetchResponse(req: Request[F], conn: HttpURLConnection) =
    for {
      _ <- writeBody(req, conn)
      code <- F.delay(conn.getResponseCode)
      status <- F.fromEither(Status.fromInt(code))
      headers <- F.delay(
        Headers(
          conn.getHeaderFields.asScala
            .filter(_._1 != null)
            .flatMap { case (k, vs) => vs.asScala.map(Header.Raw(CIString(k), _)) }
            .toList
        ))
    } yield Response(status = status, headers = headers, body = readBody(conn))

  private def timeoutMillis(d: Duration): Int =
    d match {
      case d: FiniteDuration if d > Duration.Zero => d.toMillis.max(0).min(Int.MaxValue).toInt
      case _ => 0
    }

  private def openConnection(url: URL)(implicit F: Sync[F]) =
    proxy match {
      case Some(p) =>
        F.delay(url.openConnection(p).asInstanceOf[HttpURLConnection])
      case None =>
        F.delay(url.openConnection().asInstanceOf[HttpURLConnection])
    }

  private def writeBody(req: Request[F], conn: HttpURLConnection): F[Unit] =
    if (req.isChunked)
      F.delay(conn.setDoOutput(true)) *>
        F.delay(conn.setChunkedStreamingMode(4096)) *>
        req.body
          .through(writeOutputStream(F.delay(conn.getOutputStream), blocker, false))
          .compile
          .drain
    else
      req.contentLength match {
        case Some(len) if len >= 0L =>
          F.delay(conn.setDoOutput(true)) *>
            F.delay(conn.setFixedLengthStreamingMode(len)) *>
            req.body
              .through(writeOutputStream(F.delay(conn.getOutputStream), blocker, false))
              .compile
              .drain
        case _ =>
          F.delay(conn.setDoOutput(false))
      }

  private def readBody(conn: HttpURLConnection): Stream[F, Byte] = {
    def inputStream =
      F.delay(Option(conn.getInputStream)).recoverWith {
        case _: IOException if conn.getResponseCode > 0 =>
          F.delay(Option(conn.getErrorStream))
      }
    Stream.eval(inputStream).flatMap {
      case Some(in) => readInputStream(F.pure(in), 4096, blocker, false)
      case None => Stream.empty
    }
  }

  private def configureSsl(conn: HttpURLConnection) =
    conn match {
      case connSsl: HttpsURLConnection =>
        for {
          _ <- hostnameVerifier.fold(F.unit)(hv => F.delay(connSsl.setHostnameVerifier(hv)))
          _ <- sslSocketFactory.fold(F.unit)(sslf => F.delay(connSsl.setSSLSocketFactory(sslf)))
        } yield ()
      case _ => F.unit
    }
}

/** Builder for a [[Client]] backed by on `java.net.HttpUrlConnection`. */
object JavaNetClientBuilder {

  /** @param blockingExecutionContext An `ExecutionContext` on which
    * blocking operations will be performed.
    */
  def apply[F[_]: Async: ContextShift](blocker: Blocker): JavaNetClientBuilder[F] =
    new JavaNetClientBuilder[F](
      connectTimeout = defaults.ConnectTimeout,
      readTimeout = defaults.RequestTimeout,
      proxy = None,
      hostnameVerifier = None,
      sslSocketFactory = None,
      blocker = blocker
    ) {}
}

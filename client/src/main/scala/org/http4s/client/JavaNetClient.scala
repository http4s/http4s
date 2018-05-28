package org.http4s
package client

import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits._
import fs2.io.{readInputStream, writeOutputStream}
import java.net.{HttpURLConnection, Proxy, URL}
import javax.net.ssl.{HostnameVerifier, HttpsURLConnection, SSLSocketFactory}
import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}

sealed abstract class JavaNetClient private (
  val connectTimeout: Duration,
  val readTimeout: Duration,
  val proxy: Option[Proxy],
  val hostnameVerifier: Option[HostnameVerifier],
  val sslSocketFactory: Option[SSLSocketFactory]
) {
  private def copy(
    connectTimeout: Duration = connectTimeout,
    readTimeout: Duration = readTimeout,
    proxy: Option[Proxy] = proxy,
    hostnameVerifier: Option[HostnameVerifier] = hostnameVerifier,
    sslSocketFactory: Option[SSLSocketFactory] = sslSocketFactory,
  ): JavaNetClient = new JavaNetClient(
    connectTimeout = connectTimeout,
    readTimeout = readTimeout,
    proxy = proxy,
    hostnameVerifier = hostnameVerifier,
    sslSocketFactory = sslSocketFactory
  ) {}

  def withConnectTimeout(connectTimeout: Duration): JavaNetClient =
    copy(connectTimeout = connectTimeout)

  def withReadTimeout(readTimeout: Duration): JavaNetClient =
    copy(readTimeout = readTimeout)

  def withProxyOption(proxy: Option[Proxy]): JavaNetClient =
    copy(proxy = proxy)
  def withProxy(proxy: Proxy): JavaNetClient =
    withProxyOption(Some(proxy))
  def withoutProxy: JavaNetClient =
    withProxyOption(None)

  def withHostnameVerifierOption(hostnameVerifier: Option[HostnameVerifier]): JavaNetClient =
    copy(hostnameVerifier = hostnameVerifier)
  def withHostnameVerifier(hostnameVerifier: HostnameVerifier): JavaNetClient =
    withHostnameVerifierOption(Some(hostnameVerifier))
  def withoutHostnameVerifier: JavaNetClient =
    withHostnameVerifierOption(None)

  def withSslSocketFactoryOption(sslSocketFactory: Option[SSLSocketFactory]): JavaNetClient =
    copy(sslSocketFactory = sslSocketFactory)
  def withSslSocketFactory(sslSocketFactory: SSLSocketFactory): JavaNetClient =
    withSslSocketFactoryOption(Some(sslSocketFactory))
  def withoutSslSocketFactory: JavaNetClient =
    withSslSocketFactoryOption(None)

  def apply[F[_]](implicit F: Sync[F]): Client[F] = Client(
    open = Kleisli { req =>
      for {
        url <- F.delay(new URL(req.uri.toString))
        conn <- openConnection(url)
        _ <- if (req.uri.scheme === Some(Uri.Scheme.https)) configureSsl(conn) else F.unit
        _ <- F.delay(conn.setConnectTimeout(timeoutMillis(connectTimeout)))
        _ <- F.delay(conn.setReadTimeout(timeoutMillis(readTimeout)))
        _ <- F.delay(conn.setRequestMethod(req.method.renderString))
        _ <- F.delay(req.headers.foreach {
          case Header(name, value) => conn.setRequestProperty(name.value, value)
        })
        _ <- F.delay(conn.setInstanceFollowRedirects(false))
        _ <- F.delay(conn.setDoInput(true))
        _ <- writeBody(conn, req)
        status <- F.fromEither(Status.fromInt(conn.getResponseCode))
        headers <- F.delay(Headers(
          conn.getHeaderFields.asScala
            .filter(_._1 != null)
            .flatMap { case (k, vs) => vs.asScala.map(Header(k, _)) }
            .toList
        ))
        body = readInputStream(F.delay(conn.getInputStream), 4096)
      } yield DisposableResponse(
        Response(status = status, headers = headers, body = body),
        F.delay(conn.getInputStream.close())
      )
    },
    shutdown = F.unit
  )

  private def timeoutMillis(d: Duration): Int = d match {
    case d: FiniteDuration if d > Duration.Zero => (d.toMillis max 0 min Int.MaxValue).toInt
    case _ => 0
  }

  private def openConnection[F[_]](url: URL)(implicit F: Sync[F]): F[HttpURLConnection] =
    proxy match {
      case Some(p) =>
        F.delay(url.openConnection(p).asInstanceOf[HttpURLConnection])
      case None =>
        F.delay(url.openConnection().asInstanceOf[HttpURLConnection])
    }

  private def writeBody[F[_]](conn: HttpURLConnection, req: Request[F])(implicit F: Sync[F]): F[Unit] =
    if (req.isChunked) {
      F.delay(conn.setDoOutput(true)) *>
      F.delay(conn.setChunkedStreamingMode(4096)) *>
      req.body.to(writeOutputStream(F.delay(conn.getOutputStream), false)).compile.drain
    } else req.contentLength match {
      case Some(len) if len >= 0L =>
        F.delay(conn.setDoOutput(true)) *>
        F.delay(conn.setFixedLengthStreamingMode(len)) *>
        req.body.to(writeOutputStream(F.delay(conn.getOutputStream), false)).compile.drain
      case _ =>
        F.delay(conn.setDoOutput(false))
    }

  private def configureSsl[F[_]](conn: HttpURLConnection)(implicit F: Sync[F]): F[Unit] =
    for {
      connSsl <- F.delay(conn.asInstanceOf[HttpsURLConnection])
      _ <- hostnameVerifier.fold(F.unit)(hv => F.delay(connSsl.setHostnameVerifier(hv)))
      _ <- sslSocketFactory.fold(F.unit)(sslf => F.delay(connSsl.setSSLSocketFactory(sslf)))
    } yield ()
}

object JavaNetClient extends JavaNetClient(
  connectTimeout = Duration.Inf,
  readTimeout = Duration.Inf,
  proxy = None,
  hostnameVerifier = None,
  sslSocketFactory = None
)


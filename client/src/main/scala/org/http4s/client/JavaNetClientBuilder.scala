package org.http4s
package client

import cats.data.Kleisli
import cats.effect.{Async, ContextShift, Resource, Sync}
import cats.implicits._
import fs2.Stream
import fs2.io.{readInputStream, writeOutputStream}
import java.net.{HttpURLConnection, Proxy, URL}
import javax.net.ssl.{HostnameVerifier, HttpsURLConnection, SSLSocketFactory}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, blocking}
import scala.concurrent.duration.{Duration, FiniteDuration}

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
  *
  * @define WHYNOSHUTDOWN Creation of the client allocates no
  * resources, and any resources allocated while using this client
  * are reclaimed by the JVM at its own leisure.
  */
sealed abstract class JavaNetClientBuilder private (
    val connectTimeout: Duration,
    val readTimeout: Duration,
    val proxy: Option[Proxy],
    val hostnameVerifier: Option[HostnameVerifier],
    val sslSocketFactory: Option[SSLSocketFactory],
    val blockingExecutionContext: ExecutionContext
) {
  private def copy(
      connectTimeout: Duration = connectTimeout,
      readTimeout: Duration = readTimeout,
      proxy: Option[Proxy] = proxy,
      hostnameVerifier: Option[HostnameVerifier] = hostnameVerifier,
      sslSocketFactory: Option[SSLSocketFactory] = sslSocketFactory,
      blockingExecutionContext: ExecutionContext = blockingExecutionContext
  ): JavaNetClientBuilder =
    new JavaNetClientBuilder(
      connectTimeout = connectTimeout,
      readTimeout = readTimeout,
      proxy = proxy,
      hostnameVerifier = hostnameVerifier,
      sslSocketFactory = sslSocketFactory,
      blockingExecutionContext = blockingExecutionContext
    ) {}

  def withConnectTimeout(connectTimeout: Duration): JavaNetClientBuilder =
    copy(connectTimeout = connectTimeout)

  def withReadTimeout(readTimeout: Duration): JavaNetClientBuilder =
    copy(readTimeout = readTimeout)

  def withProxyOption(proxy: Option[Proxy]): JavaNetClientBuilder =
    copy(proxy = proxy)
  def withProxy(proxy: Proxy): JavaNetClientBuilder =
    withProxyOption(Some(proxy))
  def withoutProxy: JavaNetClientBuilder =
    withProxyOption(None)

  def withHostnameVerifierOption(hostnameVerifier: Option[HostnameVerifier]): JavaNetClientBuilder =
    copy(hostnameVerifier = hostnameVerifier)
  def withHostnameVerifier(hostnameVerifier: HostnameVerifier): JavaNetClientBuilder =
    withHostnameVerifierOption(Some(hostnameVerifier))
  def withoutHostnameVerifier: JavaNetClientBuilder =
    withHostnameVerifierOption(None)

  def withSslSocketFactoryOption(sslSocketFactory: Option[SSLSocketFactory]): JavaNetClientBuilder =
    copy(sslSocketFactory = sslSocketFactory)
  def withSslSocketFactory(sslSocketFactory: SSLSocketFactory): JavaNetClientBuilder =
    withSslSocketFactoryOption(Some(sslSocketFactory))
  def withoutSslSocketFactory: JavaNetClientBuilder =
    withSslSocketFactoryOption(None)

  def withBlockingExecutionContext(
      blockingExecutionContext: ExecutionContext): JavaNetClientBuilder =
    copy(blockingExecutionContext = blockingExecutionContext)

  /** Creates a [[Client]].
    *
    * The shutdown of this client is a no-op. $WHYNOSHUTDOWN
    */
  def create[F[_]](implicit F: Async[F], cs: ContextShift[F]): Client[F] = Client(open, F.unit)

  /** Creates a [[Client]] resource.
    *
    * The release of this resource is a no-op. $WHYNOSHUTDOWN
    */
  def resource[F[_]](implicit F: Async[F], cs: ContextShift[F]): Resource[F, Client[F]] =
    Resource.make(F.delay(create))(_.shutdown)

  /** Creates a [[Client]] stream.
    *
    * The bracketed release on this stream is a no-op. $WHYNOSHUTDOWN
    */
  def stream[F[_]](implicit F: Async[F], cs: ContextShift[F]): Stream[F, Client[F]] =
    Stream.resource(resource)

  private def open[F[_]](implicit F: Async[F], cs: ContextShift[F]) = Kleisli { req: Request[F] =>
    for {
      url <- F.delay(new URL(req.uri.toString))
      conn <- openConnection(url)
      _ <- configureSsl(conn)
      _ <- F.delay(conn.setConnectTimeout(timeoutMillis(connectTimeout)))
      _ <- F.delay(conn.setReadTimeout(timeoutMillis(readTimeout)))
      _ <- F.delay(conn.setRequestMethod(req.method.renderString))
      _ <- F.delay(req.headers.foreach {
        case Header(name, value) => conn.setRequestProperty(name.value, value)
      })
      _ <- F.delay(conn.setInstanceFollowRedirects(false))
      _ <- F.delay(conn.setDoInput(true))
      resp <- cs.evalOn(blockingExecutionContext)(blocking(fetchResponse(req, conn)))
    } yield DisposableResponse(resp, F.delay(conn.getInputStream.close()))
  }

  private def fetchResponse[F[_]](req: Request[F], conn: HttpURLConnection)(
      implicit F: Async[F],
      cs: ContextShift[F]) =
    for {
      _ <- writeBody(req, conn)
      code <- F.delay(conn.getResponseCode)
      status <- F.fromEither(Status.fromInt(code))
      headers <- F.delay(
        Headers(
          conn.getHeaderFields.asScala
            .filter(_._1 != null)
            .flatMap { case (k, vs) => vs.asScala.map(Header(k, _)) }
            .toList
        ))
      body = readInputStream(F.delay(conn.getInputStream), 4096, blockingExecutionContext)
    } yield Response(status = status, headers = headers, body = body)

  private def timeoutMillis(d: Duration): Int = d match {
    case d: FiniteDuration if d > Duration.Zero => d.toMillis.max(0).min(Int.MaxValue).toInt
    case _ => 0
  }

  private def openConnection[F[_]](url: URL)(implicit F: Sync[F]) = proxy match {
    case Some(p) =>
      F.delay(url.openConnection(p).asInstanceOf[HttpURLConnection])
    case None =>
      F.delay(url.openConnection().asInstanceOf[HttpURLConnection])
  }

  private def writeBody[F[_]](req: Request[F], conn: HttpURLConnection)(
      implicit F: Async[F],
      cs: ContextShift[F]) =
    if (req.isChunked) {
      F.delay(conn.setDoOutput(true)) *>
        F.delay(conn.setChunkedStreamingMode(4096)) *>
        req.body
          .to(writeOutputStream(F.delay(conn.getOutputStream), blockingExecutionContext, false))
          .compile
          .drain
    } else
      req.contentLength match {
        case Some(len) if len >= 0L =>
          F.delay(conn.setDoOutput(true)) *>
            F.delay(conn.setFixedLengthStreamingMode(len)) *>
            req.body
              .to(writeOutputStream(F.delay(conn.getOutputStream), blockingExecutionContext, false))
              .compile
              .drain
        case _ =>
          F.delay(conn.setDoOutput(false))
      }

  private def configureSsl[F[_]](conn: HttpURLConnection)(implicit F: Sync[F]) =
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

  /**
    * @param blockingExecutionContext An `ExecutionContext` on which
    * blocking operations will be performed.
    */
  def apply(blockingExecutionContext: ExecutionContext): JavaNetClientBuilder =
    new JavaNetClientBuilder(
      connectTimeout = Duration.Inf,
      readTimeout = Duration.Inf,
      proxy = None,
      hostnameVerifier = None,
      sslSocketFactory = None,
      blockingExecutionContext = blockingExecutionContext
    ) {}
}

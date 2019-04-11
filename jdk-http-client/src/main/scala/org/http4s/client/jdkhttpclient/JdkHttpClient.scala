package org.http4s.client.jdkhttpclient

import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{ProxySelector, URI}
import java.nio.ByteBuffer
import java.time.{Duration => JDuration}
import java.util
import java.util.concurrent.{Executor, Flow}

import cats.ApplicativeError
import cats.effect._
import cats.implicits._
import fs2.interop.reactivestreams._
import fs2.{Chunk, Stream}
import javax.net.ssl.SSLContext
import org.http4s.client.Client
import org.http4s.internal.fromCompletableFuture
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Headers, HttpVersion, Request, Response, Status}
import org.reactivestreams.FlowAdapters

import scala.collection.JavaConverters._
import scala.concurrent.duration._

// TODO documentation
object JdkHttpClient {

  def apply[F[_]](
      jdkHttpClient: HttpClient,
      ignoredHeaders: Set[CaseInsensitiveString] = restrictedHeaders
  )(implicit F: ConcurrentEffect[F]): Client[F] = {

    def convertRequest(req: Request[F]): F[HttpRequest] =
      convertHttpVersionFromHttp4s[F](req.httpVersion).map { version =>
        val rb = HttpRequest.newBuilder
          .method(
            req.method.name, {
              val publisher = FlowAdapters.toFlowPublisher(
                StreamUnicastPublisher(req.body.chunks.map(_.toByteBuffer)))
              if (req.isChunked)
                BodyPublishers.fromPublisher(publisher)
              else
                req.contentLength.fold(BodyPublishers.noBody)(
                  BodyPublishers.fromPublisher(publisher, _))
            }
          )
          .uri(URI.create(req.uri.renderString))
          .version(version)
        val headers = req.headers.iterator
        // hacky workaround (see e.g. https://stackoverflow.com/questions/53979173)
          .filterNot(h => ignoredHeaders.contains(h.name))
          .flatMap(h => Iterator(h.name.value, h.value))
          .toArray
        (if (headers.isEmpty) rb else rb.headers(headers: _*)).build
      }

    def convertResponse(res: HttpResponse[Flow.Publisher[util.List[ByteBuffer]]]): F[Response[F]] =
      F.fromEither(Status.fromInt(res.statusCode)).map { status =>
        Response(
          status = status,
          headers = Headers(res.headers.map.asScala.flatMap {
            case (k, vs) => vs.asScala.map(Header(k, _))
          }.toList),
          httpVersion = res.version match {
            case HttpClient.Version.HTTP_1_1 => HttpVersion.`HTTP/1.1`
            case HttpClient.Version.HTTP_2 => HttpVersion.`HTTP/2.0`
          },
          body = FlowAdapters
            .toPublisher(res.body)
            .toStream[F]
            .flatMap(bs =>
              Stream.fromIterator(bs.asScala.map(Chunk.byteBuffer).iterator).flatMap(Stream.chunk))
        )
      }

    Client[F] { req =>
      Resource.liftF(
        convertRequest(req)
          .flatMap(r =>
            fromCompletableFuture(F.delay(jdkHttpClient.sendAsync(r, BodyHandlers.ofPublisher))))
          .flatMap(convertResponse))
    }
  }

  def createUnderlyingClient[F[_]](
      httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
      connectTimeout: Duration = 10.seconds,
      redirectPolicy: HttpClient.Redirect = HttpClient.Redirect.NEVER,
      sslContext: => SSLContext = SSLContext.getDefault,
      proxySelector: => ProxySelector = ProxySelector.getDefault,
      customEC: Option[Executor] = None
  )(implicit F: Sync[F]): F[HttpClient] =
    convertHttpVersionFromHttp4s[F](httpVersion).flatMap(version =>
      F.delay {
        val builder = HttpClient.newBuilder
          .version(version)
          .connectTimeout(JDuration.ofNanos(connectTimeout.toNanos))
          .followRedirects(redirectPolicy)
          .sslContext(sslContext)
          .proxy(proxySelector)
        customEC.fold(builder)(builder.executor).build()
    })

  def simple[F[_]: ConcurrentEffect]: F[Client[F]] =
    createUnderlyingClient[F]().map(apply[F](_))

  // see jdk.internal.net.http.common.Utils#DISALLOWED_HEADERS_SET
  private val restrictedHeaders =
    Set(
      "connection",
      "content-length",
      "date",
      "expect",
      "from",
      "host",
      "upgrade",
      "via",
      "warning"
    ).map(CaseInsensitiveString(_))

  private def convertHttpVersionFromHttp4s[F[_]](version: HttpVersion)(
      implicit F: ApplicativeError[F, Throwable]): F[HttpClient.Version] =
    version match {
      case HttpVersion.`HTTP/1.1` => HttpClient.Version.HTTP_1_1.pure[F]
      case HttpVersion.`HTTP/2.0` => HttpClient.Version.HTTP_2.pure[F]
      case _ => F.raiseError(new IllegalArgumentException("invalid HTTP version"))
    }

}

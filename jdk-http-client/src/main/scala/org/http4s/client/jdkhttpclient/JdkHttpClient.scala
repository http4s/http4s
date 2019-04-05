package org.http4s.client.jdkhttpclient

import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.{CancellationException, CompletableFuture, CompletionException, Flow}
import java.util.function.BiFunction

import cats.effect._
import cats.implicits._
import fs2.interop.reactivestreams._
import fs2.{Chunk, Stream}
import org.http4s.client.Client
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Headers, HttpVersion, Request, Response, Status}
import org.reactivestreams.FlowAdapters

import scala.collection.JavaConverters._

object JdkHttpClient {

  def apply[F[_]](builder: HttpClient.Builder = HttpClient.newBuilder)(
      implicit F: ConcurrentEffect[F]): F[Client[F]] =
    F.delay(builder.build).map { jdkHttpClient =>
      Client[F] { req =>
        Resource.liftF {
          F.delay(jdkHttpClient.sendAsync(convertRequest(req), BodyHandlers.ofPublisher))
            .flatMap(fromJavaFuture(_))
            .flatMap(convertResponse(_))
        }
      }
    }

  private def fromJavaFuture[F[_], A](cf: CompletableFuture[A])(implicit F: Concurrent[F]): F[A] =
    F.cancelable(cb => {
      cf.handle[Unit](new BiFunction[A, Throwable, Unit] {
        override def apply(result: A, err: Throwable): Unit = err match {
          case null => cb(Right(result))
          case _: CancellationException => ()
          case ex: CompletionException if ex.getCause ne null => cb(Left(ex.getCause))
          case ex => cb(Left(ex))
        }
      })
      F.delay { cf.cancel(true); () }
    })

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

  def convertRequest[F[_]: ConcurrentEffect](req: Request[F]): HttpRequest = {
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
      .version(req.httpVersion match {
        case HttpVersion.`HTTP/1.1` => HttpClient.Version.HTTP_1_1
        case HttpVersion.`HTTP/2.0` => HttpClient.Version.HTTP_2
        case _ => HttpClient.Version.HTTP_1_1
      })
    val headers = req.headers.iterator.flatMap { h =>
      // hacky workaround (see e.g. https://stackoverflow.com/questions/53979173)
      if (restrictedHeaders.contains(h.name)) Iterator.empty
      else Iterator(h.name.value, h.value)
    }.toArray
    (if (headers.isEmpty) rb else rb.headers(headers: _*)).build
  }

  def convertResponse[F[_]: ConcurrentEffect](
      res: HttpResponse[Flow.Publisher[util.List[ByteBuffer]]]): F[Response[F]] =
    ConcurrentEffect[F].fromEither(Status.fromInt(res.statusCode)).map { status =>
      Response(
        status = status, // TODO better error handling
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

}

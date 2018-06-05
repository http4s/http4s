package org.http4s.client.okhttp

import java.io.IOException

import cats.data._
import cats.effect._
import cats.implicits._
import cats.effect.implicits._
import okhttp3.{
  Call,
  Callback,
  OkHttpClient,
  Protocol,
  RequestBody,
  Headers => OKHeaders,
  MediaType => OKMediaType,
  Request => OKRequest,
  Response => OKResponse
}
import okio.BufferedSink
import org.http4s.{Header, Headers, HttpVersion, Method, Request, Response, Status}
import org.http4s.client.{Client, DisposableResponse}
import fs2.Stream._
import fs2._
import fs2.io._
import org.log4s.getLogger

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object OkHttp {

  private val logger = getLogger

  def defaultConfig[F[_]]()(implicit F: Effect[F]): F[OkHttpClient.Builder] = F.delay {
    new OkHttpClient.Builder()
      .protocols(
        List(
          Protocol.HTTP_2,
          Protocol.HTTP_1_1
        ).asJava)
  }

  /** Create an okhttp client with the default config */
  def apply[F[_]]()(implicit F: Effect[F], ec: ExecutionContext): F[Client[F]] =
    apply(defaultConfig[F]())

  /** Create an okhttp client with a supplied config */
  def apply[F[_]](
      config: F[OkHttpClient.Builder])(implicit F: Effect[F], ec: ExecutionContext): F[Client[F]] =
    F.map(config) { c =>
      val client = c.build()
      Client(
        Kleisli { req =>
          F.async[DisposableResponse[F]] { cb =>
            client.newCall(toOkHttpRequest(req)).enqueue(handler(cb))
            ()
          }
        },
        F.delay({
          try {
            client.dispatcher.executorService().shutdown()
          } catch {
            case NonFatal(t) =>
              logger.warn(t)("Unable to shut down dispatcher when disposing of OkHttp client")
          }
          try {
            client.connectionPool().evictAll()
          } catch {
            case NonFatal(t) =>
              logger.warn(t)("Unable to evict connection pool when disposing of OkHttp client")
          }
          if (client.cache() != null) {
            try {
              client.cache().close()
            } catch {
              case NonFatal(t) =>
                logger.warn(t)("Unable to close cache when disposing of OkHttp client")
            }
          }
        })
      )
    }

  /* Create a bracketed stream of a Client with the default config  */
  def stream[F[_]]()(implicit F: Effect[F], ec: ExecutionContext): Stream[F, Client[F]] =
    stream(defaultConfig[F]())

  /* Create a bracketed stream of a Client with a supplied config */
  def stream[F[_]](config: F[OkHttpClient.Builder])(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, Client[F]] =
    Stream.bracket(apply(config))(_.shutdown)

  private def handler[F[_]](cb: Either[Throwable, DisposableResponse[F]] => Unit)(
      implicit F: Effect[F],
      ec: ExecutionContext): Callback =
    new Callback {
      override def onFailure(call: Call, e: IOException): Unit =
        ec.execute(new Runnable { override def run(): Unit = cb(Left(e)) })

      override def onResponse(call: Call, response: OKResponse): Unit = {
        val protocol = response.protocol() match {
          case Protocol.HTTP_2 => HttpVersion.`HTTP/2.0`
          case Protocol.HTTP_1_1 => HttpVersion.`HTTP/1.1`
          case Protocol.HTTP_1_0 => HttpVersion.`HTTP/1.0`
          case _ => HttpVersion.`HTTP/1.1`
        }
        val status = Status.fromInt(response.code())
        val bodyStream = response.body.byteStream()
        val dr = status
          .map { s =>
            new DisposableResponse[F](
              Response[F](headers = getHeaders(response), httpVersion = protocol)
                .withStatus(s)
                .withBodyStream(
                  readInputStream(F.pure(bodyStream), chunkSize = 1024, closeAfterUse = true)),
              F.delay({
                bodyStream.close(); ()
              })
            )
          }
          .leftMap { t =>
            // we didn't understand the status code, close the body and return a failure
            bodyStream.close()
            t
          }
        ec.execute(new Runnable {
          override def run(): Unit = cb(dr)
        })
      }
    }

  private def getHeaders(response: OKResponse): Headers =
    Headers(response.headers().names().asScala.toList.flatMap { v =>
      response.headers().values(v).asScala.map(Header(v, _))
    })

  private def toOkHttpRequest[F[_]](req: Request[F])(implicit F: Effect[F]): OKRequest = {
    val body = req match {
      case _ if req.isChunked || req.contentLength.isDefined =>
        new RequestBody {
          override def contentType(): OKMediaType =
            req.contentType.map(c => OKMediaType.parse(c.toString())).orNull

          override def writeTo(sink: BufferedSink): Unit =
            req.body.chunks
              .map(_.toArray)
              .to(Sink { b: Array[Byte] =>
                F.delay {
                  sink.write(b); ()
                }
              })
              .compile
              .drain
              .runAsync {
                case Left(t) =>
                  IO { logger.warn(t)("Unable to write to OkHttp sink") }
                case Right(_) =>
                  IO.unit
              }
              .unsafeRunSync()
        }
      // if it's a GET or HEAD, okhttp wants us to pass null
      case _ if req.method == Method.GET || req.method == Method.HEAD => null
      // for anything else we can pass a body which produces no output
      case _ =>
        new RequestBody {
          override def contentType(): OKMediaType = null
          override def writeTo(sink: BufferedSink): Unit = ()
        }
    }

    new OKRequest.Builder()
      .headers(OKHeaders.of(req.headers.toList.map(h => (h.name.value, h.value)).toMap.asJava))
      .method(req.method.toString(), body)
      .url(req.uri.toString())
      .build()
  }

}

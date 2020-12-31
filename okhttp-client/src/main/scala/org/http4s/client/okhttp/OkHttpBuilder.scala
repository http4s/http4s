/*
 * Copyright 2018 http4s.org
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

package org.http4s.client.okhttp

import java.io.IOException

import cats.effect._
import cats.syntax.all._
import fs2.io._
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
import org.http4s.client.Client
import org.http4s.internal.BackendBuilder
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.log4s.getLogger
import scala.util.control.NonFatal
import scala.util.chaining._
import cats.effect.std.Dispatcher

/** A builder for [[org.http4s.client.Client]] with an OkHttp backend.
  *
  * @define DISPATCHER a [[cats.effect.std.Dispatcher]] using which
  * we will call unsafeRunSync
  *
  * @define WHYNOSHUTDOWN It is assumed that the OkHttp client is
  * passed to us as a Resource, or that the caller will shut it down, or
  * that the caller is comfortable letting OkHttp's resources expire on
  * their own.
  *
  * @param okHttpClient the underlying OkHttp client.
  * @param dispatcher $DISPATCHER
  */
sealed abstract class OkHttpBuilder[F[_]] private (
    val okHttpClient: OkHttpClient,
    val dispatcher: Dispatcher[F]
)(implicit protected val F: Async[F])
    extends BackendBuilder[F, Client[F]] {
  private[this] val logger = getLogger

  private type Result[F[_]] = Either[Throwable, Resource[F, Response[F]]]

  private def logTap[F[_]](result: Result[F])(implicit
      F: Async[F]): F[Either[Throwable, Resource[F, Response[F]]]] =
    (result match {
      case Left(e) => F.delay(logger.error(e)("Error in call back"))
      case Right(_) => F.unit
    }).map(_ => result)

  private def invokeCallback(result: Result[F], cb: Result[F] => Unit)(implicit
      F: Async[F]): Unit = {
    logTap(result)
      .flatMap(r => F.delay(cb(r)))
      .pipe(dispatcher.unsafeRunSync)
    ()
  }

  private def copy(
      okHttpClient: OkHttpClient = okHttpClient,
      dispatcher: Dispatcher[F] = dispatcher) = new OkHttpBuilder[F](okHttpClient, dispatcher) {}

  def withOkHttpClient(okHttpClient: OkHttpClient): OkHttpBuilder[F] =
    copy(okHttpClient = okHttpClient)

  def withDispatcher(dispatcher: Dispatcher[F]): OkHttpBuilder[F] =
    copy(dispatcher = dispatcher)

  /** Creates the [[org.http4s.client.Client]]
    *
    * The shutdown method on this client is a no-op.  $WHYNOSHUTDOWN
    */
  def create: Client[F] = Client(run)

  def resource: Resource[F, Client[F]] =
    Resource.make(F.delay(create))(_ => F.unit)

  private def run(req: Request[F]) =
    Resource.suspend(F.async_[Resource[F, Response[F]]] { cb =>
      okHttpClient.newCall(toOkHttpRequest(req)).enqueue(handler(cb))
    })

  private def handler(cb: Result[F] => Unit)(implicit F: Async[F]): Callback =
    new Callback {
      override def onFailure(call: Call, e: IOException): Unit =
        invokeCallback(Left(e), cb)

      override def onResponse(call: Call, response: OKResponse): Unit = {
        val protocol = response.protocol() match {
          case Protocol.HTTP_2 => HttpVersion.`HTTP/2.0`
          case Protocol.HTTP_1_1 => HttpVersion.`HTTP/1.1`
          case Protocol.HTTP_1_0 => HttpVersion.`HTTP/1.0`
          case _ => HttpVersion.`HTTP/1.1`
        }
        val status = Status.fromInt(response.code())
        val bodyStream = response.body.byteStream()
        val body = readInputStream(F.pure(bodyStream), 1024, false)
        val dispose = F.delay {
          bodyStream.close()
          ()
        }
        val r = status
          .map { s =>
            Resource[F, Response[F]](
              F.pure(
                (
                  Response[F](
                    status = s,
                    headers = getHeaders(response),
                    httpVersion = protocol,
                    body = body),
                  dispose
                ))
            )
          }
          .leftMap { t =>
            // we didn't understand the status code, close the body and return a failure
            bodyStream.close()
            t
          }
        invokeCallback(r, cb)
      }
    }

  private def getHeaders(response: OKResponse): Headers =
    Headers(response.headers().names().asScala.toList.flatMap { v =>
      response.headers().values(v).asScala.map(Header(v, _))
    })

  private def toOkHttpRequest(req: Request[F])(implicit F: Async[F]): OKRequest = {
    val body = req match {
      case _ if req.isChunked || req.contentLength.isDefined =>
        new RequestBody {
          override def contentType(): OKMediaType =
            req.contentType.map(c => OKMediaType.parse(c.toString())).orNull

          //OKHttp will override the content-length header set below and always use "transfer-encoding: chunked" unless this method is overriden
          override def contentLength(): Long = req.contentLength.getOrElse(-1L)

          override def writeTo(sink: BufferedSink): Unit = {
            // This has to be synchronous with this method, or else
            // chunks get silently dropped.
            req.body.chunks
              .map(_.toArray)
              .evalMap { (b: Array[Byte]) =>
                F.delay {
                  sink.write(b); ()
                }
              }
              .compile
              .drain
              .pipe(dispatcher.unsafeRunSync)
            ()
          }
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
      .headers(OKHeaders.of(req.headers.toList.map(h => (h.name.toString, h.value)).toMap.asJava))
      .method(req.method.toString(), body)
      .url(req.uri.toString())
      .build()
  }
}

/** Builder for a [[org.http4s.client.Client]] with an OkHttp backend
  */
object OkHttpBuilder {
  private[this] val logger = getLogger

  /** Creates a builder.
    *
    * @param okHttpClient the underlying client.
    * @param dispatcher $DISPATCHER
    */
  def apply[F[_]: Async](okHttpClient: OkHttpClient, dispatcher: Dispatcher[F]): OkHttpBuilder[F] =
    new OkHttpBuilder[F](okHttpClient, dispatcher) {}

  /** Create a builder with a default OkHttp client.  The builder is
    * returned as a `Resource` so we shut down the OkHttp client that
    * we create.
    *
    * @param dispatcher $DISPATCHER
    */
  def withDefaultClient[F[_]: Async](dispatcher: Dispatcher[F]): Resource[F, OkHttpBuilder[F]] =
    defaultOkHttpClient.map(apply(_, dispatcher))

  private def defaultOkHttpClient[F[_]](implicit F: Async[F]): Resource[F, OkHttpClient] =
    Resource.make(F.delay(new OkHttpClient()))(shutdown(_))

  private def shutdown[F[_]](client: OkHttpClient)(implicit F: Async[F]) =
    F.delay {
      try client.dispatcher.executorService().shutdown()
      catch {
        case NonFatal(t) =>
          logger.warn(t)("Unable to shut down dispatcher when disposing of OkHttp client")
      }
      try client.connectionPool().evictAll()
      catch {
        case NonFatal(t) =>
          logger.warn(t)("Unable to evict connection pool when disposing of OkHttp client")
      }
      if (client.cache() != null)
        try client.cache().close()
        catch {
          case NonFatal(t) =>
            logger.warn(t)("Unable to close cache when disposing of OkHttp client")
        }
    }
}

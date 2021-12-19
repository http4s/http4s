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

package org.http4s
package okhttp.client

import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import fs2.io._
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.{Headers => OKHeaders}
import okhttp3.{MediaType => OKMediaType}
import okhttp3.{Request => OKRequest}
import okhttp3.{Response => OKResponse}
import okio.BufferedSink
import org.http4s.client.Client
import org.http4s.internal.BackendBuilder
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.http4s.internal.invokeCallback
import org.log4s.getLogger

import java.io.IOException
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/** A builder for [[org.http4s.client.Client]] with an OkHttp backend.
  *
  * @define BLOCKINGEC an execution context onto which all blocking
  * I/O operations will be shifted.
  *
  * @define WHYNOSHUTDOWN It is assumed that the OkHttp client is
  * passed to us as a Resource, or that the caller will shut it down, or
  * that the caller is comfortable letting OkHttp's resources expire on
  * their own.
  *
  * @param okHttpClient the underlying OkHttp client.
  * @param blockingExecutionContext $BLOCKINGEC
  */
sealed abstract class OkHttpBuilder[F[_]] private (
    val okHttpClient: OkHttpClient,
    val blocker: Blocker,
)(implicit protected val F: ConcurrentEffect[F], cs: ContextShift[F])
    extends BackendBuilder[F, Client[F]] {
  private[this] val logger = getLogger

  private def copy(
      okHttpClient: OkHttpClient = okHttpClient,
      blocker: Blocker = blocker,
  ) = new OkHttpBuilder[F](okHttpClient, blocker) {}

  def withOkHttpClient(okHttpClient: OkHttpClient): OkHttpBuilder[F] =
    copy(okHttpClient = okHttpClient)

  def withBlocker(blocker: Blocker): OkHttpBuilder[F] =
    copy(blocker = blocker)

  @deprecated("Use withBlocker instead", "0.21.0")
  def withBlockingExecutionContext(blockingExecutionContext: ExecutionContext): OkHttpBuilder[F] =
    copy(blocker = Blocker.liftExecutionContext(blockingExecutionContext))

  /** Creates the [[org.http4s.client.Client]]
    *
    * The shutdown method on this client is a no-op.  $WHYNOSHUTDOWN
    */
  def create: Client[F] = Client(run)

  def resource: Resource[F, Client[F]] =
    Resource.make(F.delay(create))(_ => F.unit)

  private def run(req: Request[F]) =
    Resource.suspend(F.async[Resource[F, Response[F]]] { cb =>
      okHttpClient.newCall(toOkHttpRequest(req)).enqueue(handler(cb))
      ()
    })

  private def handler(
      cb: Either[Throwable, Resource[F, Response[F]]] => Unit
  )(implicit F: ConcurrentEffect[F], cs: ContextShift[F]): Callback =
    new Callback {
      override def onFailure(call: Call, e: IOException): Unit =
        invokeCallback(logger)(cb(Left(e)))

      override def onResponse(call: Call, response: OKResponse): Unit = {
        val protocol = response.protocol() match {
          case Protocol.HTTP_2 => HttpVersion.`HTTP/2`
          case Protocol.HTTP_1_1 => HttpVersion.`HTTP/1.1`
          case Protocol.HTTP_1_0 => HttpVersion.`HTTP/1.0`
          case _ => HttpVersion.`HTTP/1.1`
        }
        val status = Status.fromInt(response.code())
        val bodyStream = response.body.byteStream()
        val body = readInputStream(F.pure(bodyStream), 1024, blocker, false)
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
                    body = body,
                  ),
                  dispose,
                )
              )
            )
          }
          .leftMap { t =>
            // we didn't understand the status code, close the body and return a failure
            bodyStream.close()
            t
          }
        invokeCallback(logger)(cb(r))
      }
    }

  private def getHeaders(response: OKResponse): Headers =
    Headers(response.headers().names().asScala.toList.flatMap { k =>
      response.headers().values(k).asScala.map(k -> _)
    })

  private def toOkHttpRequest(req: Request[F])(implicit F: Effect[F]): OKRequest = {
    val body = req match {
      case _ if req.isChunked || req.contentLength.isDefined =>
        new RequestBody {
          override def contentType(): OKMediaType =
            req.contentType.map(c => OKMediaType.parse(c.toString())).orNull

          // OKHttp will override the content-length header set below and always use "transfer-encoding: chunked" unless this method is overriden
          override def contentLength(): Long = req.contentLength.getOrElse(-1L)

          override def writeTo(sink: BufferedSink): Unit =
            req.body.chunks
              .map(_.toArray)
              .evalMap { (b: Array[Byte]) =>
                F.delay {
                  sink.write(b); ()
                }
              }
              .compile
              .drain
              // This has to be synchronous with this method, or else
              // chunks get silently dropped.
              .toIO
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
      .headers(OKHeaders.of(req.headers.headers.map(h => (h.name.toString, h.value)).toMap.asJava))
      .method(req.method.toString(), body)
      .url(req.uri.toString())
      .build()
  }
}

/** Builder for a [[org.http4s.client.Client]] with an OkHttp backend
  *
  * @define BLOCKER a [[cats.effect.Blocker]] onto which all blocking
  * I/O operations will be shifted.
  */
object OkHttpBuilder {
  private[this] val logger = getLogger

  /** Creates a builder.
    *
    * @param okHttpClient the underlying client.
    * @param blocker $BLOCKER
    */
  def apply[F[_]: ConcurrentEffect: ContextShift](
      okHttpClient: OkHttpClient,
      blocker: Blocker,
  ): OkHttpBuilder[F] =
    new OkHttpBuilder[F](okHttpClient, blocker) {}

  /** Create a builder with a default OkHttp client.  The builder is
    * returned as a `Resource` so we shut down the OkHttp client that
    * we create.
    *
    * @param blocker $BLOCKER
    */
  def withDefaultClient[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker
  ): Resource[F, OkHttpBuilder[F]] =
    defaultOkHttpClient.map(apply(_, blocker))

  private def defaultOkHttpClient[F[_]](implicit
      F: ConcurrentEffect[F]
  ): Resource[F, OkHttpClient] =
    Resource.make(F.delay(new OkHttpClient()))(shutdown(_))

  private def shutdown[F[_]](client: OkHttpClient)(implicit F: Sync[F]) =
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

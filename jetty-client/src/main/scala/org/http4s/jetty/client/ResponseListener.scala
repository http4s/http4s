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
package jetty
package client

import cats.effect._
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.all._
import fs2._
import fs2.Stream._
import java.nio.ByteBuffer
import org.eclipse.jetty.client.api.{Result, Response => JettyResponse}
import org.eclipse.jetty.http.{HttpFields, HttpVersion => JHttpVersion}
import org.eclipse.jetty.util.{Callback => JettyCallback}
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.http4s.internal.loggingAsyncCallback
import org.http4s.jetty.client.ResponseListener.Item
import org.log4s.getLogger

private[jetty] final case class ResponseListener[F[_]](
    queue: Queue[F, Option[Item]],
    cb: Callback[Resource[F, Response[F]]])(implicit F: Async[F], D: Dispatcher[F])
    extends JettyResponse.Listener.Adapter {
  import ResponseListener.logger

  /* Needed to properly propagate client errors */
  private[this] var responseSent = false

  override def onHeaders(response: JettyResponse): Unit = {
    val r = Status
      .fromInt(response.getStatus)
      .map { s =>
        responseSent = true
        Resource.pure[F, Response[F]](Response(
          status = s,
          httpVersion = getHttpVersion(response.getVersion),
          headers = getHeaders(response.getHeaders),
          body = Stream.fromQueueNoneTerminated(queue).repeatPull {
            _.uncons1.flatMap {
              case None => Pull.pure(None)
              case Some((Item.Done, _)) => Pull.pure(None)
              case Some((Item.Buf(b), tl)) => Pull.output(Chunk.byteBuffer(b)).as(Some(tl))
              case Some((Item.Raise(t), _)) => Pull.raiseError[F](t)
            }
          }
        ))
      }
      .leftMap { t => abort(t, response); t }

    D.unsafeRunAndForget(F.delay(cb(r)).attempt.flatMap(loggingAsyncCallback[F, Unit](logger)))
  }

  private def getHttpVersion(version: JHttpVersion): HttpVersion =
    version match {
      case JHttpVersion.HTTP_1_1 => HttpVersion.`HTTP/1.1`
      case JHttpVersion.HTTP_2 => HttpVersion.`HTTP/2`
      case JHttpVersion.HTTP_1_0 => HttpVersion.`HTTP/1.0`
      case _ => HttpVersion.`HTTP/1.1`
    }

  private def getHeaders(headers: HttpFields): Headers =
    Headers(headers.asScala.map(h => h.getName -> h.getValue).toList)

  override def onContent(
      response: JettyResponse,
      content: ByteBuffer,
      callback: JettyCallback): Unit = {
    val copy = ByteBuffer.allocate(content.remaining())
    copy.put(content).flip()
    enqueue(Item.Buf(copy)) {
      case Right(_) => F.delay(callback.succeeded())
      case Left(e) =>
        F.delay(logger.error(e)("Error in asynchronous callback")) >> F.delay(callback.failed(e))
    }
  }

  override def onFailure(response: JettyResponse, failure: Throwable): Unit =
    if (responseSent) enqueue(Item.Raise(failure))(_ => F.unit)
    else
      D.unsafeRunAndForget(
        F.delay(cb(Left(failure))).attempt.flatMap(loggingAsyncCallback[F, Unit](logger)))

  // the entire response has been received
  override def onSuccess(response: JettyResponse): Unit =
    closeStream()

  // the entire req/resp conversation has completed
  // (the request might complete after the response has been entirely received)
  override def onComplete(result: Result): Unit = ()

  private def abort(t: Throwable, response: JettyResponse): Unit =
    if (!response.abort(t)) // this also aborts the request
      logger.error(t)("Failed to abort the response")
    else
      closeStream()

  private def closeStream(): Unit =
    enqueue(Item.Done)(loggingAsyncCallback[F, Unit](logger))

  private def enqueue(item: Item)(cb: Either[Throwable, Unit] => F[Unit]): Unit =
    D.unsafeRunAndForget(queue.offer(item.some).attempt.flatMap(cb))
}

private[jetty] object ResponseListener {
  sealed trait Item
  object Item {
    case object Done extends Item
    case class Raise(t: Throwable) extends Item
    case class Buf(b: ByteBuffer) extends Item
  }

  private val logger = getLogger

  def apply[F[_]](cb: Callback[Resource[F, Response[F]]])(implicit
      F: Async[F],
      D: Dispatcher[F]): F[ResponseListener[F]] =
    Queue
      .synchronous[F, Option[Item]]
      .map(q => ResponseListener(q, cb))
}

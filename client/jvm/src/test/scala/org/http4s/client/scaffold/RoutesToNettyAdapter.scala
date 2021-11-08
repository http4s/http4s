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

package org.http4s.client.scaffold

import cats.effect.Ref
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits._
import fs2.Chunk
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpContent, HttpRequest, HttpResponseStatus}
import org.http4s
import org.http4s.{HttpRoutes, Method}

import scala.annotation.nowarn
import scala.collection.JavaConverters._

object RoutesToNettyAdapter {
  def apply[F[_]](routes: HttpRoutes[F], dispatcher: Dispatcher[F])(implicit F: Async[F]): F[ChannelInboundHandler] =
    RoutesToHandlerAdapter[F](routes, dispatcher).flatMap(HandlersToNettyAdapter[F](Map.empty, _))
}

object RoutesToHandlerAdapter {
  def apply[F[_]](routes: HttpRoutes[F], dispatcher: Dispatcher[F])(implicit
      F: Async[F]): F[RoutesToHandlerAdapter[F]] =
    for {
      initialDummyQueue <- Queue.unbounded[F, Option[Chunk[Byte]]]
      requestBodyQueue <- Ref[F].of(initialDummyQueue)
    } yield new RoutesToHandlerAdapter(routes, dispatcher, requestBodyQueue)
}

class RoutesToHandlerAdapter[F[_]](
    routes: HttpRoutes[F],
    dispatcher: Dispatcher[F],
    requestBodyQueue: Ref[F, Queue[F, Option[Chunk[Byte]]]])(implicit F: Async[F])
    extends Handler {

  override def onRequestStart(ctx: ChannelHandlerContext, request: HttpRequest): Unit =
    dispatcher.unsafeRunSync(Queue.unbounded[F, Option[Chunk[Byte]]].flatMap(requestBodyQueue.set))

  override def onContent(
      ctx: ChannelHandlerContext,
      request: HttpRequest,
      content: HttpContent): Unit = {
    val bytes = new Array[Byte](content.content().readableBytes())
    content.content().readBytes(bytes)
    dispatcher.unsafeRunSync(requestBodyQueue.get.flatMap(_.offer(Some(Chunk.array(bytes)))))
  }

  override def onRequestEnd(ctx: ChannelHandlerContext, request: HttpRequest): Unit =
    dispatcher.unsafeRunSync(
    for {
      method <- Method.fromString(request.method().name()).liftTo[F]
      uri <- http4s.Uri.fromString(request.uri()).liftTo[F]
      headers = http4s.Headers(request.headers().names().asScala.toVector.flatMap { k =>
        val vs = request.headers().getAll(k)
        vs.asScala.toVector.map { v =>
          (k -> v): http4s.Header.ToRaw
        }
      }): @nowarn("cat=deprecation")
      bodyQueue <- requestBodyQueue.get
      _ <- bodyQueue.offer(None)
      body = fs2.Stream.fromQueueNoneTerminatedChunk(bodyQueue)
      http4sRequest = http4s.Request(method, uri, headers = headers, body = body)
      response <- routes.run(http4sRequest).value
      _ <- response.fold(
        F.delay(HandlerHelpers.sendResponse(ctx, request, HttpResponseStatus.NOT_FOUND))
      ) { response =>
        val headers = new DefaultHttpHeaders(false)
        response.headers.foreach(h => headers.add(h.name.toString, h.value))
        response.body.compile.to(Array).flatMap { bodyBytes =>
          F.delay(
            HandlerHelpers.sendResponse(
              ctx,
              request,
              HttpResponseStatus.valueOf(response.status.code),
              Unpooled.copiedBuffer(bodyBytes),
              headers
            )
          )
        }
      }
    } yield ()
    )
}

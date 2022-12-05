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

package org.http4s.client.testkit.scaffold

import cats.effect.Ref
import cats.effect.implicits.genSpawnOps
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.implicits._
import fs2.Chunk
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandler
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http._
import org.http4s
import org.http4s.Headers
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Response
import org.http4s.headers.`Transfer-Encoding`

import scala.annotation.nowarn

private[http4s] object RoutesToNettyAdapter {
  def apply[F[_]](routes: HttpRoutes[F], dispatcher: Dispatcher[F])(implicit
      F: Async[F]
  ): F[ChannelInboundHandler] =
    RoutesToHandlerAdapter[F](routes, dispatcher).flatMap(HandlersToNettyAdapter[F](Map.empty, _))
}

private[http4s] object RoutesToHandlerAdapter {
  def apply[F[_]](routes: HttpRoutes[F], dispatcher: Dispatcher[F])(implicit
      F: Async[F]
  ): F[RoutesToHandlerAdapter[F]] =
    for {
      initialDummyQueue <- Queue.unbounded[F, Option[Chunk[Byte]]]
      requestBodyQueue <- Ref[F].of(initialDummyQueue)
    } yield new RoutesToHandlerAdapter(routes, dispatcher, requestBodyQueue)
}

private[http4s] class RoutesToHandlerAdapter[F[_]](
    routes: HttpRoutes[F],
    dispatcher: Dispatcher[F],
    requestBodyQueue: Ref[F, Queue[F, Option[Chunk[Byte]]]],
)(implicit F: Async[F])
    extends Handler {

  override def onRequestStart(ctx: ChannelHandlerContext, request: HttpRequest): Unit =
    dispatcher.unsafeRunSync(
      for {
        method <- Method.fromString(request.method().name()).liftTo[F]
        uri <- http4s.Uri.fromString(request.uri()).liftTo[F]
        headers = {
          import scala.collection.JavaConverters._

          http4s.Headers(request.headers().names().asScala.toVector.flatMap { k =>
            val vs = request.headers().getAll(k)
            vs.asScala.toVector.map(v => (k -> v): http4s.Header.ToRaw)
          })
        }: @nowarn("cat=deprecation")
        bodyQueue <- Queue.unbounded[F, Option[Chunk[Byte]]]
        _ <- requestBodyQueue.set(bodyQueue)
        body = fs2.Stream.fromQueueNoneTerminatedChunk(bodyQueue)
        http4sRequest = http4s.Request(method, uri, headers = headers, body = body)
        _ <- processRequest(ctx, http4sRequest).start
      } yield ()
    )

  private def processRequest(
      ctx: ChannelHandlerContext,
      http4sRequest: http4s.Request[F],
  ): F[Unit] =
    for {
      response <- routes.run(http4sRequest).value
      _ <- response.fold(
        F.delay(HandlerHelpers.sendResponse(ctx, NOT_FOUND)).liftToF
      )(response => sendResponse(ctx, response))
    } yield ()

  private def sendResponse(ctx: ChannelHandlerContext, response: Response[F]): F[Unit] =
    response.headers.get[`Transfer-Encoding`] match {
      case None => sendStrictResponse(ctx, response)
      case Some(codings) if codings.hasChunked => sendChunkedResponse(ctx, response)
      case Some(_) => F.delay(HandlerHelpers.sendResponse(ctx, BAD_REQUEST)).liftToF
    }

  private def sendStrictResponse(ctx: ChannelHandlerContext, response: Response[F]): F[Unit] =
    response.body.compile.to(Array).flatMap { bodyBytes =>
      F.delay(
        HandlerHelpers.sendResponse(
          ctx,
          HttpResponseStatus.valueOf(response.status.code),
          Unpooled.copiedBuffer(bodyBytes),
          makeNettyHeaders(response.headers),
        )
      ).liftToF
    }

  private def sendChunkedResponse(ctx: ChannelHandlerContext, response: Response[F]): F[Unit] =
    for {
      _ <- F
        .delay(
          HandlerHelpers.sendChunkedResponseHead(
            ctx,
            HttpResponseStatus.valueOf(response.status.code),
            makeNettyHeaders(response.headers),
          )
        )
        .liftToF
      _ <- response.body.chunks
        .foreach(chunk =>
          F.delay(HandlerHelpers.sendChunk(ctx, Unpooled.copiedBuffer(chunk.toArray))).liftToF
        )
        .compile
        .drain
      _ <- F.delay(HandlerHelpers.sendEmptyLastChunk(ctx)).liftToF
    } yield ()

  private def makeNettyHeaders(http4sHeaders: Headers): DefaultHttpHeaders = {
    val nettyHeaders = new DefaultHttpHeaders()
    http4sHeaders.foreach { h =>
      nettyHeaders.add(h.name.toString, h.value)
      ()
    }
    nettyHeaders
  }

  override def onContent(
      ctx: ChannelHandlerContext,
      request: HttpRequest,
      content: HttpContent,
  ): Unit = {
    val bytes = new Array[Byte](content.content().readableBytes())
    content.content().readBytes(bytes)
    dispatcher.unsafeRunSync(requestBodyQueue.get.flatMap(_.offer(Some(Chunk.array(bytes)))))
  }

  override def onRequestEnd(ctx: ChannelHandlerContext, request: HttpRequest): Unit =
    dispatcher.unsafeRunSync(requestBodyQueue.get.flatMap(_.offer(None)))

}

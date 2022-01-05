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

package org.http4s.blaze
package client

import cats.effect._
import cats.implicits.catsSyntaxApplicativeId
import fs2.Stream
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import org.http4s.Status.Ok
import org.http4s._
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.client.scaffold.Handler
import org.http4s.client.scaffold.HandlerHelpers
import org.http4s.client.scaffold.HandlersToNettyAdapter
import org.http4s.client.scaffold.RoutesToHandlerAdapter
import org.http4s.client.scaffold.ServerScaffold
import org.http4s.client.testroutes.GetRoutes
import org.http4s.dsl.io._

import javax.net.ssl.SSLContext
import scala.concurrent.duration._

trait BlazeClientBase extends Http4sSuite {
  val tickWheel: TickWheelExecutor = new TickWheelExecutor(tick = 50.millis)

  def builder(
      maxConnectionsPerRequestKey: Int,
      maxTotalConnections: Int = 5,
      responseHeaderTimeout: Duration = 30.seconds,
      requestTimeout: Duration = 45.seconds,
      chunkBufferMaxSize: Int = 1024,
      sslContextOption: Option[SSLContext] = Some(bits.TrustingSslContext),
  ): BlazeClientBuilder[IO] = {
    val builder: BlazeClientBuilder[IO] =
      BlazeClientBuilder[IO](munitExecutionContext)
        .withCheckEndpointAuthentication(false)
        .withResponseHeaderTimeout(responseHeaderTimeout)
        .withRequestTimeout(requestTimeout)
        .withMaxTotalConnections(maxTotalConnections)
        .withMaxConnectionsPerRequestKey(Function.const(maxConnectionsPerRequestKey))
        .withChunkBufferMaxSize(chunkBufferMaxSize)
        .withScheduler(scheduler = tickWheel)

    sslContextOption.fold[BlazeClientBuilder[IO]](builder.withoutSslContext)(builder.withSslContext)
  }

  private def makeScaffold(num: Int, secure: Boolean): Resource[IO, ServerScaffold[IO]] =
    for {
      getHandler <- Resource.eval(
        RoutesToHandlerAdapter(
          HttpRoutes.of[IO] {
            case Method.GET -> Root / "infinite" =>
              Response[IO](Ok).withEntity(Stream.emit[IO, String]("a" * 8 * 1024).repeat).pure[IO]
            case Method.GET -> path =>
              GetRoutes.getPaths.getOrElse(path.toString, NotFound())
          }
        )
      )
      scaffold <- ServerScaffold[IO](
        num,
        secure,
        HandlersToNettyAdapter[IO](postHandlers, getHandler),
      )
    } yield scaffold

  private def postHandlers: Map[(HttpMethod, String), Handler] =
    Map(
      (HttpMethod.POST, "/respond-and-close-immediately") -> new Handler {
        // The client may receive the response before sending the whole request
        override def onRequestStart(ctx: ChannelHandlerContext, request: HttpRequest): Unit = {
          HandlerHelpers.sendResponse(
            ctx,
            HttpResponseStatus.OK,
            HandlerHelpers.utf8Text("a"),
            closeConnection = true,
          )
          ()
        }

        override def onRequestEnd(ctx: ChannelHandlerContext, request: HttpRequest): Unit = ()
      },
      (HttpMethod.POST, "/respond-and-close-immediately-no-body") -> new Handler {
        // The client may receive the response before sending the whole request
        override def onRequestStart(ctx: ChannelHandlerContext, request: HttpRequest): Unit = {
          HandlerHelpers.sendResponse(ctx, HttpResponseStatus.OK, closeConnection = true)
          ()
        }

        override def onRequestEnd(ctx: ChannelHandlerContext, request: HttpRequest): Unit = ()
      },
      (HttpMethod.POST, "/process-request-entity") -> new Handler {
        // We wait for the entire request to arrive before sending a response. That's how servers normally behave.
        override def onRequestEnd(ctx: ChannelHandlerContext, request: HttpRequest): Unit = {
          HandlerHelpers.sendResponse(ctx, HttpResponseStatus.OK)
          ()
        }
      },
    )

  val server: Fixture[ServerScaffold[IO]] = resourceSuiteFixture("http", makeScaffold(2, false))
  val secureServer: Fixture[ServerScaffold[IO]] = resourceSuiteFixture("https", makeScaffold(1, true))
}

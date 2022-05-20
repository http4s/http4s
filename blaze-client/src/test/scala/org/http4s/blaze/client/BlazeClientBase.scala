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
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.implicits.catsSyntaxApplicativeId
import fs2.Stream
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import org.http4s.Status.Ok
import org.http4s._
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.client.testkit.scaffold._
import org.http4s.client.testkit.testroutes.GetRoutes
import org.http4s.dsl.io._

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import scala.concurrent.duration._

trait BlazeClientBase extends Http4sSuite {
  val tickWheel: TickWheelExecutor = new TickWheelExecutor(tick = 50.millis)

  val TrustingSslContext: IO[SSLContext] = IO.blocking {
    val trustManager = new X509TrustManager {
      def getAcceptedIssuers(): Array[X509Certificate] = Array.empty
      def checkClientTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
      def checkServerTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
    }
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, Array(trustManager), new SecureRandom())
    ctx
  }

  def builder(
      maxConnectionsPerRequestKey: Int,
      maxTotalConnections: Int = 5,
      responseHeaderTimeout: Duration = 30.seconds,
      requestTimeout: Duration = 45.seconds,
      chunkBufferMaxSize: Int = 1024,
      sslContextOption: Option[SSLContext] = None,
      retries: Int = 0,
  ): BlazeClientBuilder[IO] = {
    val builder: BlazeClientBuilder[IO] =
      BlazeClientBuilder[IO]
        .withCheckEndpointAuthentication(false)
        .withResponseHeaderTimeout(responseHeaderTimeout)
        .withRequestTimeout(requestTimeout)
        .withMaxTotalConnections(maxTotalConnections)
        .withMaxConnectionsPerRequestKey(Function.const(maxConnectionsPerRequestKey))
        .withChunkBufferMaxSize(chunkBufferMaxSize)
        .withScheduler(scheduler = tickWheel)
        .withRetries(retries)

    sslContextOption.fold[BlazeClientBuilder[IO]](builder.withoutSslContext)(builder.withSslContext)
  }

  private def makeScaffold(num: Int, secure: Boolean): Resource[IO, ServerScaffold[IO]] =
    for {
      dispatcher <- Dispatcher[IO]
      getHandler <- Resource.eval(
        RoutesToHandlerAdapter(
          HttpRoutes.of[IO] {
            case Method.GET -> Root / "infinite" =>
              Response[IO](Ok).withEntity(Stream.emit[IO, String]("a" * 8 * 1024).repeat).pure[IO]

            case _ @(Method.GET -> path) =>
              GetRoutes.getPaths.getOrElse(path.toString, NotFound())
          },
          dispatcher,
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
  val secureServer: Fixture[ServerScaffold[IO]] =
    resourceSuiteFixture("https", makeScaffold(1, true))
}

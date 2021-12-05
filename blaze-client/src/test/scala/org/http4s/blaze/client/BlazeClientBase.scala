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
import cats.syntax.all._
import com.sun.net.httpserver.HttpHandler
import fs2.Stream
import org.http4s._
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.client.ServerScaffold
import org.http4s.client.testroutes.GetRoutes

import javax.net.ssl.SSLContext
import scala.concurrent.duration._

trait BlazeClientBase extends Http4sSuite {
  val tickWheel = new TickWheelExecutor(tick = 50.millis)

  def builder(
      maxConnectionsPerRequestKey: Int,
      maxTotalConnections: Int = 5,
      responseHeaderTimeout: Duration = 30.seconds,
      requestTimeout: Duration = 45.seconds,
      chunkBufferMaxSize: Int = 1024,
      sslContextOption: Option[SSLContext] = Some(bits.TrustingSslContext),
  ) = {
    val builder: BlazeClientBuilder[IO] =
      BlazeClientBuilder[IO]
        .withCheckEndpointAuthentication(false)
        .withResponseHeaderTimeout(responseHeaderTimeout)
        .withRequestTimeout(requestTimeout)
        .withMaxTotalConnections(maxTotalConnections)
        .withMaxConnectionsPerRequestKey(Function.const(maxConnectionsPerRequestKey))
        .withChunkBufferMaxSize(chunkBufferMaxSize)
        .withScheduler(scheduler = tickWheel)

    sslContextOption.fold[BlazeClientBuilder[IO]](builder.withoutSslContext)(builder.withSslContext)
  }

  private def testHandler: HttpHandler = exchange => {
    val io = exchange.getRequestMethod match {
      case "GET" =>
        val path = exchange.getRequestURI.getPath
        GetRoutes.getPaths.get(path) match {
          case Some(responseIO) =>
            responseIO.flatMap { resp =>
              val prelude =
                resp.headers.headers
                  .filter(_.name =!= headers.`Content-Length`.name)
                  .traverse_(h =>
                    IO.blocking(exchange.getResponseHeaders.add(h.name.toString, h.value))
                  ) *>
                  IO.blocking(
                    exchange
                      .sendResponseHeaders(resp.status.code, resp.contentLength.getOrElse(0L))
                  )
              val body =
                resp.body
                  .evalMap { byte =>
                    IO.blocking(exchange.getResponseBody.write(Array(byte)))
                  }
                  .compile
                  .drain
              val flush = IO.blocking(exchange.getResponseBody.flush())
              val close = IO.blocking(exchange.close())
              (prelude *> body *> flush).guarantee(close)
            }
          case None =>
            IO.blocking(exchange.sendResponseHeaders(404, -1)) *>
              IO.blocking(exchange.close())
        }
      case "POST" =>
        exchange.getRequestURI.getPath match {
          case "/respond-and-close-immediately" =>
            // We don't consume the req.getInputStream (the request entity). That means that:
            // - The client may receive the response before sending the whole request
            // - Jetty will send a "Connection: close" header and a TCP FIN+ACK along with the response, closing the connection.
            IO.blocking(exchange.sendResponseHeaders(200, 1L)) *>
              IO.blocking(exchange.getResponseBody.write(Array("a".toByte))) *>
              IO.blocking(exchange.getResponseBody.flush()) *>
              IO.blocking(exchange.close())
          case "/respond-and-close-immediately-no-body" =>
            // We don't consume the req.getInputStream (the request entity). That means that:
            // - The client may receive the response before sending the whole request
            // - Jetty will send a "Connection: close" header and a TCP FIN+ACK along with the response, closing the connection.
            IO.blocking(exchange.sendResponseHeaders(204, 0L)) *>
              IO.blocking(exchange.close())
          case "/process-request-entity" =>
            // We wait for the entire request to arrive before sending a response. That's how servers normally behave.
            Stream
              .eval(IO.blocking(exchange.getRequestBody.read()))
              .repeat
              .takeWhile(_ =!= -1)
              .compile
              .drain *>
              IO.blocking(exchange.sendResponseHeaders(204, 0L)) *>
              IO.blocking(exchange.close())
          case _ =>
            IO.blocking(exchange.sendResponseHeaders(404, -1)) *>
              IO.blocking(exchange.close())
        }
      case _ =>
        IO.blocking(exchange.sendResponseHeaders(404, -1)) *>
          IO.blocking(exchange.close())
    }
    io.start.unsafeRunAndForget()
  }

  val server = resourceSuiteFixture("http", ServerScaffold[IO](2, false, testHandler))
  val secureServer = resourceSuiteFixture("https", ServerScaffold[IO](1, true, testHandler))
}

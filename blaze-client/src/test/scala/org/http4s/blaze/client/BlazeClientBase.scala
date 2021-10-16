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
import javax.net.ssl.SSLContext
import javax.servlet.ServletOutputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.http4s._
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.client.JettyScaffold
import org.http4s.client.testroutes.GetRoutes
import scala.concurrent.duration._

trait BlazeClientBase extends Http4sSuite {
  val tickWheel = new TickWheelExecutor(tick = 50.millis)

  def builder(
      maxConnectionsPerRequestKey: Int,
      maxTotalConnections: Int = 5,
      responseHeaderTimeout: Duration = 30.seconds,
      requestTimeout: Duration = 45.seconds,
      chunkBufferMaxSize: Int = 1024,
      sslContextOption: Option[SSLContext] = Some(bits.TrustingSslContext)
  ) = {
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

  private def testServlet =
    new HttpServlet {
      override def doGet(req: HttpServletRequest, srv: HttpServletResponse): Unit =
        req.getRequestURI match {
          case "/infinite" =>
            srv.setStatus(Status.Ok.code)
            fs2.Stream
              .emit[IO, String]("a" * 8 * 1024)
              .through(fs2.text.utf8EncodeC)
              .evalMap(chunk => IO(srv.getOutputStream.write(chunk.toArray)))
              .repeat
              .compile
              .drain
              .unsafeRunSync()

          case _ =>
            GetRoutes.getPaths.get(req.getRequestURI) match {
              case Some(response) =>
                val resp = response.unsafeRunSync()
                srv.setStatus(resp.status.code)
                resp.headers.foreach { h =>
                  srv.addHeader(h.name.toString, h.value)
                }

                val os: ServletOutputStream = srv.getOutputStream

                val writeBody: IO[Unit] = resp.body
                  .evalMap { byte =>
                    IO(os.write(Array(byte)))
                  }
                  .compile
                  .drain
                val flushOutputStream: IO[Unit] = IO(os.flush())
                (writeBody *> flushOutputStream).unsafeRunSync()

              case None => srv.sendError(404)
            }
        }

      override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit =
        req.getRequestURI match {
          case "/respond-and-close-immediately" =>
            // We don't consume the req.getInputStream (the request entity). That means that:
            // - The client may receive the response before sending the whole request
            // - Jetty will send a "Connection: close" header and a TCP FIN+ACK along with the response, closing the connection.
            resp.getOutputStream.print("a")
            resp.setStatus(Status.Ok.code)

          case "/respond-and-close-immediately-no-body" =>
            // We don't consume the req.getInputStream (the request entity). That means that:
            // - The client may receive the response before sending the whole request
            // - Jetty will send a "Connection: close" header and a TCP FIN+ACK along with the response, closing the connection.
            resp.setStatus(Status.Ok.code)
          case "/process-request-entity" =>
            // We wait for the entire request to arrive before sending a response. That's how servers normally behave.
            var result: Int = 0
            while (result != -1)
              result = req.getInputStream.read()
            resp.setStatus(Status.Ok.code)
        }

    }

  val jettyServer = resourceSuiteFixture("http", JettyScaffold[IO](2, false, testServlet))
  val jettySslServer = resourceSuiteFixture("https", JettyScaffold[IO](1, true, testServlet))
}

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

package org.http4s.client
package blaze

import cats.effect._
import cats.syntax.all._
import javax.net.ssl.SSLContext
import javax.servlet.ServletOutputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.http4s._
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.client.testroutes.GetRoutes
import scala.concurrent.duration._

trait BlazeClientBase extends Http4sSuite {
  val tickWheel = new TickWheelExecutor(tick = 50.millis)

  def mkClient(
      maxConnectionsPerRequestKey: Int,
      maxTotalConnections: Int = 5,
      responseHeaderTimeout: Duration = 30.seconds,
      requestTimeout: Duration = 45.seconds,
      chunkBufferMaxSize: Int = 1024,
      sslContextOption: Option[SSLContext] = Some(bits.TrustingSslContext)
  ) =
    BlazeClientBuilder[IO](munitExecutionContext)
      .withSslContextOption(sslContextOption)
      .withCheckEndpointAuthentication(false)
      .withResponseHeaderTimeout(responseHeaderTimeout)
      .withRequestTimeout(requestTimeout)
      .withMaxTotalConnections(maxTotalConnections)
      .withMaxConnectionsPerRequestKey(Function.const(maxConnectionsPerRequestKey))
      .withChunkBufferMaxSize(chunkBufferMaxSize)
      .withScheduler(scheduler = tickWheel)
      .resource

  private def testServlet =
    new HttpServlet {
      override def doGet(req: HttpServletRequest, srv: HttpServletResponse): Unit =
        GetRoutes.getPaths.get(req.getRequestURI) match {
          case Some(resp) =>
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

      override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
        resp.setStatus(Status.Ok.code)
        req.getInputStream.close()
      }
    }

  def jettyScaffold: FunFixture[(JettyScaffold, JettyScaffold)] =
    ResourceFixture(
      (JettyScaffold[IO](5, false, testServlet), JettyScaffold[IO](1, true, testServlet)).tupled)

}

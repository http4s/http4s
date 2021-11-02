/*
 * Copyright 2013 http4s.org
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
package servlet

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all._
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.{Server => EclipseServer}
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.http4s.dsl.io._
import org.http4s.server.DefaultServiceErrorHandler
import org.http4s.syntax.all._

import java.net.URL
import scala.concurrent.duration._
import scala.io.Source

class AsyncHttp4sServletSuite extends Http4sSuite {
  lazy val service = HttpRoutes
    .of[IO] {
      case GET -> Root / "simple" =>
        Ok("simple")
      case req @ POST -> Root / "echo" =>
        Ok(req.body)
      case GET -> Root / "shifted" =>
        // Wait for a bit to make sure we lose the race
        (IO.sleep(50.millis) *>
          Ok("shifted")).evalOn(munitExecutionContext)
    }
    .orNotFound

  val servletServer = ResourceFixture[Int](serverPortR)

  def get(serverPort: Int, path: String): IO[String] =
    IO.blocking[String](
      Source
        .fromURL(new URL(s"http://127.0.0.1:$serverPort/$path"))
        .getLines()
        .mkString)

  servletServer.test("AsyncHttp4sServlet handle GET requests") { server =>
    get(server, "simple").assertEquals("simple")
  }

  servletServer.test("AsyncHttp4sServlet handle POST requests") { server =>
    val contents = (1 to 14).map { i =>
      val number =
        scala.math.pow(2, i.toDouble).toInt - 1 //-1 for the end-of-line to make awk play nice
      s"$i $number ${"*".*(number)}\n"
    }.toList

    import org.asynchttpclient.Dsl._
    import org.asynchttpclient.Response

    Resource.make(IO(asyncHttpClient()))(c => IO(c.close())).use { client =>
      contents
        .traverse { content =>
          IO {
            client
              .preparePost(s"http://127.0.0.1:$server/echo")
              .setBody(content)
              .execute()
              .toCompletableFuture()
          }.flatMap { cf =>
            IO.async[Response] { cb =>
              IO.delay {
                val stage = cf.handle[Unit] {
                  case (response, null) => cb(Right(response))
                  case (_, t) => cb(Left(t))
                }
                Some(IO.delay(stage.cancel(false)).void)
              }
            }
          }.flatMap { response =>
            IO(response.getResponseBody())
          }
        }
        .assertEquals(contents)
    }
  }

  servletServer.test("AsyncHttp4sServlet work for shifted IO") { server =>
    get(server, "shifted").assertEquals("shifted")
  }

  lazy val serverPortR = for {
    dispatcher <- Dispatcher[IO]
    server <- Resource.make(IO(new EclipseServer))(server => IO(server.stop()))
    servlet = new AsyncHttp4sServlet[IO](
      service = service,
      dispatcher = dispatcher,
      servletIo = NonBlockingServletIo[IO](4096),
      serviceErrorHandler = DefaultServiceErrorHandler[IO]
    )
    port <- Resource.eval(IO {
      val connector =
        new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()))

      val context = new ServletContextHandler
      context.addServlet(new ServletHolder(servlet), "/*")

      server.addConnector(connector)
      server.setHandler(context)

      server.start()

      connector.getLocalPort
    })
  } yield port

}

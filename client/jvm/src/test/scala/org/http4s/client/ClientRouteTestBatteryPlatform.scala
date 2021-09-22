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

package org.http4s
package client

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.comcast.ip4s.Host
import com.comcast.ip4s.SocketAddress
import com.sun.net.httpserver._
import org.http4s.Response
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.testroutes.GetRoutes
import org.http4s.dsl.io._
import org.http4s.server.Server

import java.io.PrintWriter
import java.net.InetSocketAddress

trait ClientRouteTestBatteryPlatform extends Http4sClientDsl[IO] {

  def serverResource: Resource[IO, Server] = Resource
    .make {
      IO {
        val server = HttpServer.create()
        val context = server.createContext("/")
        context.setHandler { exchange =>
          (exchange.getRequestMethod match {
            case "GET" =>
              val path = exchange.getRequestURI.getPath
              path match {
                case "/request-splitting" =>
                  val status =
                    if (exchange.getRequestHeaders.containsKey("Evil"))
                      Status.InternalServerError.code
                    else
                      Status.Ok.code
                  IO.blocking {
                    exchange.sendResponseHeaders(status, -1L)
                    exchange.close()
                  }
                case _ =>
                  GetRoutes.getPaths.get(path) match {
                    case Some(r) =>
                      r.flatMap(renderResponse(exchange, _))
                    case None =>
                      IO.blocking {
                        exchange.sendResponseHeaders(404, -1L)
                        exchange.close()
                      }
                  }
              }
            case "POST" =>
              IO.blocking {
                exchange.sendResponseHeaders(200, 0L)
                val s = scala.io.Source.fromInputStream(exchange.getRequestBody).mkString
                val out = new PrintWriter(exchange.getResponseBody())
                out.print(s)
                out.flush()
                exchange.close()
              }
          }).start.unsafeRunAndForget()
        }

        server.bind(new InetSocketAddress("localhost", 0), 0)
        server.start()
        server
      }
    }(server => IO(server.stop(0)))
    .map { server =>
      new Server {
        override def address: SocketAddress[Host] =
          SocketAddress.fromInetSocketAddress(server.getAddress())
        override def isSecure: Boolean = false
      }
    }

  private def renderResponse(exchange: HttpExchange, resp: Response[IO]): IO[Unit] =
    IO(resp.headers.foreach { h =>
      if (h.name =!= headers.`Content-Length`.name)
        exchange.getResponseHeaders.add(h.name.toString, h.value)
    }) *>
      IO.blocking {
        // com.sun.net.httpserver warns on nocontent with a content lengt that is not -1
        val contentLength =
          if (resp.status.code == NoContent.code) -1L
          else resp.contentLength.getOrElse(0L)
        exchange.sendResponseHeaders(resp.status.code, contentLength)
      } *>
      resp.body
        .through(
          fs2.io.writeOutputStream[IO](IO.pure(exchange.getResponseBody), closeAfterUse = false))
        .compile
        .drain
        .guarantee(IO(exchange.close()))

}

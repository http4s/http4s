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
package jetty
package server

import cats.effect.ContextShift
import cats.effect.IO
import org.eclipse.jetty.ee8.servlet.ServletContextHandler
import org.eclipse.jetty.ee8.servlet.ServletHolder
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.http4s.dsl.io._
import org.http4s.server.DefaultServiceErrorHandler
import org.http4s.servlet.AsyncHttp4sServlet
import org.http4s.syntax.all._

object Issue454 {
  implicit val cs: ContextShift[IO] = Http4sSuite.TestContextShift

  // If the bug is not triggered right away, try increasing or
  // repeating the request. Also if you decrease the data size (to
  // say 32mb, the bug does not manifest so often, but the stack
  // trace is a bit different.
  private val insanelyHugeData = Array.ofDim[Byte](1024 * 1024 * 128)

  {
    var i = 0
    while (i < insanelyHugeData.length) {
      insanelyHugeData(i) = ('0' + i).toByte
      i = i + 1
    }
    insanelyHugeData(insanelyHugeData.length - 1) = '-' // end marker
  }

  def main(args: Array[String]): Unit = {
    val server = new Server

    val connector = new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()))
    connector.setPort(5555)

    val context = new ServletContextHandler
    context.setContextPath("/")
    context.addServlet(new ServletHolder(servlet), "/")

    server.addConnector(connector)
    server.setHandler(context)

    server.start()
  }

  val servlet = new AsyncHttp4sServlet[IO](
    service = HttpRoutes
      .of[IO] { case GET -> Root =>
        Ok(insanelyHugeData)
      }
      .orNotFound,
    servletIo = org.http4s.servlet.NonBlockingServletIo(4096),
    serviceErrorHandler = DefaultServiceErrorHandler,
  )
}

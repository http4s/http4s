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

package org.http4s.servlet

import cats.effect.IO
import cats.effect.Resource
import org.eclipse.jetty.ee8.servlet.ServletContextHandler
import org.eclipse.jetty.ee8.servlet.ServletHolder
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.{Server => EclipseServer}

import javax.servlet.Servlet

object TestEclipseServer {

  def apply(
      servlet: Servlet,
      contextPath: String = "/",
      servletPath: String = "/*",
  ): Resource[IO, Int /* port */ ] =
    Resource
      .make(IO(new EclipseServer))(server => IO(server.stop()))
      .evalMap { server =>
        IO {
          val connector =
            new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()))

          val context = new ServletContextHandler
          context.addServlet(new ServletHolder(servlet), servletPath)
          context.setContextPath(contextPath)

          server.addConnector(connector)
          server.setHandler(context)

          server.start()

          connector.getLocalPort
        }
      }

}

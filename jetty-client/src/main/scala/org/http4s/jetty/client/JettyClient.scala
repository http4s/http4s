/*
 * Copyright 2018 http4s.org
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
package client

import cats.effect._
import cats.syntax.all._
import fs2._
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.{Request => JettyRequest}
import org.eclipse.jetty.http.{HttpVersion => JHttpVersion}
import org.http4s.client.Client
import org.log4s.Logger
import org.log4s.getLogger

object JettyClient {
  private val logger: Logger = getLogger

  def allocate[F[_]](
      client: HttpClient = defaultHttpClient()
  )(implicit F: ConcurrentEffect[F], CS: ContextShift[F]): F[(Client[F], F[Unit])] = {
    val acquire = F
      .pure(client)
      .flatTap(client => F.delay(client.start()))
      .map(client =>
        Client[F] { req =>
          Resource.suspend(F.asyncF[Resource[F, Response[F]]] { cb =>
            F.bracket(StreamRequestContent()) { dcp =>
              (for {
                jReq <- F.catchNonFatal(toJettyRequest(client, req, dcp))
                rl <- ResponseListener(cb)
                _ <- F.delay(jReq.send(rl))
                _ <- dcp.write(req.body)
              } yield ()).recover { case e =>
                cb(Left(e))
              }
            } { dcp =>
              F.delay(dcp.close())
            }
          })
        }
      )
    val dispose = F
      .delay(client.stop())
      .handleErrorWith(t => F.delay(logger.error(t)("Unable to shut down Jetty client")))
    acquire.map((_, dispose))
  }

  def resource[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient = defaultHttpClient()
  ): Resource[F, Client[F]] =
    Resource(allocate[F](client))

  def stream[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient = defaultHttpClient()
  ): Stream[F, Client[F]] =
    Stream.resource(resource(client))

  def defaultHttpClient(): HttpClient = {
    val c = new HttpClient()
    c.setFollowRedirects(false)
    c.setDefaultRequestContentType(null)
    c
  }

  private def toJettyRequest[F[_]](
      client: HttpClient,
      request: Request[F],
      dcp: StreamRequestContent[F],
  ): JettyRequest = {
    val jReq = client
      .newRequest(request.uri.toString)
      .method(request.method.name)
      .version(
        request.httpVersion match {
          case HttpVersion.`HTTP/1.1` => JHttpVersion.HTTP_1_1
          case HttpVersion.`HTTP/2` => JHttpVersion.HTTP_2
          case HttpVersion.`HTTP/1.0` => JHttpVersion.HTTP_1_0
          case _ => JHttpVersion.HTTP_1_1
        }
      )
    jReq.headers { jettyHeaders =>
      for (h <- request.headers.headers if h.isNameValid)
        jettyHeaders.add(h.name.toString, h.value)
    }
    jReq.body(dcp)
  }
}

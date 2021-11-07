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

import cats.effect.Async
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.std.Dispatcher
import cats.effect.syntax.all._
import cats.syntax.all._
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.SocketAddress
import com.sun.net.httpserver._
import org.http4s
import org.http4s.HttpRoutes
import org.http4s.Method

import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import scala.annotation.nowarn
import scala.collection.JavaConverters._

object ServerScaffold {
  def apply[F[_]](num: Int, secure: Boolean, routes: HttpRoutes[F])(implicit
      F: Async[F]
  ): Resource[F, ServerScaffold] =
    Dispatcher[F].flatMap { dispatcher =>
      val handler: HttpHandler = { exchange =>
        val run = for {
          method <- Method.fromString(exchange.getRequestMethod()).liftTo[F]
          uri <- http4s.Uri.fromString(exchange.getRequestURI().toString()).liftTo[F]
          headers = http4s.Headers(
            exchange.getRequestHeaders().asScala.toList.flatMap { case (k, vs) =>
              vs.asScala.toList.map { v =>
                (k -> v): http4s.Header.ToRaw
              }
            }
          ): @nowarn("cat=deprecation")
          body = fs2.io.readInputStream(exchange.getRequestBody().pure[F], 8192)
          request = http4s.Request(method, uri, headers = headers, body = body)
          response <- routes.run(request).value
          _ <- response.fold(F.unit) { res =>
            F.delay {
              res.headers.foreach { h =>
                if (h.name =!= http4s.headers.`Content-Length`.name)
                  exchange.getResponseHeaders.add(h.name.toString, h.value)
              }
            } *> F.blocking {
              // com.sun.net.httpserver warns on nocontent with a content lengt that is not -1
              val contentLength =
                if (res.status.code == http4s.Status.NoContent.code) -1L
                else res.contentLength.getOrElse(0L)
              exchange.sendResponseHeaders(res.status.code, contentLength)
            } *>
              res.body
                .through(
                  fs2.io
                    .writeOutputStream[F](exchange.getResponseBody.pure[F])
                )
                .compile
                .drain
          }
        } yield ()
        dispatcher.unsafeRunAndForget(run.guarantee(F.blocking(exchange.close())))
      }

      apply(num, secure, handler)
    }

  def apply[F[_]](num: Int, secure: Boolean, testHandler: HttpHandler)(implicit
      F: Sync[F]
  ): Resource[F, ServerScaffold] =
    Resource.make(F.delay {
      val scaffold = new ServerScaffold(num, secure)
      scaffold.startServers(testHandler)
    })(s => F.delay(s.stopServers()))
}

class ServerScaffold private (num: Int, secure: Boolean) {
  private var servers = Vector.empty[HttpServer]
  var addresses = Vector.empty[SocketAddress[IpAddress]]

  def startServers(testHandler: HttpHandler): this.type = {
    val res = (0 until num).map { _ =>
      val server: HttpServer =
        if (secure) {

          val ksStream = this.getClass.getResourceAsStream("/server.jks")
          val ks = KeyStore.getInstance("JKS")
          ks.load(ksStream, "password".toCharArray)
          ksStream.close()

          val kmf = KeyManagerFactory.getInstance(
            Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
              .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
          )

          kmf.init(ks, "secure".toCharArray)

          val sslContext = SSLContext.getInstance("TLS")
          sslContext.init(kmf.getKeyManagers, null, null)

          val server = HttpsServer.create()
          server.setHttpsConfigurator(new HttpsConfigurator(sslContext))

          server
        } else HttpServer.create()

      val context = server.createContext("/")
      context.setHandler(testHandler)

      server.bind(new InetSocketAddress("localhost", 0), 0)
      server.start()
      (server.getAddress, server)
    }.toVector

    servers = res.map(_._2)
    addresses = res.map(_._1).map(SocketAddress.fromInetSocketAddress)

    this
  }

  def stopServers(): Unit = servers.foreach(_.stop(10))
}

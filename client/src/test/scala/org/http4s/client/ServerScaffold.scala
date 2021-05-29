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

import cats.effect.{Resource, Sync}
import com.sun.net.httpserver._
import java.net.InetSocketAddress
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

object ServerScaffold {
  def apply[F[_]](num: Int, secure: Boolean, testHandler: HttpHandler)(implicit
      F: Sync[F]): Resource[F, ServerScaffold] =
    Resource.make(F.delay {
      val scaffold = new ServerScaffold(num, secure)
      scaffold.startServers(testHandler)
    })(s => F.delay(s.stopServers()))
}

class ServerScaffold private (num: Int, secure: Boolean) {
  private var servers = Vector.empty[HttpServer]
  var addresses = Vector.empty[InetSocketAddress]

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
              .getOrElse(KeyManagerFactory.getDefaultAlgorithm))

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
    addresses = res.map(_._1)

    this
  }

  def stopServers(): Unit = servers.foreach(_.stop(10))
}

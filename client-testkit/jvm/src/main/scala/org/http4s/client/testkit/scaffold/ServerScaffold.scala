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

package org.http4s.client.testkit.scaffold

import cats.effect.Async
import cats.effect.Resource
import cats.effect.Sync
import cats.effect.std.Dispatcher
import cats.syntax.all._
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.SocketAddress
import io.netty.channel.ChannelInboundHandler
import io.netty.handler.codec.http.HttpMethod
import org.http4s.HttpRoutes

import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

private[http4s] class ServerScaffold[F[_]] private (val servers: Vector[TestServer[F]]) {
  def addresses: Vector[SocketAddress[IpAddress]] = servers.map(_.localAddress)
}

private[http4s] object ServerScaffold {

  // high-level API
  def apply[F[_]](num: Int, secure: Boolean, routes: HttpRoutes[F])(implicit
      F: Async[F]
  ): Resource[F, ServerScaffold[F]] =
    for {
      dispatcher <- Dispatcher.parallel[F]
      scaffold <- apply(num, secure, RoutesToNettyAdapter[F](routes, dispatcher))
    } yield scaffold

  // mid-level API
  def apply[F[_]](num: Int, secure: Boolean, handlers: Map[(HttpMethod, String), Handler])(implicit
      F: Async[F]
  ): Resource[F, ServerScaffold[F]] =
    apply[F](num, secure, HandlersToNettyAdapter(handlers))

  // low-level API
  def apply[F[_]](num: Int, secure: Boolean, makeHandler: F[ChannelInboundHandler])(implicit
      F: Async[F]
  ): Resource[F, ServerScaffold[F]] =
    for {
      dispatcher <- Dispatcher.parallel[F]
      maybeSsl <-
        if (secure) Resource.eval[F, SSLContext](makeSslContext[F]).map(Some(_))
        else Resource.pure[F, Option[SSLContext]](None)
      servers <- NettyTestServer[F](port = 0, makeHandler, maybeSsl, dispatcher).replicateA(num)
    } yield new ServerScaffold(servers.toVector)

  private def makeSslContext[F[_]](implicit F: Sync[F]): F[SSLContext] = F.delay {
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
    sslContext
  }
}

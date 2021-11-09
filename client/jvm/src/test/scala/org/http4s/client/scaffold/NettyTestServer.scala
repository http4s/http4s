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

package org.http4s.client.scaffold

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.effect.{Ref, Resource}
import cats.implicits._
import com.comcast.ip4s.{IpAddress, Port, SocketAddress}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.handler.codec.http._
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.handler.ssl.SslHandler
import org.log4s.getLogger

import java.net.{InetAddress, InetSocketAddress}
import javax.net.ssl.SSLContext

trait TestServer[F[_]] {
  def localAddress: SocketAddress[IpAddress]
  def establishedConnections: F[Long]
  def resetEstablishedConnections: F[Unit]
}

class NettyTestServer[F[_]](
    establishedConnectionsRef: Ref[F, Long],
    val localAddress: SocketAddress[IpAddress]
) extends TestServer[F] {
  def establishedConnections: F[Long] = establishedConnectionsRef.get
  def resetEstablishedConnections: F[Unit] = establishedConnectionsRef.set(0L)
}

object NettyTestServer {
  private val logger = getLogger(this.getClass)

  def apply[F[_]: Async](
      port: Int,
      makeHandler: F[ChannelInboundHandler],
      sslContext: Option[SSLContext],
      dispatcher: Dispatcher[F]
  ): Resource[F, NettyTestServer[F]] = for {
    bossGroup <- nioEventLoopGroup[F]
    workerGroup <- nioEventLoopGroup[F]
    establishedConnections <- Resource.eval(Ref[F].of(0L))
    bootstrap = new ServerBootstrap()
      .group(bossGroup, workerGroup)
      .channelFactory(new ChannelFactory[NioServerSocketChannel] {
        override def newChannel(): NioServerSocketChannel = new NioServerSocketChannel()
      })
      .handler(new LoggingHandler(LogLevel.INFO))
      .childHandler(new ChannelInitializer[NioSocketChannel]() {
        def initChannel(ch: NioSocketChannel): Unit = {
          logger.trace(s"Accepted new connection from [${ch.remoteAddress()}].")
          establishedConnections.update(_ + 1)
          sslContext.foreach { sslContext =>
            val engine = sslContext.createSSLEngine()
            engine.setUseClientMode(false)
            ch.pipeline().addLast(new SslHandler(engine))
          }
          ch.pipeline()
            .addLast(new HttpRequestDecoder())
            .addLast(new HttpResponseEncoder())
            .addLast(dispatcher.unsafeRunSync(makeHandler))
        }
      })
    channel <- server[F](bootstrap, port)
    // TODO refactor
    inetSocketAddress = channel.localAddress().asInstanceOf[InetSocketAddress]
    ip <- Resource.eval(
      IpAddress
        .fromString(inetSocketAddress.getAddress.getHostAddress)
        .liftTo[F](new Exception(s"Invalid IP: [${inetSocketAddress.getAddress.toString}]")))
    port <- Resource.eval(
      Port
        .fromInt(inetSocketAddress.getPort)
        .liftTo[F](new Exception(s"Invalid port: [${inetSocketAddress.getPort}]")))
  } yield new NettyTestServer(establishedConnections, SocketAddress(ip, port))

  private def nioEventLoopGroup[F[_]](implicit F: Async[F]): Resource[F, NioEventLoopGroup] =
    Resource.make[F, NioEventLoopGroup](F.delay(new NioEventLoopGroup()))(el =>
      F.delay(el.shutdownGracefully()))

  private def server[F[_]](bootstrap: ServerBootstrap, port: Int)(implicit
      F: Async[F]): Resource[F, Channel] =
    Resource.make[F, Channel](
      F.delay(bootstrap.bind(InetAddress.getLocalHost(), port)).liftToFWithChannel
    )(channel => F.delay(channel.close(new DefaultChannelPromise(channel))).liftToF)

}

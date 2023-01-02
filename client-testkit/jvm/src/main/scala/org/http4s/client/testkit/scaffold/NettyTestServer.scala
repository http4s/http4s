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

import cats.effect.Ref
import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.implicits._
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslHandler
import org.http4s.Uri

import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

private[http4s] trait TestServer[F[_]] {
  def localAddress: SocketAddress[IpAddress]
  def establishedConnections: F[Long]
  def resetEstablishedConnections: F[Unit]
  def secure: Boolean
  def uri: Uri = Uri.unsafeFromString(s"${if (secure) "https" else "http"}://$localAddress")
}

private[http4s] class NettyTestServer[F[_]](
    establishedConnectionsRef: Ref[F, Long],
    val localAddress: SocketAddress[IpAddress],
    val secure: Boolean,
) extends TestServer[F] {
  def establishedConnections: F[Long] = establishedConnectionsRef.get
  def resetEstablishedConnections: F[Unit] = establishedConnectionsRef.set(0L)
}

private[http4s] object NettyTestServer {
  private val logger = org.http4s.Platform.loggerFactory.getLoggerFromClass(this.getClass)

  def apply[F[_]: Async](
      port: Int,
      makeHandler: F[ChannelInboundHandler],
      sslContext: Option[SSLContext],
      dispatcher: Dispatcher[F],
  ): Resource[F, NettyTestServer[F]] = for {
    bossGroup <- nioEventLoopGroup[F]
    workerGroup <- nioEventLoopGroup[F]
    establishedConnections <- Resource.eval(Ref[F].of(0L))
    bootstrap = new ServerBootstrap()
      .group(bossGroup, workerGroup)
      .channelFactory(new ChannelFactory[NioServerSocketChannel] {
        override def newChannel(): NioServerSocketChannel = new NioServerSocketChannel()
      })
      .handler(new LoggingHandler())
      .childHandler(new ChannelInitializer[NioSocketChannel]() {
        def initChannel(ch: NioSocketChannel): Unit = {
          logger.trace(s"Accepted new connection from [${ch.remoteAddress()}].").unsafeRunSync()
          dispatcher.unsafeRunSync(establishedConnections.update(_ + 1))
          sslContext.foreach { sslContext =>
            val engine = sslContext.createSSLEngine()
            engine.setUseClientMode(false)
            ch.pipeline().addLast(new SslHandler(engine))
          }
          ch.pipeline()
            .addLast(new HttpRequestDecoder())
            .addLast(new HttpResponseEncoder())
            .addLast(dispatcher.unsafeRunSync(makeHandler))
          ()
        }
      })
    channel <- server[F](bootstrap, port)
    localInetSocketAddress = channel.localAddress().asInstanceOf[InetSocketAddress]
    localAddress <- Resource.eval(toSocketAddress(localInetSocketAddress).liftTo[F])
  } yield new NettyTestServer(establishedConnections, localAddress, secure = sslContext.isDefined)

  private def nioEventLoopGroup[F[_]](implicit F: Async[F]): Resource[F, NioEventLoopGroup] =
    Resource.make(
      F.delay(new NioEventLoopGroup())
    )(el => F.delay(el.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)).liftToF)

  private def server[F[_]](bootstrap: ServerBootstrap, port: Int)(implicit
      F: Async[F]
  ): Resource[F, Channel] =
    Resource.make[F, Channel](
      F.delay(bootstrap.bind(InetAddress.getLoopbackAddress(), port)).liftToFWithChannel
    )(channel => F.delay(channel.close(new DefaultChannelPromise(channel))).liftToF)

  private def toSocketAddress(
      addr: InetSocketAddress
  ): Either[Exception, SocketAddress[IpAddress]] =
    for {
      ip <- IpAddress
        .fromString(addr.getAddress.getHostAddress)
        .toRight(new Exception(s"Invalid IP: [${addr.getAddress.toString}]"))
      port <- Port
        .fromInt(addr.getPort)
        .toRight(new Exception(s"Invalid port: [${addr.getPort}]"))
    } yield SocketAddress(ip, port)
}

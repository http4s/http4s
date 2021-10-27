package org.http4s.client.scaffold

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import cats.effect.{Ref, Resource}
import org.log4s.getLogger
import javax.net.ssl.SSLContext
import io.netty.handler.ssl.SslHandler
import cats.effect.kernel.Async
import cats.implicits._
import com.comcast.ip4s.{SocketAddress, IpAddress}
import java.net.InetAddress

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

  def apply[F[_]: Async](port: Int, 
    handler: ChannelInboundHandler, 
    sslContext: Option[SSLContext]
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
          sslContext.foreach(sslContext => 
            ch.pipeline().addLast(new SslHandler(sslContext.createSSLEngine()))
          )
          ch.pipeline()
            .addLast(new HttpRequestDecoder())
            .addLast(new HttpResponseEncoder())
            .addLast(handler)
        }
      })
    channel <- server[F](bootstrap, port)
    address <- Resource.eval(
      SocketAddress.fromStringIp(channel.localAddress().toString())
        .liftTo[F](new Exception("invalid address"))
    )
  } yield new NettyTestServer(establishedConnections, address)

  private def nioEventLoopGroup[F[_]](implicit F: Async[F]): Resource[F, NioEventLoopGroup] =
    Resource.make[F, NioEventLoopGroup](F.delay(new NioEventLoopGroup()))(el =>
      F.delay(el.shutdownGracefully()))

  private def server[F[_]](bootstrap: ServerBootstrap, port: Int)(implicit F: Async[F]): Resource[F, Channel] =
    Resource.make[F, Channel](
      F.async((callback: Either[Throwable, Channel] => Unit) =>
        F.delay(
          bootstrap
            .bind(InetAddress.getLocalHost(), port)
            .addListener((f: ChannelFuture) => callback(Right(f.channel())))
        ).as(None))
    )(channel =>
      F.async { (callback: Either[Throwable, Unit] => Unit) =>
        F.delay {
          val p = new DefaultChannelPromise(channel)
          channel.close(p).addListener((f: ChannelFuture) => callback(Right(())))
        }.as(None)
      })

}

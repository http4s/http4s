package org.http4s.miku

import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import cats.effect.{Effect, IO}
import com.typesafe.netty.HandlerPublisher
import com.typesafe.netty.http.HttpStreamsServerHandler
import fs2.Pipe
import fs2.interop.reactivestreams._
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{
  HttpContentDecompressor,
  HttpRequestDecoder,
  HttpResponseEncoder
}
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.IdleStateHandler
import org.http4s.HttpService
import org.http4s.server._
import org.http4s.util.bug
import org.log4s.getLogger

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

sealed trait NettyTransport
case object Jdk extends NettyTransport
case object Native extends NettyTransport

/** Netty server builder.
  *
  *
  * @param httpService
  * @param socketAddress
  * @param idleTimeout
  * @param maxInitialLineLength The maximum length of the initial line. This effectively restricts the maximum length of a URL that the server will
  *                             accept, the initial line consists of the method (3-7 characters), the URL, and the HTTP version (8 characters),
  *                             including typical whitespace, the maximum URL length will be this number - 18.
  * @param maxHeaderSize The maximum length of the HTTP headers. The most common effect of this is a restriction in cookie length, including
  *                      number of cookies and size of cookie values.
  * @param maxChunkSize The maximum length of body bytes that Netty will read into memory at a time.
  *                     This is used in many ways.  Note that this setting has no relation to HTTP chunked transfer encoding - Netty will
  *                     read "chunks", that is, byte buffers worth of content at a time and pass it to Play, regardless of whether the body
  *                     is using HTTP chunked transfer encoding.  A single HTTP chunk could span multiple Netty chunks if it exceeds this.
                        A body that is not HTTP chunked will span multiple Netty chunks if it exceeds this or if no content length is
                        specified. This only controls the maximum length of the Netty chunk byte buffers.
  * @param sslBits The SSL information.
  * @param serviceErrorHandler
  * @param transport
  * @param ec
  * @param banner
  * @param F
  * @tparam F
  */
class MikuBuilder[F[_]](
    httpService: HttpService[F],
    socketAddress: InetSocketAddress,
    idleTimeout: Duration,
    maxInitialLineLength: Int,
    maxHeaderSize: Int,
    maxChunkSize: Int,
    sslBits: Option[MikuSSLConfig],
    serviceErrorHandler: ServiceErrorHandler[F],
    transport: NettyTransport,
    ec: ExecutionContext,
    enableWebsockets: Boolean,
    banner: immutable.Seq[String]
)(implicit F: Effect[F])
    extends ServerBuilder[F]
    with IdleTimeoutSupport[F]
    with SSLKeyStoreSupport[F]
    with SSLContextSupport[F]
    with WebSocketSupport[F] {
  implicit val e = ec
  private val logger = getLogger

  type Self = MikuBuilder[F]

  protected def copy(
      httpService: HttpService[F] = httpService,
      socketAddress: InetSocketAddress = socketAddress,
      idleTimeout: Duration = Duration.Inf,
      maxInitialLineLength: Int = maxInitialLineLength,
      maxHeaderSize: Int = maxHeaderSize,
      maxChunkSize: Int = maxChunkSize,
      sslBits: Option[MikuSSLConfig] = sslBits,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      ec: ExecutionContext = ec,
      enableWebsockets: Boolean = enableWebsockets,
      banner: immutable.Seq[String] = banner,
      transport: NettyTransport = transport
  ): MikuBuilder[F] =
    new MikuBuilder[F](
      httpService,
      socketAddress,
      idleTimeout,
      maxInitialLineLength,
      maxHeaderSize,
      maxChunkSize,
      sslBits,
      serviceErrorHandler,
      transport,
      ec,
      enableWebsockets,
      banner
    )

  def bindSocketAddress(socketAddress: InetSocketAddress): MikuBuilder[F] =
    copy(socketAddress = socketAddress)

  def withExecutionContext(executionContext: ExecutionContext): MikuBuilder[F] =
    copy(ec = executionContext)

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): MikuBuilder[F] =
    copy(serviceErrorHandler = serviceErrorHandler)

  //Todo: Router-hack thingy
  def mountService(service: HttpService[F], prefix: String): MikuBuilder[F] =
    copy(httpService = service)

  def start: F[Server[F]] = F.delay {
    val eventLoop: MultithreadEventLoopGroup = transport match {
      case Jdk => new NioEventLoopGroup()
      case Native =>
        if (Epoll.isAvailable)
          new EpollEventLoopGroup()
        else {
          logger.info("Falling back to NIO EventLoopGroup")
          new NioEventLoopGroup()
        }
    }

    val allChannels: DefaultChannelGroup = new DefaultChannelGroup(eventLoop.next())

    val (_, boundAddress) = startNetty(eventLoop, allChannels)
    val server = new Server[F] {
      def shutdown: F[Unit] = F.delay {
        // First, close all opened sockets
        allChannels.close().awaitUninterruptibly()
        // Now shutdown the event loop
        eventLoop.shutdownGracefully()

        logger.info("Shut down gracefully :)")
      }

      def onShutdown(f: => Unit): this.type = {
        Runtime.getRuntime.addShutdownHook(new Thread(() => f))
        this
      }

      def address: InetSocketAddress = boundAddress

      def isSecure: Boolean = sslBits.isDefined
    }
    banner.foreach(logger.info(_))
    logger.info(s"༼ つ ◕_◕ ༽つ Started Miku(Netty) Server at ${server.baseUri} ༼ つ ◕_◕ ༽つ")
    server
  }

  def withBanner(banner: immutable.Seq[String]): MikuBuilder[F] =
    copy(banner = banner)

  def withIdleTimeout(idleTimeout: Duration): MikuBuilder[F] =
    copy(idleTimeout = idleTimeout)

  def withSSL(
      keyStore: SSLKeyStoreSupport.StoreInfo,
      keyManagerPassword: String,
      protocol: String,
      trustStore: Option[SSLKeyStoreSupport.StoreInfo],
      clientAuth: Boolean
  ): MikuBuilder[F] = {
    val auth = if (clientAuth) ClientAuthRequired else NoClientAuth
    val bits = MikuKeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, auth)
    copy(sslBits = Some(bits))
  }

  def withSSLContext(sslContext: SSLContext, clientAuth: Boolean): MikuBuilder[F] = {
    val auth = if (clientAuth) ClientAuthRequired else NoClientAuth
    copy(sslBits = Some(MikuSSLContextBits(sslContext, auth)))
  }

  def withWebSockets(enableWebsockets: Boolean): MikuBuilder[F] =
    copy(enableWebsockets = enableWebsockets)

  protected[this] def newRequestHandler(): ChannelInboundHandler =
    if (enableWebsockets)
      MikuHandler.websocket[F](httpService, serviceErrorHandler)
    else
      MikuHandler.default[F](httpService, serviceErrorHandler)

  /** Return our SSL context.
    *
    */
  private[this] def getContext(): Option[(SSLContext, ClientAuth)] = sslBits.map {
    case MikuKeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth) =>
      val ksStream = new FileInputStream(keyStore.path)
      val ks = KeyStore.getInstance("JKS")
      ks.load(ksStream, keyStore.password.toCharArray)
      ksStream.close()

      val tmf = trustStore.map { auth =>
        val ksStream = new FileInputStream(auth.path)

        val ks = KeyStore.getInstance("JKS")
        ks.load(ksStream, auth.password.toCharArray)
        ksStream.close()

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

        tmf.init(ks)
        tmf.getTrustManagers
      }

      val kmf = KeyManagerFactory.getInstance(
        Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
          .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
      )

      kmf.init(ks, keyManagerPassword.toCharArray)

      val context = SSLContext.getInstance(protocol)
      context.init(kmf.getKeyManagers, tmf.orNull, null)

      (context, clientAuth)

    case MikuSSLContextBits(context, clientAuth) =>
      (context, clientAuth)
  }

  /** A stream transformation that registers our channels and
    * adds the necessary codecs and transport handlers
    */
  def channelPipe(
      eventLoop: MultithreadEventLoopGroup,
      allChannels: DefaultChannelGroup): Pipe[F, Channel, Unit] = { s =>
    s.evalMap { connChannel =>
      F.delay {

        // Setup the channel for explicit reads
        connChannel
          .config()
          .setOption(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE)

        val pipeline = connChannel.pipeline()
        getContext() match {
          case Some((ctx, auth)) => //Ignore client authentication for now
            val engine = ctx.createSSLEngine()
            engine.setUseClientMode(false)
            auth match {
              case NoClientAuth => ()
              case ClientAuthRequired =>
                engine.setNeedClientAuth(true)
              case ClientAuthOptional =>
                engine.setWantClientAuth(true)
            }
            pipeline.addLast("ssl", new SslHandler(engine))

          case None => //Do nothing
        }

        // Netty HTTP decoders/encoders/etc
        pipeline.addLast(
          "decoder",
          new HttpRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize))
        pipeline.addLast("encoder", new HttpResponseEncoder())
        pipeline.addLast("decompressor", new HttpContentDecompressor())

        idleTimeout match {
          case Duration.Inf => //Do nothing
          case Duration(timeout, timeUnit) =>
            pipeline.addLast("idle-handler", new IdleStateHandler(0, 0, timeout, timeUnit))

        }

        val requestHandler = newRequestHandler()

        // Use the streams handler to close off the connection.
        pipeline.addLast(
          "http-handler",
          new HttpStreamsServerHandler(Seq[ChannelHandler](requestHandler).asJava))

        pipeline.addLast("request-handler", requestHandler)

        // And finally, register the channel with the event loop
        val childChannelEventLoop = eventLoop.next()
        childChannelEventLoop.register(connChannel)
        allChannels.add(connChannel)
        ()
      }
    }
  }

  private def bootstrapNative(
      serverChannelEventLoop: EventLoopGroup,
      channelPublisher: HandlerPublisher[Channel],
      address: InetSocketAddress): Bootstrap =
    new Bootstrap()
      .channel(classOf[EpollServerSocketChannel])
      .group(serverChannelEventLoop)
      .option(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE) // publisher does ctx.read()
      .handler(channelPublisher)
      .localAddress(address)

  private def bootstrapNIO(
      serverChannelEventLoop: EventLoopGroup,
      channelPublisher: HandlerPublisher[Channel],
      address: InetSocketAddress): Bootstrap =
    new Bootstrap()
      .channel(classOf[NioServerSocketChannel])
      .group(serverChannelEventLoop)
      .option(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE) // publisher does ctx.read()
      .handler(channelPublisher)
      .localAddress(address)

  protected def getBootstrap(
      serverChannelEventLoop: EventLoopGroup,
      channelPublisher: HandlerPublisher[Channel],
      address: InetSocketAddress): Bootstrap = transport match {
    case Jdk =>
      bootstrapNIO(serverChannelEventLoop, channelPublisher, address)
    case Native =>
      if (Epoll.isAvailable)
        bootstrapNative(serverChannelEventLoop, channelPublisher, address)
      else {
        logger.info(
          "Native transport not available. Falling back to nio2 transport. Please use .withTransport(Jdk)")
        bootstrapNIO(serverChannelEventLoop, channelPublisher, address)
      }
  }

  /** Bind our server to our address and create the channel handler stream, which we must
    * bind the handlers to.
    */
  def bind(
      eventLoop: MultithreadEventLoopGroup,
      allChannels: DefaultChannelGroup,
      address: InetSocketAddress): (Channel, fs2.Stream[F, Channel]) = {
    val serverChannelEventLoop = eventLoop.next

    // Watches for channel events, and pushes them through a reactive streams publisher.
    val channelPublisher =
      new HandlerPublisher(serverChannelEventLoop, classOf[Channel])

    val bootstrap: Bootstrap = getBootstrap(serverChannelEventLoop, channelPublisher, address)

    //Actually bind the server toe ht address
    val channel = bootstrap.bind.await().channel()
    allChannels.add(channel)

    (channel, channelPublisher.toStream[F]())
  }

  private def startNetty(
      eventLoop: MultithreadEventLoopGroup,
      allChannels: DefaultChannelGroup
  ): (Channel, InetSocketAddress) = {

    //Resolve address
    val resolvedAddress = new InetSocketAddress(socketAddress.getHostName, socketAddress.getPort)
    val (serverChannel, channelSource) = bind(eventLoop, allChannels, resolvedAddress)
    F.runAsync(
        channelSource
          .through(channelPipe(eventLoop, allChannels))
          .compile
          .drain
      )(_.fold(IO.raiseError, _ => IO.unit)) //This shouldn't ever throw an error in the first place.
      .unsafeRunSync()
    //Cast downcast here is unfortunately necessary... because netty.
    val boundAddress = serverChannel.localAddress().asInstanceOf[InetSocketAddress]
    if (boundAddress == null) {
      val e = bug("no bound address")
      logger.error(e)("Error in server initialization")
      throw e
    }
    (serverChannel, boundAddress)
  }

}

object MikuBuilder {
  def apply[F[_]: Effect] =
    new MikuBuilder[F](
      httpService = HttpService.empty[F],
      socketAddress = ServerBuilder.DefaultSocketAddress,
      idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
      maxInitialLineLength = 4096,
      maxHeaderSize = 8192,
      maxChunkSize = 8192,
      sslBits = None,
      serviceErrorHandler = DefaultServiceErrorHandler[F],
      transport = Native,
      ec = ExecutionContext.global,
      enableWebsockets = false,
      banner = MikuBanner
    )
  ServerBuilder.DefaultSocketAddress.isUnresolved

  val MikuBanner =
    """　　　　　　　 ┌--,-'￣￣￣ー―-､／＼
      |　　　　　　,イ |／: : : : :〈〈二ゝ＼
      |　　　　　/: /: : : : : : : : : ＼|::＼
      |　　　　/: :イ: :∧: : ∧: : : : : :|:  :ﾍ
      |　　　 /: : |: :/_\::/_＼: : : : :|: : :ﾍ
      |　　　/: : :|: /　 \/　　＼: : ___|: : : ﾍ
      |　　 |:: : |::/ o 　  　o |:::/:::|: : : :ﾍ
      |　　 |: : : \/\  |￣￣￣| ||:/ーー|: : : :|
      |　　 |: : : |ヽ＼|＿＿＿|_/|/_|   |: : : :|
      |　　 |: : : |　＾_/|_/\_/\　　　  |: : : :|
      |　　 |: : : |　　( | TT ￣|ヽ　　 |: : : :|
      |　　 |: : : |　　ﾄ-| ||　 | ﾍ　　 |: : : :|
      |　　 |: : : | 　 L/| ||   |  ﾍ　  |: : : :|
      |　　  ﾍ : : |　　  |__,､__ﾍ--´　  |: : : :|
      |　　　 ﾍ : :|　  く////\\\\>ヽJ　 |: : : :/
      |　　　　ﾍ: /　　 　ﾄ-| .ﾄ-|　　   |／￣＼/
      |　　　　 \/　　 　 |_|　|_|　　　
      |  _   _   _        _ _
      | | |_| |_| |_ _ __| | | ___
      | | ' \  _|  _| '_ \_  _(_-<
      | |_||_\__|\__| .__/ |_|/__/
      |             |_|
      |             """.stripMargin.split("\n").toList
}

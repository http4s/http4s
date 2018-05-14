package org.http4s
package netty

import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import cats.effect.{Effect, IO}
import com.typesafe.netty.HandlerPublisher
import com.typesafe.netty.http.HttpStreamsServerHandler
import fs2.interop.reactivestreams._
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.IdleStateHandler
import org.http4s.server._
import org.log4s.getLogger

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/** Netty server builder.
  *
  *
  * @param mounts
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
class NettyBuilder[F[_]](
    mounts: Vector[NettyMount[F]],
    socketAddress: InetSocketAddress,
    idleTimeout: Duration,
    eventLoopThreads: Int,
    maxInitialLineLength: Int,
    maxHeaderSize: Int,
    maxChunkSize: Int,
    sslBits: Option[NettySSLConfig],
    serviceErrorHandler: ServiceErrorHandler[F],
    transport: NettyTransport,
    ec: ExecutionContext,
    enableWebsockets: Boolean,
    banner: immutable.Seq[String],
    nettyChannelOptions: NettyBuilder.NettyChannelOptions
)(implicit F: Effect[F])
    extends ServerBuilder[F]
    with IdleTimeoutSupport[F]
    with SSLKeyStoreSupport[F]
    with SSLContextSupport[F]
    with WebSocketSupport[F] {
  implicit val e = ec
  private val logger = getLogger

  type Self = NettyBuilder[F]

  private def copy(
      mounts: Vector[NettyMount[F]] = mounts,
      socketAddress: InetSocketAddress = socketAddress,
      idleTimeout: Duration = Duration.Inf,
      eventLoopThreads: Int = eventLoopThreads,
      maxInitialLineLength: Int = maxInitialLineLength,
      maxHeaderSize: Int = maxHeaderSize,
      maxChunkSize: Int = maxChunkSize,
      sslBits: Option[NettySSLConfig] = sslBits,
      serviceErrorHandler: ServiceErrorHandler[F] = serviceErrorHandler,
      ec: ExecutionContext = ec,
      enableWebsockets: Boolean = enableWebsockets,
      banner: immutable.Seq[String] = banner,
      transport: NettyTransport = transport,
      nettyChannelOptions: NettyBuilder.NettyChannelOptions = nettyChannelOptions
  ): NettyBuilder[F] =
    new NettyBuilder[F](
      mounts,
      socketAddress,
      idleTimeout,
      eventLoopThreads,
      maxInitialLineLength,
      maxHeaderSize,
      maxChunkSize,
      sslBits,
      serviceErrorHandler,
      transport,
      ec,
      enableWebsockets,
      banner,
      nettyChannelOptions
    )

  def bindSocketAddress(socketAddress: InetSocketAddress): NettyBuilder[F] =
    copy(socketAddress = socketAddress)

  def withExecutionContext(executionContext: ExecutionContext): NettyBuilder[F] =
    copy(ec = executionContext)

  def withServiceErrorHandler(serviceErrorHandler: ServiceErrorHandler[F]): NettyBuilder[F] =
    copy(serviceErrorHandler = serviceErrorHandler)

  def mountService(service: HttpService[F], prefix: String): NettyBuilder[F] = {
    val prefixedService =
      if (prefix.isEmpty || prefix == "/") service
      else {
        val newCaret = (if (prefix.startsWith("/")) 0 else 1) + prefix.length

        service.local { req: Request[F] =>
          req.withAttribute(Request.Keys.PathInfoCaret(newCaret))
        }
      }
    copy(mounts = mounts :+ NettyMount[F](prefixedService, prefix))
  }

  def start: F[Server[F]] = F.delay {
    val eventLoop = getEventLoop

    val allChannels: DefaultChannelGroup = new DefaultChannelGroup(eventLoop.next())

    val (_, boundAddress) = startNetty(eventLoop, allChannels)
    val server = new Server[F] {
      def shutdown: F[Unit] = F.delay {
        // First, close all opened sockets
        allChannels.close().awaitUninterruptibly()
        // Now shutdown the event loop
        eventLoop.shutdownGracefully()

        logger.info(s"All channels shut down. Server bound at ${baseUri} shut down gracefully")
      }

      def onShutdown(f: => Unit): this.type = {
        Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
          def run(): Unit = f
        }))
        this
      }

      def address: InetSocketAddress = boundAddress

      def isSecure: Boolean = sslBits.isDefined
    }
    banner.foreach(logger.info(_))
    logger.info(s"Started Http4s Netty Server at ${server.baseUri} ༼ つ ◕_◕ ༽つ")
    server
  }

  def withBanner(banner: immutable.Seq[String]): NettyBuilder[F] =
    copy(banner = banner)

  def withIdleTimeout(idleTimeout: Duration): NettyBuilder[F] =
    copy(idleTimeout = idleTimeout)

  def withSSL(
      keyStore: SSLKeyStoreSupport.StoreInfo,
      keyManagerPassword: String,
      protocol: String,
      trustStore: Option[SSLKeyStoreSupport.StoreInfo],
      clientAuth: Boolean
  ): NettyBuilder[F] = {
    val auth = if (clientAuth) ClientAuthRequired else NoClientAuth
    val bits = MikuKeyStoreBits(keyStore, keyManagerPassword, protocol, trustStore, auth)
    copy(sslBits = Some(bits))
  }

  def withSSLContext(sslContext: SSLContext, clientAuth: Boolean): NettyBuilder[F] = {
    val auth = if (clientAuth) ClientAuthRequired else NoClientAuth
    copy(sslBits = Some(MikuSSLContextBits(sslContext, auth)))
  }

  def withWebSockets(enableWebsockets: Boolean): NettyBuilder[F] =
    copy(enableWebsockets = enableWebsockets)

  /** Netty-only option **/
  def withTransport(transport: NettyTransport): NettyBuilder[F] =
    copy(transport = transport)

  /** Netty-only option **/
  def withChannelOptions(channelOptions: NettyBuilder.NettyChannelOptions): NettyBuilder[F] =
    copy(nettyChannelOptions = channelOptions)

  def withHttpCodecOptions(
      maxInitialLineLength: Int,
      maxHeaderSize: Int,
      maxChunkSize: Int): NettyBuilder[F] =
    copy(
      maxInitialLineLength = maxInitialLineLength,
      maxHeaderSize = maxHeaderSize,
      maxChunkSize = maxChunkSize)

  /** Netty-only option **/
  def withEventLoopThreads(eventLoopThreads: Int): NettyBuilder[F] =
    copy(eventLoopThreads = eventLoopThreads)

  protected[this] def newRequestHandler(): ChannelInboundHandler = {
    val finalService = Router(mounts.map(mount => mount.prefix -> mount.service): _*)

    if (enableWebsockets)
      Http4sNettyHandler.websocket[F](finalService, serviceErrorHandler)
    else
      Http4sNettyHandler.default[F](finalService, serviceErrorHandler)
  }

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
  private[netty] def registerChannel(
      eventLoop: MultithreadEventLoopGroup,
      allChannels: DefaultChannelGroup): Channel => F[Unit] = { connChannel =>
    F.delay {

      // Setup the channel for explicit reads
      connChannel
        .config()
        .setOption(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE)

      val pipeline = connChannel.pipeline()
      getContext() match {
        case Some((ctx, auth)) =>
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

      //Ensure finite length + positive timeout value
      if (idleTimeout.isFinite() && idleTimeout.length > 0) {
        pipeline.addLast(
          "idle-handler",
          new IdleStateHandler(0, 0, idleTimeout.length, idleTimeout.unit))
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

  private[netty] def getEventLoop: MultithreadEventLoopGroup = transport match {
    case NettyTransport.Nio =>
      new NioEventLoopGroup(eventLoopThreads)
    case NettyTransport.Native =>
      if (Epoll.isAvailable)
        new EpollEventLoopGroup(eventLoopThreads)
      else {
        logger.info("Falling back to NIO EventLoopGroup")
        new NioEventLoopGroup(eventLoopThreads)
      }
  }

  private def addChannelOptions(b: Bootstrap) =
    nettyChannelOptions.foldLeft(b)((boot, o) => boot.option(o._1, o._2))

  private def bootstrapNative(
      serverChannelEventLoop: EventLoopGroup,
      channelPublisher: HandlerPublisher[Channel],
      address: InetSocketAddress): Bootstrap =
    addChannelOptions(
      new Bootstrap()
        .channel(classOf[EpollServerSocketChannel])
        .group(serverChannelEventLoop))
      .option(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE) // publisher does ctx.read()
      .handler(channelPublisher)
      .localAddress(address)

  private def bootstrapNIO(
      serverChannelEventLoop: EventLoopGroup,
      channelPublisher: HandlerPublisher[Channel],
      address: InetSocketAddress): Bootstrap =
    addChannelOptions(
      new Bootstrap()
        .channel(classOf[NioServerSocketChannel])
        .group(serverChannelEventLoop))
      .option(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE) // publisher does ctx.read()
      .handler(channelPublisher)
      .localAddress(address)

  private[netty] def getBootstrap(
      serverChannelEventLoop: EventLoopGroup,
      channelPublisher: HandlerPublisher[Channel],
      address: InetSocketAddress): Bootstrap = transport match {
    case NettyTransport.Nio =>
      bootstrapNIO(serverChannelEventLoop, channelPublisher, address)
    case NettyTransport.Native =>
      if (Epoll.isAvailable)
        bootstrapNative(serverChannelEventLoop, channelPublisher, address)
      else {
        logger.info(
          "Native transport not available. Falling back to nio transport. Please use .withTransport(Nio)")
        bootstrapNIO(serverChannelEventLoop, channelPublisher, address)
      }
  }

  /** Bind our server to our address and create the channel handler stream, which we must
    * bind the handlers to.
    */
  private[netty] def bind(
      eventLoop: MultithreadEventLoopGroup,
      allChannels: DefaultChannelGroup,
      address: InetSocketAddress): (Channel, fs2.Stream[F, Channel]) = {
    val serverChannelEventLoop = eventLoop.next

    // Watches for channel events, and pushes them through a reactive streams publisher.
    val channelPublisher =
      new HandlerPublisher(serverChannelEventLoop, classOf[Channel])

    val bootstrap: Bootstrap = getBootstrap(serverChannelEventLoop, channelPublisher, address)
    //Actually bind the server to the address
    val channel = bootstrap.bind.await().channel()
    allChannels.add(channel)

    (channel, channelPublisher.toStream[F]())
  }

  private[netty] def startNetty(
      eventLoop: MultithreadEventLoopGroup,
      allChannels: DefaultChannelGroup
  ): (Channel, InetSocketAddress) = {

    //Resolve address
    val resolvedAddress = new InetSocketAddress(socketAddress.getHostName, socketAddress.getPort)
    val (serverChannel, channelSource) = bind(eventLoop, allChannels, resolvedAddress)
    F.runAsync(
        channelSource
          .evalMap(registerChannel(eventLoop, allChannels))
          .compile
          .drain
      )(_.fold(IO.raiseError, _ => IO.unit)) //This shouldn't ever throw an error in the first place.
      .unsafeRunSync()
    //Cast downcast here is unfortunately necessary... because netty.
    val boundAddress = serverChannel.localAddress().asInstanceOf[InetSocketAddress]
    if (boundAddress == null) {
      val e = new NettyBuilder.ServerInitException("no bound address")
      logger.error(e)("Error in server initialization")
      throw e
    }
    (serverChannel, boundAddress)
  }

}

object NettyBuilder {

  //This means, let netty choose
  private[this] val DefaultEventLoopThreads = 0

  /** Ensure we construct our netty channel options in a typeful, immutable way, despite
    * the underlying being disgusting
    */
  sealed abstract class NettyChannelOptions {

    /** Prepend to the channel options **/
    def prepend[O](channelOption: ChannelOption[O], value: O): NettyChannelOptions

    /** Append to the channel options **/
    def append[O](channelOption: ChannelOption[O], value: O): NettyChannelOptions

    /** Remove a channel option, if present **/
    def remove[O](channelOption: ChannelOption[O]): NettyChannelOptions

    private[http4s] def foldLeft[O](initial: O)(f: (O, (ChannelOption[Any], Any)) => O): O
  }

  object NettyChannelOptions {
    val empty = new NettyCOptions(Vector.empty)
  }

  private[http4s] final class NettyCOptions(
      private[http4s] val underlying: Vector[(ChannelOption[Any], Any)])
      extends NettyChannelOptions {

    def prepend[O](channelOption: ChannelOption[O], value: O): NettyChannelOptions =
      new NettyCOptions((channelOption.asInstanceOf[ChannelOption[Any]], value: Any) +: underlying)

    def append[O](channelOption: ChannelOption[O], value: O): NettyChannelOptions =
      new NettyCOptions(
        underlying :+ ((channelOption.asInstanceOf[ChannelOption[Any]], value: Any)))

    def remove[O](channelOption: ChannelOption[O]): NettyChannelOptions =
      new NettyCOptions(underlying.filterNot(_._1 == channelOption))

    private[http4s] def foldLeft[O](initial: O)(f: (O, (ChannelOption[Any], Any)) => O) =
      underlying.foldLeft[O](initial)(f)
  }

  def apply[F[_]: Effect] =
    new NettyBuilder[F](
      mounts = Vector.empty,
      socketAddress = ServerBuilder.DefaultSocketAddress,
      idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
      eventLoopThreads = DefaultEventLoopThreads,
      maxInitialLineLength = 4096,
      maxHeaderSize = 8192,
      maxChunkSize = 8192,
      sslBits = None,
      serviceErrorHandler = DefaultServiceErrorHandler[F],
      transport = NettyTransport.Native,
      ec = ExecutionContext.global,
      enableWebsockets = false,
      banner = MikuBanner,
      nettyChannelOptions = NettyChannelOptions.empty
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

  private[netty] class ServerInitException(message: String) extends Exception(message)
}

sealed trait NettyTransport extends Product with Serializable

object NettyTransport {
  case object Nio extends NettyTransport
  case object Native extends NettyTransport
}

private[netty] case class NettyMount[F[_]](service: HttpService[F], prefix: String)

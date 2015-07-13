package org.http4s
package server
package blaze

import java.io.FileInputStream
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.{TrustManagerFactory, KeyManagerFactory, SSLContext}
import java.util.concurrent.ExecutorService
import java.net.InetSocketAddress
import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.{SSLStage, QuietTimeoutStage}
import org.http4s.blaze.channel.SocketConnection
import org.http4s.blaze.channel.nio1.NIO1SocketServerGroup
import org.http4s.blaze.channel.nio2.NIO2SocketServerGroup
import org.http4s.server.SSLSupport.{StoreInfo, SSLBits}

import org.log4s.getLogger

import scala.annotation.tailrec
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}

class BlazeBuilder(
  socketAddress: InetSocketAddress,
  serviceExecutor: ExecutorService,
  idleTimeout: Duration,
  isNio2: Boolean,
  sslBits: Option[SSLBits],
  isHttp2Enabled: Boolean,
  serviceMounts: Vector[ServiceMount]
)
  extends ServerBuilder
  with IdleTimeoutSupport
  with SSLSupport
{
  type Self = BlazeBuilder

  private[this] val logger = getLogger

  private def copy(socketAddress: InetSocketAddress = socketAddress,
                 serviceExecutor: ExecutorService = serviceExecutor,
                     idleTimeout: Duration = idleTimeout,
                          isNio2: Boolean = isNio2,
                         sslBits: Option[SSLBits] = sslBits,
                    http2Support: Boolean = isHttp2Enabled,
                   serviceMounts: Vector[ServiceMount] = serviceMounts): BlazeBuilder =
    new BlazeBuilder(socketAddress, serviceExecutor, idleTimeout, isNio2, sslBits, http2Support, serviceMounts)


  override def withSSL(keyStore: StoreInfo, keyManagerPassword: String, protocol: String, trustStore: Option[StoreInfo], clientAuth: Boolean): Self = {
    val bits = SSLBits(keyStore, keyManagerPassword, protocol, trustStore, clientAuth)
    copy(sslBits = Some(bits))
  }

  override def bindSocketAddress(socketAddress: InetSocketAddress): BlazeBuilder =
    copy(socketAddress = socketAddress)

  override def withServiceExecutor(serviceExecutor: ExecutorService): BlazeBuilder =
    copy(serviceExecutor = serviceExecutor)

  override def withIdleTimeout(idleTimeout: Duration): BlazeBuilder = copy(idleTimeout = idleTimeout)

  def withNio2(isNio2: Boolean): BlazeBuilder = copy(isNio2 = isNio2)

  def enableHttp2(enabled: Boolean): BlazeBuilder =
    copy(http2Support = enabled)

  override def mountService(service: HttpService, prefix: String): BlazeBuilder = {
    val prefixedService =
                if (prefix.isEmpty || prefix == "/") service
                else {
                  val newCaret = prefix match {
                    case "/"                    => 0
                    case x if x.startsWith("/") => x.length
                    case x                      => x.length + 1
                  }

                  service.contramap { req: Request =>
                    req.withAttribute(Request.Keys.PathInfoCaret(newCaret))
                  }
                }
    copy(serviceMounts = serviceMounts :+ ServiceMount(prefixedService, prefix))
  }


  def start: Task[Server] = Task.delay {
    val aggregateService = Router(serviceMounts.map { mount => mount.prefix -> mount.service })

    val pipelineFactory = getContext() match {
      case Some((ctx, clientAuth)) =>
        (conn: SocketConnection) => {
          val eng = ctx.createSSLEngine()

          val l1 =
            if (isHttp2Enabled) LeafBuilder(ProtocolSelector(eng, aggregateService, 4*1024, Some(conn), serviceExecutor))
            else LeafBuilder(new Http1ServerStage(aggregateService, Some(conn), serviceExecutor))

          val l2 = if (idleTimeout.isFinite) l1.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
                   else l1

          eng.setUseClientMode(false)
          eng.setNeedClientAuth(clientAuth)

          l2.prepend(new SSLStage(eng))
        }

      case None =>
        if (isHttp2Enabled) logger.warn("Http2 support requires TLS.")
        (conn: SocketConnection) => {
          val leaf = LeafBuilder(new Http1ServerStage(aggregateService, Some(conn), serviceExecutor))
          if (idleTimeout.isFinite) leaf.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
          else leaf
        }
    }

    val factory =
      if (isNio2)
        NIO2SocketServerGroup.fixedGroup(12, 8 * 1024)
      else
        NIO1SocketServerGroup.fixedGroup(12, 8 * 1024)

    var address = socketAddress
    if (address.isUnresolved)
      address = new InetSocketAddress(address.getHostString, address.getPort)

    // if we have a Failure, it will be caught by the Task
    val serverChannel = factory.bind(address, pipelineFactory).get

    new Server {
      override def shutdown: Task[this.type] = Task.delay {
        serverChannel.close()
        factory.closeGroup()
        this
      }

      override def onShutdown(f: => Unit): this.type = {
        serverChannel.addShutdownHook(() => f)
        this
      }
    }
  }

  private def getContext(): Option[(SSLContext, Boolean)] = sslBits.map { bits =>

    val ksStream = new FileInputStream(bits.keyStore.path)
    val ks = KeyStore.getInstance("JKS")
    ks.load(ksStream, bits.keyStore.password.toCharArray)
    ksStream.close()

    val tmf = bits.trustStore.map { auth =>
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
                    .getOrElse(KeyManagerFactory.getDefaultAlgorithm))

    kmf.init(ks, bits.keyManagerPassword.toCharArray)

    val context = SSLContext.getInstance(bits.protocol)
    context.init(kmf.getKeyManagers(), tmf.orNull, null)

    (context, bits.clientAuth)
  }
}

object BlazeBuilder extends BlazeBuilder(
  socketAddress = ServerBuilder.DefaultSocketAddress,
  serviceExecutor = Strategy.DefaultExecutorService,
  idleTimeout = IdleTimeoutSupport.DefaultIdleTimeout,
  isNio2 = false,
  sslBits = None,
  isHttp2Enabled = false,
  serviceMounts = Vector.empty
)

private case class ServiceMount(service: HttpService, prefix: String)


package org.http4s
package server
package blaze

import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.{TrustManagerFactory, KeyManagerFactory, SSLContext}
import java.util.concurrent.ExecutorService
import java.net.InetSocketAddress
import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.{SSLStage, QuietTimeoutStage}
import org.http4s.blaze.channel.SocketConnection
import org.http4s.blaze.channel.nio1.NIO1SocketServerChannelFactory
import org.http4s.blaze.channel.nio2.NIO2SocketServerChannelFactory
import org.http4s.server.SSLSupport.{StoreInfo, SSLBits}

import server.middleware.URITranslation

import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}

class BlazeBuilder(
  socketAddress: InetSocketAddress,
  serviceExecutor: ExecutorService,
  idleTimeout: Duration,
  isNio2: Boolean,
  sslBits: Option[SSLBits],
  serviceMounts: Vector[ServiceMount]
)
  extends ServerBuilder
  with IdleTimeoutSupport
  with SSLSupport
{
  type Self = BlazeBuilder

  private def copy(socketAddress: InetSocketAddress = socketAddress,
                 serviceExecutor: ExecutorService = serviceExecutor,
                     idleTimeout: Duration = idleTimeout,
                          isNio2: Boolean = isNio2,
                         sslBits: Option[SSLBits] = sslBits,
                   serviceMounts: Vector[ServiceMount] = serviceMounts): BlazeBuilder =
    new BlazeBuilder(socketAddress, serviceExecutor, idleTimeout, isNio2, sslBits, serviceMounts)


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

  override def mountService(service: HttpService, prefix: String): BlazeBuilder =
    copy(serviceMounts = serviceMounts :+ ServiceMount(service, prefix))

  def start: Task[Server] = Task.delay {
    val aggregateService = serviceMounts.foldLeft[HttpService](Service.empty) {
      case (aggregate, ServiceMount(service, prefix)) =>
        val prefixedService =
          if (prefix.isEmpty || prefix == "/") service
          else URITranslation.translateRoot(prefix)(service)

        if (aggregate.run eq Service.empty.run)
          prefixedService
        else
          prefixedService orElse aggregate
    }

    val pipelineFactory = getContext() match {
      case Some((ctx, clientAuth)) =>
        (conn: SocketConnection) => {
          val l1 = LeafBuilder(new Http1ServerStage(aggregateService, Some(conn), serviceExecutor))
          val l2 = if (idleTimeout.isFinite) l1.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
                   else l1

          val eng = ctx.createSSLEngine()
          eng.setUseClientMode(false)
          eng.setNeedClientAuth(clientAuth)

          l2.prepend(new SSLStage(eng))
        }

      case None =>
        (conn: SocketConnection) => {
          val leaf = LeafBuilder(new Http1ServerStage(aggregateService, Some(conn), serviceExecutor))
          if (idleTimeout.isFinite) leaf.prepend(new QuietTimeoutStage[ByteBuffer](idleTimeout))
          else leaf
        }
    }

    val factory =
      if (isNio2)
        new NIO2SocketServerChannelFactory(pipelineFactory)
      else
        new NIO1SocketServerChannelFactory(pipelineFactory, 12, 8 * 1024)

    var address = socketAddress
    if (address.isUnresolved)
      address = new InetSocketAddress(address.getHostString, address.getPort)
    val serverChannel = factory.bind(address)

    // Begin the server asynchronously
    serverChannel.runAsync()

    new Server {
      override def shutdown: Task[this.type] = Task.delay {
        serverChannel.close()
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

    val kmf = KeyManagerFactory.getInstance("SunX509")
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
  serviceMounts = Vector.empty
)

private case class ServiceMount(service: HttpService, prefix: String)


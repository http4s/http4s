package org.http4s
package client
package blaze

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.Uri.Scheme
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.headers.`User-Agent`
import org.http4s.util.task
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.util.CaseInsensitiveString._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scalaz.concurrent.Task

import scalaz.{\/, -\/, \/-}

object Http1Support {
  /** Create a new [[ConnectionBuilder]]
   *
   * @param bufferSize buffer size of the socket stages
   * @param userAgent User-Agent header information
   * @param es `ExecutorService` on which computations should be run
   * @param osslContext Optional `SSSContext` for secure requests
   * @param group `AsynchronousChannelGroup` used to manage socket connections
   * @return [[ConnectionBuilder]] for creating new requests
   */
  def apply(bufferSize: Int,
             userAgent: Option[`User-Agent`],
                    es: ExecutorService,
           osslContext: Option[SSLContext],
       endpointAuthentication: Boolean,
                 group: Option[AsynchronousChannelGroup]): ConnectionBuilder[BlazeConnection] = {
    val builder = new Http1Support(bufferSize, userAgent, es, osslContext, endpointAuthentication, group)
    builder.makeClient
  }

  private val Https: Scheme = "https".ci
  private val Http: Scheme  = "http".ci
}

/** Provides basic HTTP1 pipeline building
  */
final private class Http1Support(bufferSize: Int,
                          userAgent: Option[`User-Agent`],
                                 es: ExecutorService,
                        osslContext: Option[SSLContext],
             endpointAuthentication: Boolean,
                              group: Option[AsynchronousChannelGroup]) {
  import Http1Support._

  private val ec = ExecutionContext.fromExecutorService(es)
  private val sslContext = osslContext.getOrElse(bits.sslContext)
  private val connectionManager = new ClientChannelFactory(bufferSize, group.orNull)

////////////////////////////////////////////////////

  def makeClient(requestKey: RequestKey): Task[BlazeConnection] = getAddress(requestKey) match {
    case \/-(a) => task.futureToTask(buildPipeline(requestKey, a))(ec)
    case -\/(t) => Task.fail(t)
  }

  private def buildPipeline(requestKey: RequestKey, addr: InetSocketAddress): Future[BlazeConnection] = {
    connectionManager.connect(addr, bufferSize).map { head =>
      val (builder, t) = buildStages(requestKey)
      builder.base(head)
      t
    }(ec)
  }

  private def buildStages(requestKey: RequestKey): (LeafBuilder[ByteBuffer], BlazeConnection) = {
    val t = new Http1Connection(requestKey, userAgent, ec)
    val builder = LeafBuilder(t)
    requestKey match {
      case RequestKey(Https, auth) if endpointAuthentication =>
        val eng = sslContext.createSSLEngine(auth.host.value, auth.port getOrElse 443)
        eng.setUseClientMode(true)

        val sslParams = eng.getSSLParameters
        sslParams.setEndpointIdentificationAlgorithm("HTTPS")
        eng.setSSLParameters(sslParams)

        (builder.prepend(new SSLStage(eng)),t)

      case RequestKey(Https, _) =>
        val eng = sslContext.createSSLEngine()
        eng.setUseClientMode(true)
        (builder.prepend(new SSLStage(eng)),t)

      case _ => (builder, t)
    }
  }

  private def getAddress(requestKey: RequestKey): Throwable \/ InetSocketAddress = {
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port getOrElse { if (s == Https) 443 else 80 }
        val host = auth.host.value
        \/.fromTryCatchNonFatal(new InetSocketAddress(host, port))
    }
  }
}


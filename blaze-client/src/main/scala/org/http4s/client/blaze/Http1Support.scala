package org.http4s
package client
package blaze

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import org.http4s.Uri.Scheme
import org.http4s.blaze.channel.nio2.ClientChannelFactory
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
   * @param config The client configuration object
   */
  def apply(config: BlazeClientConfig): ConnectionBuilder[BlazeConnection] = {
    val builder = new Http1Support(config)
    builder.makeClient
  }

  private val Https: Scheme = "https".ci
  private val Http: Scheme  = "http".ci
}

/** Provides basic HTTP1 pipeline building
  */
final private class Http1Support(config: BlazeClientConfig) {
  import Http1Support._

  private val ec = ExecutionContext.fromExecutorService(config.executor)
  private val sslContext = config.sslContext.getOrElse(bits.sslContext)
  private val connectionManager = new ClientChannelFactory(config.bufferSize, config.group.orNull)

////////////////////////////////////////////////////

  def makeClient(requestKey: RequestKey): Task[BlazeConnection] = getAddress(requestKey) match {
    case \/-(a) => task.futureToTask(buildPipeline(requestKey, a))(ec)
    case -\/(t) => Task.fail(t)
  }

  private def buildPipeline(requestKey: RequestKey, addr: InetSocketAddress): Future[BlazeConnection] = {
    connectionManager.connect(addr, config.bufferSize).map { head =>
      val (builder, t) = buildStages(requestKey)
      builder.base(head)
      t
    }(ec)
  }

  private def buildStages(requestKey: RequestKey): (LeafBuilder[ByteBuffer], BlazeConnection) = {
    val t = new Http1Connection(requestKey, config, ec)
    val builder = LeafBuilder(t)
    requestKey match {
      case RequestKey(Https, auth) if config.endpointAuthentication =>
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


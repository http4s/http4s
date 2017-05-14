package org.http4s
package client
package blaze

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.Uri.Scheme
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.util.task
import org.http4s.blaze.pipeline.{Command, LeafBuilder}
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.syntax.string._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scalaz.concurrent.Task
import scalaz.{-\/, \/, \/-}

private[blaze] object Http1Support {
  /** Create a new [[ConnectionBuilder]]
   *
   * @param config The client configuration object
   */
  def apply(config: BlazeClientConfig, executor: ExecutorService): ConnectionBuilder[BlazeConnection] = {
    val builder = new Http1Support(config, executor)
    builder.makeClient
  }

  val Https: Scheme = "https".ci
  val Http: Scheme  = "http".ci
}

/** Provides basic HTTP1 pipeline building
  */
final private class Http1Support(config: BlazeClientConfig, executor: ExecutorService) {
  import Http1Support._

  private val ec = ExecutionContext.fromExecutorService(executor)
  private val sslContext = config.sslContext.getOrElse(SSLContext.getDefault)
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
      head.inboundCommand(Command.Connected)
      t
    }(ec)
  }

  private def buildStages(requestKey: RequestKey): (LeafBuilder[ByteBuffer], BlazeConnection) = {
    val t = new Http1Connection(requestKey, config, executor, ec)
    val builder = LeafBuilder(t).prepend(new ReadBufferStage[ByteBuffer])
    requestKey match {
      case RequestKey(Https, auth) =>
        val eng = sslContext.createSSLEngine(auth.host.value, auth.port getOrElse 443)
        eng.setUseClientMode(true)

        if (config.checkEndpointIdentification) {
          val sslParams = eng.getSSLParameters
          sslParams.setEndpointIdentificationAlgorithm("HTTPS")
          eng.setSSLParameters(sslParams)
        }

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


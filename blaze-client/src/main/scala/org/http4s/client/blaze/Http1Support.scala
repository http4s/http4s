package org.http4s
package client
package blaze

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext

import cats.implicits._
import fs2.{Strategy, Task}
import org.http4s.Uri.Scheme
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.blaze.pipeline.{Command, LeafBuilder}
import org.http4s.syntax.string._

import scala.concurrent.{ExecutionContext, Future}

private object Http1Support {
  /** Create a new [[ConnectionBuilder]]
   *
   * @param config The client configuration object
   */
  def apply(config: BlazeClientConfig): ConnectionBuilder[BlazeConnection] =
    new Http1Support(config).makeClient

  private val Https: Scheme = "https".ci
  private val Http: Scheme  = "http".ci
}

/** Provides basic HTTP1 pipeline building
  */
final private class Http1Support(config: BlazeClientConfig) {
  import Http1Support._

  private val strategy = Strategy.fromExecutionContext(config.executionContext)
  private val sslContext = config.sslContext.getOrElse(SSLContext.getDefault)
  private val connectionManager = new ClientChannelFactory(config.bufferSize, config.group.orNull)

////////////////////////////////////////////////////

  def makeClient(requestKey: RequestKey): Task[BlazeConnection] = getAddress(requestKey) match {
    case Right(a) => Task.fromFuture(buildPipeline(requestKey, a))(strategy, config.executionContext)
    case Left(t) => Task.fail(t)
  }

  private def buildPipeline(requestKey: RequestKey, addr: InetSocketAddress): Future[BlazeConnection] = {
    connectionManager.connect(addr, config.bufferSize).map { head =>
      val (builder, t) = buildStages(requestKey)
      builder.base(head)
      head.inboundCommand(Command.Connected)
      t
    }(config.executionContext)
  }

  private def buildStages(requestKey: RequestKey): (LeafBuilder[ByteBuffer], BlazeConnection) = {
    val t = new Http1Connection(requestKey, config)
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

  private def getAddress(requestKey: RequestKey): Either[Throwable, InetSocketAddress] = {
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port getOrElse { if (s == Https) 443 else 80 }
        val host = auth.host.value
        Either.catchNonFatal(new InetSocketAddress(host, port))
    }
  }
}

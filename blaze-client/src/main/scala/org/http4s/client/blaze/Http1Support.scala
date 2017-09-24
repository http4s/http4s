package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext
import org.http4s.Uri.Scheme
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.blaze.pipeline.{Command, LeafBuilder}
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.syntax.async._
import org.http4s.syntax.string._
import scala.concurrent.Future

private object Http1Support {

  /** Create a new [[ConnectionBuilder]]
    *
    * @param config The client configuration object
    */
  def apply[F[_]: Effect](config: BlazeClientConfig): ConnectionBuilder[F, BlazeConnection[F]] = {
    val builder = new Http1Support(config)
    builder.makeClient
  }

  private val Https: Scheme = "https".ci
}

/** Provides basic HTTP1 pipeline building
  */
final private class Http1Support[F[_]](config: BlazeClientConfig)(implicit F: Effect[F]) {
  import Http1Support._

  private val sslContext = config.sslContext.getOrElse(SSLContext.getDefault)
  private val connectionManager = new ClientChannelFactory(config.bufferSize, config.group.orNull)

////////////////////////////////////////////////////

  def makeClient(requestKey: RequestKey): F[BlazeConnection[F]] =
    getAddress(requestKey) match {
      case Right(a) => F.fromFuture(buildPipeline(requestKey, a))(config.executionContext)
      case Left(t) => F.raiseError(t)
    }

  private def buildPipeline(
      requestKey: RequestKey,
      addr: InetSocketAddress): Future[BlazeConnection[F]] =
    connectionManager
      .connect(addr, config.bufferSize)
      .map { head =>
        val (builder, t) = buildStages(requestKey)
        builder.base(head)
        head.inboundCommand(Command.Connected)
        t
      }(config.executionContext)

  private def buildStages(requestKey: RequestKey): (LeafBuilder[ByteBuffer], BlazeConnection[F]) = {
    val t = new Http1Connection(requestKey, config)
    val builder = LeafBuilder(t).prepend(new ReadBufferStage[ByteBuffer])
    requestKey match {
      case RequestKey(Https, auth) =>
        val eng = sslContext.createSSLEngine(auth.host.value, auth.port.getOrElse(443))
        eng.setUseClientMode(true)

        if (config.checkEndpointIdentification) {
          val sslParams = eng.getSSLParameters
          sslParams.setEndpointIdentificationAlgorithm("HTTPS")
          eng.setSSLParameters(sslParams)
        }

        (builder.prepend(new SSLStage(eng)), t)

      case _ => (builder, t)
    }
  }

  private def getAddress(requestKey: RequestKey): Either[Throwable, InetSocketAddress] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse { if (s == Https) 443 else 80 }
        val host = auth.host.value
        Either.catchNonFatal(new InetSocketAddress(host, port))
    }
}

package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import javax.net.ssl.SSLContext
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.blaze.pipeline.{Command, LeafBuilder}
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.headers.`User-Agent`
import org.http4s.internal.fromFuture
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** Provides basic HTTP1 pipeline building
  */
final private class Http1Support[F[_]](
    sslContextOption: Option[SSLContext],
    bufferSize: Int,
    asynchronousChannelGroup: Option[AsynchronousChannelGroup],
    executionContext: ExecutionContext,
    checkEndpointIdentification: Boolean,
    maxResponseLineSize: Int,
    maxHeaderLength: Int,
    maxChunkSize: Int,
    chunkBufferMaxSize: Int,
    parserMode: ParserMode,
    userAgent: Option[`User-Agent`],
    channelOptions: ChannelOptions
)(implicit F: ConcurrentEffect[F]) {

  // SSLContext.getDefault is effectful and can fail - don't force it until we have to.
  private lazy val sslContext = sslContextOption.getOrElse(SSLContext.getDefault)
  private val connectionManager =
    new ClientChannelFactory(bufferSize, asynchronousChannelGroup, channelOptions)

////////////////////////////////////////////////////

  def makeClient(requestKey: RequestKey): F[BlazeConnection[F]] =
    getAddress(requestKey) match {
      case Right(a) => fromFuture(F.delay(buildPipeline(requestKey, a)))
      case Left(t) => F.raiseError(t)
    }

  private def buildPipeline(
      requestKey: RequestKey,
      addr: InetSocketAddress): Future[BlazeConnection[F]] =
    connectionManager
      .connect(addr, bufferSize)
      .map { head =>
        val (builder, t) = buildStages(requestKey)
        builder.base(head)
        head.inboundCommand(Command.Connected)
        t
      }(executionContext)

  private def buildStages(requestKey: RequestKey): (LeafBuilder[ByteBuffer], BlazeConnection[F]) = {
    val t = new Http1Connection(
      requestKey = requestKey,
      executionContext = executionContext,
      maxResponseLineSize = maxResponseLineSize,
      maxHeaderLength = maxHeaderLength,
      maxChunkSize = maxChunkSize,
      chunkBufferMaxSize = chunkBufferMaxSize,
      parserMode = parserMode,
      userAgent = userAgent
    )
    val builder = LeafBuilder(t).prepend(new ReadBufferStage[ByteBuffer])
    requestKey match {
      case RequestKey(Uri.Scheme.https, auth) =>
        val eng = sslContext.createSSLEngine(auth.host.value, auth.port.getOrElse(443))
        eng.setUseClientMode(true)

        if (checkEndpointIdentification) {
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
        val port = auth.port.getOrElse { if (s == Uri.Scheme.https) 443 else 80 }
        val host = auth.host.value
        Either.catchNonFatal(new InetSocketAddress(host, port))
    }
}

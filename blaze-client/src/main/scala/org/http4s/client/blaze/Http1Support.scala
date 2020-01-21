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
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.blaze.pipeline.{Command, LeafBuilder}
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.headers.`User-Agent`
import org.http4s.internal.fromFuture
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Provides basic HTTP1 pipeline building
  */
final private class Http1Support[F[_]](
    sslContextOption: Option[SSLContext],
    bufferSize: Int,
    asynchronousChannelGroup: Option[AsynchronousChannelGroup],
    executionContext: ExecutionContext,
    scheduler: TickWheelExecutor,
    checkEndpointIdentification: Boolean,
    maxResponseLineSize: Int,
    maxHeaderLength: Int,
    maxChunkSize: Int,
    chunkBufferMaxSize: Int,
    parserMode: ParserMode,
    userAgent: Option[`User-Agent`],
    channelOptions: ChannelOptions,
    connectTimeout: Duration
)(implicit F: ConcurrentEffect[F]) {

  private val connectionManager = new ClientChannelFactory(
    bufferSize,
    asynchronousChannelGroup,
    channelOptions,
    scheduler,
    connectTimeout
  )

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
      .connect(addr)
      .transformWith {
        case Success(head) =>
          buildStages(requestKey) match {
            case Right((builder, t)) =>
              Future.successful {
                builder.base(head)
                head.inboundCommand(Command.Connected)
                t
              }
            case Left(e) =>
              Future.failed(new ClientFailure(requestKey, addr, e))
          }
        case Failure(e) => Future.failed(new ClientFailure(requestKey, addr, e))
      }(executionContext)

  private def buildStages(requestKey: RequestKey)
      : Either[IllegalStateException, (LeafBuilder[ByteBuffer], BlazeConnection[F])] = {
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
        sslContextOption match {
          case Some(sslContext) =>
            val eng = sslContext.createSSLEngine(auth.host.value, auth.port.getOrElse(443))
            eng.setUseClientMode(true)

            if (checkEndpointIdentification) {
              val sslParams = eng.getSSLParameters
              sslParams.setEndpointIdentificationAlgorithm("HTTPS")
              eng.setSSLParameters(sslParams)
            }

            Right((builder.prepend(new SSLStage(eng)), t))

          case None =>
            Left(new IllegalStateException(
              "No SSLContext configured for this client. Try `withSslContext` on the `BlazeClientBuilder`, or do not make https calls."))
        }

      case _ =>
        Right((builder, t))
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

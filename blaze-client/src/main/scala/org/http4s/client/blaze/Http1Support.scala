package org.http4s.client.blaze

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.headers.`User-Agent`
import org.http4s.util.task
import org.http4s.Uri
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.SSLStage

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.concurrent.Future

import scalaz.concurrent.Task

import scalaz.{-\/, \/-}

/** Provides basic HTTP1 pipeline building
  *
  * Also serves as a non-recycling [[ConnectionManager]] */
final class Http1Support(bufferSize: Int,
                            timeout: Duration,
                          userAgent: Option[`User-Agent`],
                                 es: ExecutorService,
                        osslContext: Option[SSLContext],
                              group: Option[AsynchronousChannelGroup])
  extends ConnectionBuilder with ConnectionManager
{

  private val ec = ExecutionContext.fromExecutorService(es)

  private val sslContext = osslContext.getOrElse(bits.sslContext)
  private val connectionManager = new ClientChannelFactory(bufferSize, group.orNull)

  /** Get a connection to the provided address
    * @param uri [[Uri]] to connect too
    * @param fresh if the client should force a new connection
    * @return a Future with the connected [[BlazeClientStage]] of a blaze pipeline
    */
  override def getClient(uri: Uri, fresh: Boolean): Task[BlazeClientStage] =
    makeClient(uri)

  /** Free resources associated with this client factory */
  override def shutdown(): Task[Unit] = Task.now(())

////////////////////////////////////////////////////

  def makeClient(uri: Uri): Task[BlazeClientStage] = uri.asAddress match {
    case \/-(a) => task.futureToTask(buildPipeline(uri, a))(ec)
    case -\/(t) => Task.fail(t)
  }

  private def buildPipeline(uri: Uri, addr: InetSocketAddress): Future[BlazeClientStage] = {
    connectionManager.connect(addr, bufferSize).map { head =>
      val (builder, t) = buildStages(uri)
      builder.base(head)
      t
    }(ec)
  }

  private def buildStages(uri: Uri): (LeafBuilder[ByteBuffer], BlazeClientStage) = {
    val t = new Http1ClientStage(userAgent, timeout)(ec)
    val builder = LeafBuilder(t)
    uri match {
      case Uri(Some(Uri.Https),_,_,_,_) =>
        val eng = sslContext.createSSLEngine()
        eng.setUseClientMode(true)
        (builder.prepend(new SSLStage(eng)),t)

      case _ => (builder, t)
    }
  }
}

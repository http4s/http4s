package org.http4s.client.blaze

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.Uri.Scheme
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.headers.`User-Agent`
import org.http4s.util.task
import org.http4s.{Uri, Request}
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.util.CaseInsensitiveString._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.concurrent.Future

import scalaz.concurrent.Task

import scalaz.{\/, -\/, \/-}

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
  import Http1Support._

  private val ec = ExecutionContext.fromExecutorService(es)

  private val sslContext = osslContext.getOrElse(bits.sslContext)
  private val connectionManager = new ClientChannelFactory(bufferSize, group.orNull)

  /** Get a connection to the provided address
    * @param request [[Request]] to connect too
    * @param fresh if the client should force a new connection
    * @return a Future with the connected [[BlazeClientStage]] of a blaze pipeline
    */
  override def getClient(request: Request, fresh: Boolean): Task[BlazeClientStage] =
    makeClient(request)

  /** Free resources associated with this client factory */
  override def shutdown(): Task[Unit] = Task.now(())

////////////////////////////////////////////////////

  def makeClient(req: Request): Task[BlazeClientStage] = getAddress(req) match {
    case \/-(a) => task.futureToTask(buildPipeline(req, a))(ec)
    case -\/(t) => Task.fail(t)
  }

  private def buildPipeline(req: Request, addr: InetSocketAddress): Future[BlazeClientStage] = {
    connectionManager.connect(addr, bufferSize).map { head =>
      val (builder, t) = buildStages(req.uri)
      builder.base(head)
      t
    }(ec)
  }

  private def buildStages(uri: Uri): (LeafBuilder[ByteBuffer], BlazeClientStage) = {
    val t = new Http1ClientStage(userAgent, timeout)(ec)
    val builder = LeafBuilder(t)
    uri match {
      case Uri(Some(Https),Some(auth),_,_,_) =>
        val eng = sslContext.createSSLEngine(auth.host.value, auth.port getOrElse 443)
        eng.setUseClientMode(true)

        val sslParams = eng.getSSLParameters
        sslParams.setEndpointIdentificationAlgorithm("HTTPS")
        eng.setSSLParameters(sslParams)

        (builder.prepend(new SSLStage(eng)),t)
      case Uri(Some(Https),_,_,_,_) =>
        val eng = sslContext.createSSLEngine()
        eng.setUseClientMode(true)
        (builder.prepend(new SSLStage(eng)),t)

      case _ => (builder, t)
    }
  }

  private def getAddress(req: Request): Throwable\/InetSocketAddress = {
    req.uri match {
      case Uri(_,None,_,_,_)       => -\/(new IOException("Request must have an authority"))
      case Uri(s,Some(auth),_,_,_) =>
        val port = auth.port orElse s.map{ s => if (s == Https) 443 else 80 } getOrElse 80
        val host = auth.host.value
        \/-(new InetSocketAddress(host, port))
    }
  }
}

private object Http1Support {
  private val Https: Scheme = "https".ci
  private val Http: Scheme  = "http".ci
}

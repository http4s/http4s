package org.http4s.client.blaze

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import javax.net.ssl.SSLContext

import org.http4s.blaze.channel.nio2.ClientChannelFactory
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
  * Also serves as a non-recycling [[ConnectionManager]]
  * */
final class Http1Support(bufferSize: Int,
                            timeout: Duration,
                                 ec: ExecutionContext,
                        osslContext: Option[SSLContext],
                              group: Option[AsynchronousChannelGroup])
  extends ConnectionBuilder with ConnectionManager
{

  private val Https = "https".ci
  private val Http = "http".ci
  
  private val sslContext = osslContext.getOrElse(bits.sslContext)

  /** Get a connection to the provided address
    * @param request [[Request]] to connect too
    * @param fresh if the client should force a new connection
    * @return a Future with the connected [[BlazeClientStage]] of a blaze pipeline
    */
  override def getClient(request: Request, fresh: Boolean): Future[BlazeClientStage] =
    makeClient(request)

  /** Free resources associated with this client factory */
  override def shutdown(): Task[Unit] = Task.now(())

  protected val connectionManager = new ClientChannelFactory(bufferSize, group.orNull)

////////////////////////////////////////////////////

  def makeClient(req: Request): Future[BlazeClientStage] =
    getAddress(req).fold(Future.failed, buildPipeline(req, _))

  private def buildPipeline(req: Request, addr: InetSocketAddress): Future[BlazeClientStage] = {
    connectionManager.connect(addr, bufferSize).map { head =>
      val (builder, t) = buildStages(req)
      builder.base(head)
      t
    }(ec)
  }

  private def buildStages(req: Request): (LeafBuilder[ByteBuffer], BlazeClientStage) = {
    val t = new Http1ClientStage(timeout)(ec)
    val builder = LeafBuilder(t)
    req.uri match {
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

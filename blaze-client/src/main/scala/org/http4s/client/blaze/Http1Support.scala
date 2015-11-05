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
                 group: Option[AsynchronousChannelGroup]): ConnectionBuilder = {
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
    val t = new Http1ClientStage(userAgent, ec)
    val builder = LeafBuilder(t)
    uri match {
      case Uri(Some(Https),Some(auth),_,_,_) if endpointAuthentication =>
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


package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup

import org.http4s.Request
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.blaze.util.Execution

import scala.concurrent.{ExecutionContext, Future}
import scalaz.concurrent.Task


/** A default implementation of the Blaze Asynchronous client for HTTP/1.x */
abstract class SimpleHttp1Client(bufferSize: Int, group: Option[AsynchronousChannelGroup])
       extends BlazeClient
          with Http1Support
          with Http1SSLSupport
{
  override implicit protected def ec: ExecutionContext = Execution.trampoline

    /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = Task.now(())

  protected val connectionManager = new ClientChannelFactory(bufferSize, group.getOrElse(null))

  protected def getClient(req: Request, fresh: Boolean): Future[BlazeClientStage] = {
    getAddress(req).fold(Future.failed, addr =>
      connectionManager.connect(addr, bufferSize).map { head =>
        val PipelineResult(builder, t) = buildPipeline(req, true)
        builder.base(head)
        t
    })
  }
}

object SimpleHttp1Client extends SimpleHttp1Client(8*1024, None)
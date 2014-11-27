package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup

import org.http4s.Request
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.blaze.util.Execution

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scalaz.concurrent.Task


/** A default implementation of the Blaze Asynchronous client for HTTP/1.x */
class SimpleHttp1Client(timeout: Duration,
                     bufferSize: Int,
                       executor: ExecutionContext,
                          group: Option[AsynchronousChannelGroup])
       extends BlazeClient
          with Http1Support
          with Http1SSLSupport
{
  final override implicit protected def ec: ExecutionContext = executor

    /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = Task.now(())

  protected val connectionManager = new ClientChannelFactory(bufferSize, group.orNull)

  protected def getClient(req: Request, fresh: Boolean): Future[BlazeClientStage] = {
    getAddress(req).fold(Future.failed, addr =>
      connectionManager.connect(addr, bufferSize).map { head =>
        val PipelineResult(builder, t) = buildPipeline(req, true, timeout)
        builder.base(head)
        t
    })
  }
}

object SimpleHttp1Client extends SimpleHttp1Client(defaultTimeout, defaultBufferSize, defaultEC, None)
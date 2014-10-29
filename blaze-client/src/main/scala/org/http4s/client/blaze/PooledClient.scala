package org.http4s.client.blaze

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import org.http4s.Request
import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.blaze.util.Execution
import org.log4s.getLogger

import scala.collection.mutable.Queue
import scala.concurrent.{ExecutionContext, Future}
import scalaz.concurrent.Task
import scalaz.stream.Process.halt


/** Provides a foundation for pooling clients */
abstract class PooledClient(maxPooledConnections: Int,
                            bufferSize: Int,
                            group: Option[AsynchronousChannelGroup]) extends BlazeClient {
  private[this] val logger = getLogger

  assert(maxPooledConnections > 0, "Must have positive collection pool")

  override implicit protected def ec: ExecutionContext = Execution.trampoline

  private var closed = false
  private val cs = new Queue[(InetSocketAddress, BlazeClientStage)]()

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = Task {
    logger.debug("Shutting down PooledClient.")
    cs.synchronized {
      closed = true
      cs.foreach { case (_, s) => s.shutdown() }
    }
  }

  protected val connectionManager = new ClientChannelFactory(bufferSize, group.getOrElse(null))

  override protected def recycleClient(request: Request, stage: BlazeClientStage): Unit = cs.synchronized {
    if (closed) stage.shutdown()
    else {
      getAddress(request).foreach { addr =>
        logger.debug("Recycling connection.")
        cs += ((addr, stage))
      }

      while (cs.size >= maxPooledConnections) {  // drop connections until the pool will fit this connection
        logger.trace(s"Shutting down connection due to pool excess: Max: $maxPooledConnections")
        val (_, stage) = cs.dequeue()
        stage.shutdown()
      }
    }
  }

  protected def getClient(request: Request, fresh: Boolean): Future[BlazeClientStage] = cs.synchronized {
    if (closed) Future.failed(new Exception("Client is closed"))
    else {
      getAddress(request).fold(Future.failed, addr => {
        cs.dequeueFirst{ case (iaddr, _) => addr == iaddr } match {
          case Some((_,stage)) => Future.successful(stage)
          case None            => newConnection(request, addr)
        }
      })

    }
  }

  private def newConnection(request: Request, addr: InetSocketAddress): Future[BlazeClientStage] = {
    logger.debug(s"Generating new connection for request: ${request.copy(body = halt)}")
    connectionManager.connect(addr).map { head =>
      val PipelineResult(builder, t) = buildPipeline(request, false)
      builder.base(head)
      t
    }
  }
}

package org.http4s.client.blaze

import org.http4s.Request
import org.http4s.Uri.{Authority, Scheme}
import org.log4s.getLogger

import scala.collection.mutable
import scalaz.concurrent.Task


/* implementation bits for the pooled client manager */
private final class PoolManager (maxPooledConnections: Int, builder: ConnectionBuilder) extends ConnectionManager {

  require(maxPooledConnections > 0, "Must have finite connection pool size")

  private case class Connection(scheme: Option[Scheme], auth: Option[Authority], stage: BlazeClientStage)

  private[this] val logger = getLogger
  private var closed = false  // All access in synchronized blocks, no need to be volatile
  private val cs = new mutable.Queue[Connection]()

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = Task.delay {
    logger.debug(s"Shutting down ${getClass.getName}.")
    cs.synchronized {
      if(!closed) {
        closed = true
        cs.foreach(_.stage.shutdown())
      }
    }
  }

  override def recycleClient(request: Request, stage: BlazeClientStage): Unit =
    cs.synchronized {
      if (closed) stage.shutdown()
      else {
          logger.debug("Recycling connection.")
          cs += Connection(request.uri.scheme, request.uri.authority, stage)

        while (cs.size >= maxPooledConnections) {  // drop connections until the pool will fit this connection
          logger.trace(s"Shutting down connection due to pool excess: Max: $maxPooledConnections")
          val Connection(_, _, stage) = cs.dequeue()
          stage.shutdown()
        }
      }
    }

  override def getClient(request: Request, freshClient: Boolean): Task[BlazeClientStage] = Task.suspend {
    cs.synchronized {
      if (closed) Task.fail(new Exception("Client is closed"))
      else if (freshClient) builder(request)
      else cs.dequeueFirst { case Connection(sch, auth, _) =>
        sch == request.uri.scheme && auth == request.uri.authority
      } match {
        case Some(Connection(_, _, stage)) => Task.now(stage)
        case None => builder(request)
      }
    }
  }
}

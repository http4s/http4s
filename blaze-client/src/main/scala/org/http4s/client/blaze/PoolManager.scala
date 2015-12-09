package org.http4s.client.blaze

import java.nio.ByteBuffer

import org.apache.commons.pool2.{PooledObject, BaseKeyedPooledObjectFactory}
import org.apache.commons.pool2.impl.{GenericKeyedObjectPoolConfig, DefaultPooledObject, GenericKeyedObjectPool}
import org.http4s.Request
import org.http4s.Uri.{Authority, Scheme}
import org.http4s.blaze.pipeline.Command.InboundCommand
import org.log4s.getLogger

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scalaz.concurrent.Task

/* implementation bits for the pooled client manager */
private final class PoolManager(builder: ConnectionBuilder,
                                maxTotal: Int,
                                maxTotalPerKey: Int)
  extends ConnectionManager
{
  private[this] val logger = getLogger

  private def closed = pool.isClosed

  private val pool = {
    val factory = new BaseKeyedPooledObjectFactory[RequestKey, BlazeClientStage] {
      override def wrap(value: BlazeClientStage) = new DefaultPooledObject(value)
      override def create(key: RequestKey) = builder(key)./*gulp*/run/*gulp*/
      override def validateObject(key: RequestKey, p: PooledObject[BlazeClientStage]): Boolean =
        !p.getObject.isClosed
      override def destroyObject(key: RequestKey, p: PooledObject[BlazeClientStage]): Unit =
        p.getObject.shutdown()
    }
    val config = new GenericKeyedObjectPoolConfig
    config.setMaxTotal(maxTotal)
    config.setMaxTotalPerKey(maxTotalPerKey)
    config.setTestOnBorrow(true)
    new GenericKeyedObjectPool[RequestKey, BlazeClientStage](factory)
  }

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = Task.delay {
    pool.close()
  }

  override def recycleClient(request: Request, stage: BlazeClientStage): Unit = {
    val key = RequestKey.fromRequest(request)
    logger.debug(s"Returning connection to ${key}.")
    pool.returnObject(key, stage)
  }

  override def getClient(request: Request, ignored: Boolean): Task[BlazeClientStage] = Task.suspend {
    if (closed) Task.fail(new Exception("Client is closed"))
    else {
      val key = RequestKey.fromRequest(request)
      Task {
        logger.debug(s"Borrowing connection to ${key}.")
        pool.borrowObject(key)
      }
    }
  }
}

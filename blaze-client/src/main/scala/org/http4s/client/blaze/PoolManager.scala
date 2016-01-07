package org.http4s
package client
package blaze

import org.log4s.getLogger

import scala.collection.mutable
import scalaz.{-\/, \/-, \/}
import scalaz.syntax.either._
import scalaz.concurrent.{Actor, Task}

private object PoolManager {
  type Callback[A] = Throwable \/ A => Unit
  sealed trait Protocol
  case class RequestConnection(key: RequestKey, callback: Callback[BlazeClientStage]) extends Protocol
  case class ReturnConnection(key: RequestKey, stage: BlazeClientStage) extends Protocol
  case class DisposeConnection(key: RequestKey, stage: Option[BlazeClientStage]) extends Protocol
  case class Shutdown(callback: Callback[Unit]) extends Protocol
}
import PoolManager._

private final class PoolManager(builder: ConnectionBuilder,
                                maxTotal: Int)
  extends ConnectionManager {

  private[this] val logger = getLogger
  private var isClosed = false
  private var allocated = 0
  private val idleQueue = new mutable.Queue[BlazeClientStage]
  private val waitQueue = new mutable.Queue[Callback[BlazeClientStage]]()

  private def stats =
    s"allocated=${allocated} idleQueue.size=${idleQueue.size} waitQueue.size=${waitQueue.size}"

  private def createConnection(key: RequestKey): Unit = {
    if (allocated < maxTotal) {
      allocated += 1
      logger.debug(s"Creating connection: ${stats}")
      Task.fork(builder(key)).runAsync {
        case \/-(stage) =>
          logger.debug(s"Submitting fresh connection to pool: ${stats}")
          actor ! ReturnConnection(key, stage)
        case -\/(t) =>
          logger.error(t)("Error establishing client connection")
          actor ! DisposeConnection(key, None)
      }
    }
    else {
      logger.debug(s"Too many connections open.  Can't create a connection: ${stats}")
    }
  }

  private val actor = Actor[Protocol] {
    case RequestConnection(key, callback) =>
      logger.debug(s"Requesting connection: ${stats}")
      if (!isClosed) {
        def go(): Unit = {
          idleQueue.dequeueFirst(_.requestKey == key) match {
            case Some(stage) if !stage.isClosed =>
              logger.debug(s"Recycling connection: ${stats}")
              callback(stage.right)
            case Some(closedStage) =>
              logger.debug(s"Evicting closed connection: ${stats}")
              allocated -= 1
              go()
            case None =>
              logger.debug(s"No connections available.  Waiting on new connection: ${stats}")
              createConnection(key)
              waitQueue.enqueue(callback)
          }
        }
        go()
      }
      else
        callback(new IllegalStateException("Connection pool is closed").left)

    case ReturnConnection(key, stage) =>
      logger.debug(s"Reallocating connection: ${stats}")
      if (!isClosed) {
        if (!stage.isClosed) {
          stage.reset()
          if (waitQueue.nonEmpty) {
            logger.debug(s"Fulfilling waiting connection request: ${stats}")
            waitQueue.dequeue.apply(stage.right)
          }
          else {
            logger.debug(s"Returning idle connection to pool: ${stats}")
            idleQueue.enqueue(stage)
          }
        }
        else if (waitQueue.nonEmpty) {
          logger.debug(s"Replacing closed connection: ${stats}")
          allocated -= 1
          createConnection(key)
        }
        else {
          logger.debug(s"Connection was closed, but nothing to do. Shrinking pool: ${stats}")
          allocated -= 1
        }
      }
      else if (!stage.isClosed) {
        logger.debug(s"Shutting down connection after pool closure: ${stats}")
        stage.shutdown()
        allocated -= 1
      }

    case DisposeConnection(key, stage) =>
      logger.debug(s"Disposing of connection: ${stats}")
      allocated -= 1
      stage.foreach { s => if (!s.isClosed()) s.shutdown() }
      if (!isClosed && waitQueue.nonEmpty) {
        logger.debug(s"Replacing failed connection: ${stats}")
        createConnection(key)
      }

    case Shutdown(callback) =>
      callback(\/.fromTryCatchNonFatal {
        logger.info(s"Shutting down connection pool: ${stats}")
        if (!isClosed) {
          isClosed = true
          idleQueue.foreach(_.shutdown())
          allocated = 0
        }
      })
  }

  override def shutdown(): Task[Unit] =
    Task.async(actor ! Shutdown(_))

  override def getClient(requestKey: RequestKey): Task[BlazeClientStage] =
    Task.async[BlazeClientStage](actor ! RequestConnection(requestKey, _))

  override def releaseClient(requestKey: RequestKey, stage: BlazeClientStage, keepAlive: Boolean): Unit = {
    val msg = if (keepAlive)
      ReturnConnection(requestKey, stage)
    else
      DisposeConnection(requestKey, Some(stage))
    actor ! msg
  }
}

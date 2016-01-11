package org.http4s
package client
package blaze

import org.log4s.getLogger

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.{-\/, \/-, \/}
import scalaz.syntax.either._
import scalaz.concurrent.Task

private object PoolManager {
  type Callback[A] = Throwable \/ A => Unit
  case class Waiting(key: RequestKey, callback: Callback[BlazeClientStage])
}
import PoolManager._

private final class PoolManager(builder: ConnectionBuilder,
                                maxTotal: Int)
  extends ConnectionManager {

  private[this] val logger = getLogger
  private var isClosed = false
  private var allocated = 0
  private val idleQueue = new mutable.Queue[BlazeClientStage]
  private val waitQueue = new mutable.Queue[Waiting]

  private def stats =
    s"allocated=${allocated} idleQueue.size=${idleQueue.size} waitQueue.size=${waitQueue.size}"

  private def createConnection(key: RequestKey, callback: Callback[BlazeClientStage]): Unit = {
    if (allocated < maxTotal) {
      allocated += 1
      Task.fork(builder(key)).runAsync {
        case s@ \/-(stage) =>
          logger.debug(s"Received complete connection from pool: ${stats}")
          callback(s)
        case e@ -\/(t) =>
          logger.error(t)(s"Error establishing client connection for key $key")
          disposeConnection(key, None)
          callback(e)
      }
    }
    else {
      logger.debug(s"Too many connections open.  Can't create a connection: ${stats}")
    }
  }

  def borrow(key: RequestKey): Task[BlazeClientStage] = Task.async { callback =>
    logger.debug(s"Requesting connection: ${stats}")
    synchronized {
      if (!isClosed) {
        @tailrec
        def go(): Unit = {
          idleQueue.dequeueFirst(_.requestKey == key) match {
            case Some(stage) if !stage.isClosed =>
              logger.debug(s"Recycling connection: ${stats}")
              callback(stage.right)

            case Some(closedStage) =>
              logger.debug(s"Evicting closed connection: ${stats}")
              allocated -= 1
              go()

            case None if allocated < maxTotal =>
              logger.debug(s"Active connection not found. Creating new one. ${stats}")
              createConnection(key, callback)

            case None if idleQueue.nonEmpty =>
              logger.debug(s"No connections available for the desired key. Evicting oldest and creating a new connection: ${stats}")
              allocated -= 1
              idleQueue.dequeue().shutdown()
              createConnection(key, callback)

            case None => // we're full up. Add to waiting queue.
              logger.debug(s"No connections available.  Waiting on new connection: ${stats}")
              waitQueue.enqueue(Waiting(key, callback))
          }
        }
        go()
      }
      else
        callback(new IllegalStateException("Connection pool is closed").left)
    }
  }

  def release(stage: BlazeClientStage) = Task.delay {
    synchronized {
      if (!isClosed) {
        logger.debug(s"Recycling connection: ${stats}")
        val key = stage.requestKey
        if (!stage.isClosed) {
          waitQueue.dequeueFirst(_.key == key) match {
            case Some(Waiting(_, callback)) =>
              logger.debug(s"Fulfilling waiting connection request: ${stats}")
              callback(stage.right)

            case None if waitQueue.isEmpty =>
              logger.debug(s"Returning idle connection to pool: ${stats}")
              idleQueue.enqueue(stage)

            // returned connection didn't match any pending request: kill it and start a new one for a queued request
            case None =>
              stage.shutdown()
              allocated -= 1
              val Waiting(key, callback) = waitQueue.dequeue()
              createConnection(key, callback)
          }
        }
        else {
          // stage was closed
          allocated -= 1

          if (waitQueue.nonEmpty) {
            logger.debug(s"Connection returned in the close state, new connection needed: ${stats}")
            val Waiting(key, callback) = waitQueue.dequeue()
            createConnection(key, callback)
          }
          else logger.debug(s"Connection is closed; no pending requests. Shrinking pool: ${stats}")
        }
      }
      else if (!stage.isClosed) {
        logger.debug(s"Shutting down connection after pool closure: ${stats}")
        stage.shutdown()
        allocated -= 1
      }
    }
  }

  override def dispose(stage: BlazeClientStage): Task[Unit] =
    Task.delay(disposeConnection(stage.requestKey, Some(stage)))

  private def disposeConnection(key: RequestKey, stage: Option[BlazeClientStage]) = {
    logger.debug(s"Disposing of connection: ${stats}")
    synchronized {
      allocated -= 1
      stage.foreach { s => if (!s.isClosed) s.shutdown() }
    }
  }

  def shutdown() = Task.delay {
    logger.info(s"Shutting down connection pool: ${stats}")
    synchronized {
      if (!isClosed) {
        isClosed = true
        idleQueue.foreach(_.shutdown())
        allocated = 0
      }
    }
  }
}

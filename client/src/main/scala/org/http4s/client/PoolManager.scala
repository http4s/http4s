package org.http4s
package client

import java.util.concurrent.ExecutorService
import org.log4s.getLogger
import scala.annotation.tailrec
import scala.collection.mutable
import fs2._
import cats.implicits._

private final class PoolManager[A <: Connection](builder: ConnectionBuilder[A],
                                                 maxTotal: Int,
                                                 es: ExecutorService)
  extends ConnectionManager[A] {

  private sealed case class Waiting(key: RequestKey, callback: Callback[NextConnection])

  implicit val strategy : Strategy = Strategy.fromFixedDaemonPool(maxTotal, "workerPool" )

  private[this] val logger = getLogger
  private var isClosed = false
  private var allocated = 0
  private val idleQueue = new mutable.Queue[A]
  private val waitQueue = new mutable.Queue[Waiting]

  private def stats =
    s"allocated=${allocated} idleQueue.size=${idleQueue.size} waitQueue.size=${waitQueue.size}"

  private def createConnection(key: RequestKey, callback: Callback[NextConnection]): Task[Unit] = {
    if (allocated < maxTotal){
      builder(key).map(conn => callback(Right(NextConnection(conn, true))))
    }
    else {
      val message = s"Invariant broken in ${this.getClass.getSimpleName}! Tried to create more connections than allowed: ${stats}"
      val error = new Exception(message)
      disposeConnection(key, None)
      logger.error(error)(message)
      Task.now(callback(Left(error)))
    }
  }

   def borrow(key: RequestKey): Task[NextConnection] = Task.async{ callback =>
     logger.debug(s"Requesting connection: ${stats}")
     synchronized {
       if (!isClosed) {
         @tailrec
         def go(): Unit = {
           idleQueue.dequeueFirst(_.requestKey == key) match {
             case Some(conn) if !conn.isClosed =>
               logger.debug(s"Recycling connection: ${stats}")
               callback(Right(NextConnection(conn, false)))

             case Some(closedConn) =>
               logger.debug(s"Evicting closed connection: ${stats}")
               allocated -= 1
               go()

             case None if allocated < maxTotal =>
               logger.debug(s"Active connection not found. Creating new one. ${stats}")
               createConnection(key, callback).unsafeRun()

             case None if idleQueue.nonEmpty =>
               logger.debug(s"No connections available for the desired key. Evicting oldest and creating a new connection: ${stats}")
               allocated -= 1
               idleQueue.dequeue().shutdown()
               createConnection(key, callback).unsafeRun()

             case None => // we're full up. Add to waiting queue.
               logger.debug(s"No connections available.  Waiting on new connection: ${stats}")
               waitQueue.enqueue(Waiting(key, callback))
           }
         }
         go()
       }
       else {
         callback(Left(new IllegalStateException("Connection pool is closed")))
       }
     }
   }

  def release(connection: A): Task[Unit] = Task.delay {
    synchronized {
      if (!isClosed) {
        logger.debug(s"Recycling connection: ${stats}")
        val key = connection.requestKey
        if (connection.isRecyclable) {
          waitQueue.dequeueFirst(_.key == key) match {
            case Some(Waiting(_, callback)) =>
              logger.debug(s"Fulfilling waiting connection request: ${stats}")
              callback(Right(NextConnection(connection, false)))

            case None if waitQueue.isEmpty =>
              logger.debug(s"Returning idle connection to pool: ${stats}")
              idleQueue.enqueue(connection)

            // returned connection didn't match any pending request: kill it and start a new one for a queued request
            case None =>
              connection.shutdown()
              allocated -= 1
              val Waiting(key, callback) = waitQueue.dequeue()
              createConnection(key, callback).unsafeRun()
          }
        }
        else {
          allocated -= 1

          if (!connection.isClosed) {
            logger.debug(s"Connection returned was busy.  Shutting down: ${stats}")
            connection.shutdown()
          }

          if (waitQueue.nonEmpty) {
            logger.debug(s"Connection returned could not be recycled, new connection needed: ${stats}")
            val Waiting(key, callback) = waitQueue.dequeue()
            createConnection(key, callback).unsafeRun()
          }
          else logger.debug(s"Connection could not be recycled, no pending requests. Shrinking pool: ${stats}")
        }
      }
      else if (!connection.isClosed) {
        logger.debug(s"Shutting down connection after pool closure: ${stats}")
        connection.shutdown()
        allocated -= 1
      }
    }
  }

  override def invalidate(connection: A): Task[Unit] =
    Task.delay(disposeConnection(connection.requestKey, Some(connection)))

  private def disposeConnection(key: RequestKey, connection: Option[A]): Unit = {
    logger.debug(s"Disposing of connection: ${stats}")
    synchronized {
      allocated -= 1
      connection.foreach { s => if (!s.isClosed) s.shutdown() }
    }
  }

  def shutdown() : Task[Unit] = Task.delay {
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

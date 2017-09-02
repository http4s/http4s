package org.http4s
package client

import cats.effect._
import fs2.async
import org.log4s.getLogger

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext

private final class PoolManager[F[_], A <: Connection[F]](
    builder: ConnectionBuilder[F, A],
    maxTotal: Int,
    maxConnectionsPerRequestKey: Map[RequestKey, Int],
    implicit private val executionContext: ExecutionContext)(implicit F: Effect[F])
    extends ConnectionManager[F, A] {

  private sealed case class Waiting(key: RequestKey, callback: Callback[NextConnection])

  private[this] val logger = getLogger(classOf[PoolManager[F, A]])

  private var isClosed = false
  private var curTotal = 0
  private var allocated = mutable.Map.empty[RequestKey, Int]
  private val idleQueue = mutable.Map.empty[RequestKey, mutable.Queue[A]]
  private val waitQueue = mutable.Queue.empty[Waiting]

  private def stats =
    s"allocated=$allocated idleQueue.size=${idleQueue.size} waitQueue.size=${waitQueue.size}"

  @inline
  private def getMaxConnections(key: RequestKey): Int =
    maxConnectionsPerRequestKey.getOrElse(key, maxTotal)

  @inline
  private def getConnectionFromQueue(key: RequestKey): Option[A] =
    idleQueue.get(key).flatMap(q => if (q.nonEmpty) Some(q.dequeue()) else None)

  @inline
  private def incrConnection(key: RequestKey): Unit = {
    curTotal += 1
    allocated.update(key, allocated.getOrElse(key, 0) + 1)
  }

  @inline
  private def decrConnection(key: RequestKey): Unit = {
    curTotal -= 1
    val numConnections = allocated.getOrElse(key, 0)
    // If there are no more connections drop the key
    if (numConnections == 1) {
      allocated.remove(key)
    } else {
      allocated.update(key, numConnections - 1)
    }
  }

  /**
    * This method is the core method for creating a connection which increments allocated synchronously
    * then builds the connection with the given callback and completes the callback.
    *
    * If we can create a connection then it initially increments the allocated value within a region
    * that is called synchronously by the calling method. Then it proceeds to attempt to create the connection
    * and feed it the callback. If we cannot create a connection because we are already full then this
    * completes the callback on the error synchronously.
    *
    * @param key The RequestKey for the Connection.
    * @param callback The callback to complete with the NextConnection.
    */
  private def createConnection(key: RequestKey, callback: Callback[NextConnection]): Unit =
    if (curTotal < maxTotal && allocated.getOrElse(key, 0) < getMaxConnections(key)) {
      incrConnection(key)
      async.unsafeRunAsync(builder(key)) {
        case Right(conn) =>
          IO(callback(Right(NextConnection(conn, fresh = true))))
        case Left(error) =>
          logger.error(error)(s"Error establishing client connection for key $key")
          disposeConnection(key, None)
          IO(callback(Left(error)))
      }
    } else {
      val message =
        s"Invariant broken in ${this.getClass.getSimpleName}! Tried to create more connections than allowed: ${stats}"
      val error = new Exception(message)
      logger.error(error)(message)
      callback(Left(error))
    }

  /**
    * This generates a Task of Next Connection. The following calls are executed asynchronously
    * with respect to whenever the execution of this task can occur.
    *
    * If the pool is closed The task failure is executed.
    *
    * If the pool is not closed then we look for any connections in the idleQueue that match
    * the RequestKey requested.
    * If a matching connection exists and it is stil open the callback is executed with the connection.
    * If a matching connection is closed we deallocate and repeat the check through the idleQueue.
    * If no matching connection is found, and the pool is not full we create a new Connection to perform
    * the request.
    * If no matching connection is found and the pool is full, and we have connections in the idleQueue
    * then a connection in the idleQueue is shutdown and a new connection is created to perform the request.
    * If no matching connection is found and the pool is full, and all connections are currently in use
    * then the Request is placed in a waitingQueue to be executed when a connection is released.
    *
    * @param key The Request Key For The Connection
    * @return A Task of NextConnection
    */
  def borrow(key: RequestKey): F[NextConnection] =
    F.async { callback =>
      logger.debug(s"Requesting connection: $stats")
      synchronized {
        if (!isClosed) {
          @tailrec
          def go(): Unit =
            getConnectionFromQueue(key) match {
              case Some(conn) if !conn.isClosed =>
                logger.debug(s"Recycling connection: $stats")
                callback(Right(NextConnection(conn, fresh = false)))

              case Some(closedConn) =>
                logger.debug(s"Evicting closed connection: $stats")
                decrConnection(key)
                go()

              case None
                  if curTotal < maxTotal && allocated.getOrElse(key, 0) < getMaxConnections(key) =>
                logger.debug(s"Active connection not found. Creating new one. $stats")
                createConnection(key, callback)

              case None if idleQueue.get(key).exists(_.nonEmpty) =>
                logger.debug(
                  s"No connections available for the desired key. Evicting oldest and creating a new connection: $stats")
                decrConnection(key)
                getConnectionFromQueue(key).get.shutdown()
                createConnection(key, callback)

              case None => // we're full up. Add to waiting queue.
                logger.debug(s"No connections available.  Waiting on new connection: $stats")
                waitQueue.enqueue(Waiting(key, callback))
            }
          go()
        } else {
          callback(Left(new IllegalStateException("Connection pool is closed")))
        }
      }
    }

  /**
    * This is how connections are returned to the ConnectionPool.
    *
    * If the pool is closed the connection is shutdown and logged.
    * If it is not closed we check if the connection is recyclable.
    *
    * If the connection is Recyclable we check if any of the connections in the waitQueue
    * are looking for the returned connections RequestKey.
    * If one is the first found is given the connection.And runs it using its callback asynchronously.
    * If one is not found and the waitingQueue is Empty then we place the connection on the idle queue.
    * If the waiting queue is not empty and we did not find a match then we shutdown the connection
    * and create a connection for the first item in the waitQueue.
    *
    * If it is not recyclable, and it is not shutdown we shutdown the connection. If there
    * are values in the waitQueue we create a connection and execute the callback asynchronously.
    * Otherwise the pool is shrunk.
    *
    * @param connection The connection to be released.
    * @return A Task of Unit
    */
  def release(connection: A): F[Unit] = F.delay {
    synchronized {
      if (!isClosed) {
        logger.debug(s"Recycling connection: $stats")
        val key = connection.requestKey
        if (connection.isRecyclable) {
          waitQueue.dequeueFirst(_.key == key) match {
            case Some(Waiting(_, callback)) =>
              logger.debug(s"Fulfilling waiting connection request: $stats")
              callback(Right(NextConnection(connection, fresh = false)))

            case None if waitQueue.isEmpty =>
              logger.debug(s"Returning idle connection to pool: $stats")
              val q = idleQueue.getOrElse(key, mutable.Queue.empty[A])
              q.enqueue(connection)
              idleQueue.update(key, q)

            // returned connection didn't match any pending request: kill it and start a new one for a queued request
            case None =>
              connection.shutdown()
              decrConnection(key)
              val Waiting(k, callback) = waitQueue.dequeue()
              createConnection(k, callback)
          }
        } else {
          decrConnection(key)

          if (!connection.isClosed) {
            logger.debug(s"Connection returned was busy.  Shutting down: $stats")
            connection.shutdown()
          }

          if (waitQueue.nonEmpty) {
            logger.debug(
              s"Connection returned could not be recycled, new connection needed: $stats")
            val Waiting(key, callback) = waitQueue.dequeue()
            createConnection(key, callback)
          } else
            logger.debug(
              s"Connection could not be recycled, no pending requests. Shrinking pool: $stats")
        }
      } else if (!connection.isClosed) {
        logger.debug(s"Shutting down connection after pool closure: $stats")
        val key = connection.requestKey
        connection.shutdown()
        decrConnection(key)
      }
    }
  }

  /**
    * This invalidates a Connection. This is what is exposed externally, and
    * is just a Task wrapper around disposing the connection.
    *
    * @param connection The connection to invalidate
    * @return A Task of Unit
    */
  override def invalidate(connection: A): F[Unit] =
    F.delay(disposeConnection(connection.requestKey, Some(connection)))

  /**
    * Synchronous Immediate Disposal of a Connection and Its Resources.
    *
    * By taking an Option of a connection this also serves as a synchronized allocated decrease.
    *
    * @param key The request key for the connection. Not used internally.
    * @param connection An Option of a Connection to Dispose Of.
    */
  private def disposeConnection(key: RequestKey, connection: Option[A]): Unit = {
    logger.debug(s"Disposing of connection: $stats")
    synchronized {
      decrConnection(key)
      connection.foreach { s =>
        if (!s.isClosed) s.shutdown()
      }
    }
  }

  /**
    * Shuts down the connection pool permanently.
    *
    * Changes isClosed to true, no methods can reopen a closed Pool.
    * Shutdowns all connections in the IdleQueue and Sets Allocated to Zero
    *
    * @return A Task Of Unit
    */
  def shutdown(): F[Unit] = F.delay {
    logger.info(s"Shutting down connection pool: $stats")
    synchronized {
      if (!isClosed) {
        isClosed = true
        idleQueue.foreach(_._2.foreach(_.shutdown()))
        allocated = mutable.Map.empty
        curTotal = 0
      }
    }
  }
}

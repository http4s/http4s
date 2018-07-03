package org.http4s
package client

import cats.effect._
import java.time.Instant
import java.util.concurrent.TimeoutException
import org.log4s.getLogger
import org.http4s.internal.unsafeRunAsync
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

private final class PoolManager[F[_], A <: Connection[F]](
    builder: ConnectionBuilder[F, A],
    maxTotal: Int,
    maxWaitQueueLimit: Int,
    maxConnectionsPerRequestKey: RequestKey => Int,
    responseHeaderTimeout: Duration,
    requestTimeout: Duration,
    implicit private val executionContext: ExecutionContext)(implicit F: Effect[F])
    extends ConnectionManager[F, A] {

  private sealed case class Waiting(
      key: RequestKey,
      callback: Callback[NextConnection],
      at: Instant)

  private[this] val logger = getLogger

  private var isClosed = false
  private var curTotal = 0
  private val allocated = mutable.Map.empty[RequestKey, Int]
  private val idleQueues = mutable.Map.empty[RequestKey, mutable.Queue[A]]
  private var waitQueue = mutable.Queue.empty[Waiting]

  private def stats =
    s"curAllocated=$curTotal idleQueues.size=${idleQueues.size} waitQueue.size=${waitQueue.size} maxWaitQueueLimit=$maxWaitQueueLimit closed=${isClosed}"

  def statsForRequestKey(key: RequestKey): String = synchronized {
    s"allocated=${allocated.get(key)} idleQueues.size=${idleQueues.get(key).map(_.size)} waitQueue.size=${waitQueue.size} maxWaitQueueLimit=$maxWaitQueueLimit"
  }

  private def getConnectionFromQueue(key: RequestKey): Option[A] =
    idleQueues.get(key).flatMap { q =>
      if (q.nonEmpty) {
        val con = q.dequeue()
        if (q.isEmpty) idleQueues.remove(key)
        Some(con)
      } else None
    }

  private def incrConnection(key: RequestKey): Unit = {
    curTotal += 1
    allocated.update(key, allocated.getOrElse(key, 0) + 1)
  }

  private def decrConnection(key: RequestKey): Unit = {
    curTotal -= 1
    val numConnections = allocated.getOrElse(key, 0)
    // If there are no more connections drop the key
    if (numConnections == 1) {
      allocated.remove(key)
      idleQueues.remove(key)
      ()
    } else {
      allocated.update(key, numConnections - 1)
    }
  }

  private def numConnectionsCheckHolds(key: RequestKey): Boolean =
    curTotal < maxTotal && allocated.getOrElse(key, 0) < maxConnectionsPerRequestKey(key)

  private def isExpired(t: Instant): Boolean = {
    val elapsed = Instant.now().toEpochMilli - t.toEpochMilli
    (requestTimeout.isFinite() && elapsed >= requestTimeout.toMillis) || (responseHeaderTimeout
      .isFinite() && elapsed >= responseHeaderTimeout.toMillis)
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
    * @param key      The RequestKey for the Connection.
    * @param callback The callback to complete with the NextConnection.
    */
  private def createConnection(key: RequestKey, callback: Callback[NextConnection]): Unit =
    if (numConnectionsCheckHolds(key)) {
      incrConnection(key)
      unsafeRunAsync(builder(key)) {
        case Right(conn) =>
          IO(callback(Right(NextConnection(conn, fresh = true))))
        case Left(error) =>
          disposeConnection(key, None)
          IO(callback(Left(error)))
      }
    } else {
      addToWaitQueue(key, callback)
    }

  private def addToWaitQueue(key: RequestKey, callback: Callback[NextConnection]): Unit =
    if (waitQueue.length <= maxWaitQueueLimit) {
      waitQueue.enqueue(Waiting(key, callback, Instant.now()))
    } else {
      logger.error(s"Max wait length reached, not scheduling.")
      callback(Left(new Exception("Wait queue is full")))
    }

  private def addToIdleQueue(connection: A, key: RequestKey): Unit = {
    val q = idleQueues.getOrElse(key, mutable.Queue.empty[A])
    q.enqueue(connection)
    idleQueues.update(key, q)
  }

  /**
    * This generates a effect of Next Connection. The following calls are executed asynchronously
    * with respect to whenever the execution of this task can occur.
    *
    * If the pool is closed the effect failure is executed.
    *
    * If the pool is not closed then we look for any connections in the idleQueues that match
    * the RequestKey requested.
    * If a matching connection exists and it is stil open the callback is executed with the connection.
    * If a matching connection is closed we deallocate and repeat the check through the idleQueues.
    * If no matching connection is found, and the pool is not full we create a new Connection to perform
    * the request.
    * If no matching connection is found and the pool is full, and we have connections in the idleQueues
    * then a connection in the idleQueues is shutdown and a new connection is created to perform the request.
    * If no matching connection is found and the pool is full, and all connections are currently in use
    * then the Request is placed in a waitingQueue to be executed when a connection is released.
    *
    * @param key The Request Key For The Connection
    * @return An effect of NextConnection
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

              case Some(closedConn @ _) =>
                logger.debug(s"Evicting closed connection: $stats")
                decrConnection(key)
                go()

              case None if numConnectionsCheckHolds(key) =>
                logger.debug(s"Active connection not found. Creating new one. $stats")
                createConnection(key, callback)

              case None if maxConnectionsPerRequestKey(key) <= 0 =>
                callback(Left(NoConnectionAllowedException(key)))

              case None if curTotal == maxTotal =>
                val keys = idleQueues.keys
                if (keys.nonEmpty) {
                  logger.debug(
                    s"No connections available for the desired key. Evicting random and creating a new connection: $stats")
                  val randKey = keys.iterator.drop(Random.nextInt(keys.size)).next()
                  getConnectionFromQueue(randKey)
                    .fold(logger.warn(s"No connection to evict from the idleQueue for $randKey"))(
                      _.shutdown())
                  decrConnection(randKey)
                  createConnection(key, callback)
                } else {
                  logger.debug(
                    s"No connections available for the desired key. Adding to waitQueue: $stats")
                  addToWaitQueue(key, callback)
                }

              case None => // we're full up. Add to waiting queue.
                logger.debug(s"No connections available.  Waiting on new connection: $stats")
                addToWaitQueue(key, callback)
            }

          go()
        } else {
          callback(Left(new IllegalStateException("Connection pool is closed")))
        }
      }
    }

  private def releaseRecyclable(key: RequestKey, connection: A): Unit =
    waitQueue.dequeueFirst(_.key == key) match {
      case Some(Waiting(_, callback, at)) =>
        if (isExpired(at)) {
          logger.debug(s"Request expired")
          callback(Left(new TimeoutException("In wait queue for too long, timing out request.")))
        } else {
          logger.debug(s"Fulfilling waiting connection request: $stats")
          callback(Right(NextConnection(connection, fresh = false)))
        }

      case None if waitQueue.isEmpty =>
        logger.debug(s"Returning idle connection to pool: $stats")
        addToIdleQueue(connection, key)

      case None =>
        findFirstAllowedWaiter match {
          case Some(Waiting(k, cb, _)) =>
            // This is the first waiter not blocked on the request key limit.
            // close the undesired connection and wait for another
            connection.shutdown()
            decrConnection(key)
            createConnection(k, cb)

          case None =>
            // We're blocked not because of too many connections, but
            // because of too many connections per key.
            // We might be able to reuse this request.
            addToIdleQueue(connection, key)
        }
    }

  private def releaseNonRecyclable(key: RequestKey, connection: A): Unit = {
    decrConnection(key)

    if (!connection.isClosed) {
      logger.debug(s"Connection returned was busy.  Shutting down: $stats")
      connection.shutdown()
    }

    findFirstAllowedWaiter match {
      case Some(Waiting(k, callback, _)) =>
        logger.debug(s"Connection returned could not be recycled, new connection needed: $stats")
        createConnection(k, callback)

      case None =>
        logger.debug(
          s"Connection could not be recycled, no pending requests. Shrinking pool: $stats")
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
    * @return An effect of Unit
    */
  def release(connection: A): F[Unit] = F.delay {
    synchronized {
      logger.debug(s"Recycling connection: $stats")
      val key = connection.requestKey
      if (connection.isRecyclable) {
        releaseRecyclable(key, connection)
      } else {
        releaseNonRecyclable(key, connection)
      }
    }
  }

  private def findFirstAllowedWaiter = {
    val (expired, rest) = waitQueue.span(w => isExpired(w.at))
    expired.foreach(
      _.callback(Left(new TimeoutException("In wait queue for too long, timing out request."))))
    logger.debug(s"expired requests: ${expired.length}")
    waitQueue = rest
    logger.debug(s"Dropped expired requests: $stats")
    waitQueue.dequeueFirst { waiter =>
      allocated.getOrElse(waiter.key, 0) < maxConnectionsPerRequestKey(waiter.key)
    }
  }

  /**
    * This invalidates a Connection. This is what is exposed externally, and
    * is just an effect wrapper around disposing the connection.
    *
    * @param connection The connection to invalidate
    * @return An effect of Unit
    */
  override def invalidate(connection: A): F[Unit] =
    F.delay(synchronized {
      decrConnection(connection.requestKey)
      if (!connection.isClosed) connection.shutdown()
      findFirstAllowedWaiter match {
        case Some(Waiting(k, callback, _)) =>
          logger.debug(s"Invalidated connection, new connection needed: $stats")
          createConnection(k, callback)

        case None =>
          logger.debug(s"Invalidated connection, no pending requests. Shrinking pool: $stats")
      }
    })

  /**
    * Synchronous Immediate Disposal of a Connection and Its Resources.
    *
    * By taking an Option of a connection this also serves as a synchronized allocated decrease.
    *
    * @param key        The request key for the connection. Not used internally.
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
    * @return An effect Of Unit
    */
  def shutdown(): F[Unit] = F.delay {
    logger.info(s"Shutting down connection pool: $stats")
    synchronized {
      if (!isClosed) {
        isClosed = true
        idleQueues.foreach(_._2.foreach(_.shutdown()))
        idleQueues.clear()
        allocated.clear()
        curTotal = 0
      }
    }
  }
}

case class NoConnectionAllowedException(key: RequestKey)
    extends IllegalArgumentException(s"No client connections allowed to $key")

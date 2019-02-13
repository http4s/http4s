package org.http4s
package client

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._
import java.time.Instant
import java.util.concurrent.TimeoutException
import org.log4s.getLogger
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

final case class WaitQueueFullFailure() extends RuntimeException {
  def message: String = "Wait queue is full"
}

case object WaitQueueTimeoutException
    extends TimeoutException("In wait queue for too long, timing out request.")

private final class PoolManager[F[_], A <: Connection[F]](
    builder: ConnectionBuilder[F, A],
    maxTotal: Int,
    maxWaitQueueLimit: Int,
    maxConnectionsPerRequestKey: RequestKey => Int,
    responseHeaderTimeout: Duration,
    requestTimeout: Duration,
    semaphore: Semaphore[F],
    implicit private val executionContext: ExecutionContext)(implicit F: Concurrent[F])
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

  private def getConnectionFromQueue(key: RequestKey): F[Option[A]] = F.delay {
    idleQueues.get(key).flatMap { q =>
      if (q.nonEmpty) {
        val con = q.dequeue()
        if (q.isEmpty) idleQueues.remove(key)
        Some(con)
      } else None
    }
  }

  private def incrConnection(key: RequestKey): F[Unit] = F.delay {
    curTotal += 1
    allocated.update(key, allocated.getOrElse(key, 0) + 1)
  }

  private def decrConnection(key: RequestKey): F[Unit] = F.delay {
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
  private def createConnection(key: RequestKey, callback: Callback[NextConnection]): F[Unit] =
    if (numConnectionsCheckHolds(key)) {
      incrConnection(key) *> F.start {
        Async.shift(executionContext) *> builder(key).attempt.flatMap {
          case Right(conn) =>
            F.delay(callback(Right(NextConnection(conn, fresh = true))))
          case Left(error) =>
            disposeConnection(key, None) *> F.delay(callback(Left(error)))
        }
      }.void
    } else {
      addToWaitQueue(key, callback)
    }

  private def addToWaitQueue(key: RequestKey, callback: Callback[NextConnection]): F[Unit] =
    F.delay {
      if (waitQueue.length < maxWaitQueueLimit) {
        waitQueue.enqueue(Waiting(key, callback, Instant.now()))
      } else {
        logger.error(s"Max wait length reached, not scheduling.")
        callback(Left(WaitQueueFullFailure()))
      }
    }

  private def addToIdleQueue(connection: A, key: RequestKey): F[Unit] = F.delay {
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
    F.asyncF { callback =>
      semaphore.withPermit {
        if (!isClosed) {
          def go(): F[Unit] =
            getConnectionFromQueue(key).flatMap {
              case Some(conn) if !conn.isClosed =>
                F.delay(logger.debug(s"Recycling connection: $stats")) *>
                  F.delay(callback(Right(NextConnection(conn, fresh = false))))

              case Some(closedConn @ _) =>
                F.delay(logger.debug(s"Evicting closed connection: $stats")) *>
                  decrConnection(key) *>
                  go()

              case None if numConnectionsCheckHolds(key) =>
                F.delay(logger.debug(s"Active connection not found. Creating new one. $stats")) *>
                  createConnection(key, callback)

              case None if maxConnectionsPerRequestKey(key) <= 0 =>
                F.delay(callback(Left(NoConnectionAllowedException(key))))

              case None if curTotal == maxTotal =>
                val keys = idleQueues.keys
                if (keys.nonEmpty) {
                  F.delay(logger.debug(
                    s"No connections available for the desired key, $key. Evicting random and creating a new connection: $stats")) *>
                    F.delay(keys.iterator.drop(Random.nextInt(keys.size)).next()).flatMap {
                      randKey =>
                        getConnectionFromQueue(randKey).map(
                          _.fold(
                            logger.warn(s"No connection to evict from the idleQueue for $randKey"))(
                            _.shutdown())) *>
                          decrConnection(randKey)
                    } *>
                    createConnection(key, callback)
                } else {
                  F.delay(logger.debug(
                    s"No connections available for the desired key, $key. Adding to waitQueue: $stats")) *>
                    addToWaitQueue(key, callback)
                }

              case None => // we're full up. Add to waiting queue.
                F.delay(
                  logger.debug(s"No connections available.  Waiting on new connection: $stats")) *>
                  addToWaitQueue(key, callback)
            }

          F.delay(logger.debug(s"Requesting connection: $stats")) *>
            go()
        } else {
          F.delay(callback(Left(new IllegalStateException("Connection pool is closed"))))
        }
      }
    }

  private def releaseRecyclable(key: RequestKey, connection: A): F[Unit] =
    F.delay(waitQueue.dequeueFirst(_.key == key)).flatMap {
      case Some(Waiting(_, callback, at)) =>
        if (isExpired(at)) {
          F.delay(logger.debug(s"Request expired")) *>
            F.delay(callback(Left(WaitQueueTimeoutException)))
        } else {
          F.delay(logger.debug(s"Fulfilling waiting connection request: $stats")) *>
            F.delay(callback(Right(NextConnection(connection, fresh = false))))
        }

      case None if waitQueue.isEmpty =>
        F.delay(logger.debug(s"Returning idle connection to pool: $stats")) *>
          addToIdleQueue(connection, key)

      case None =>
        findFirstAllowedWaiter.flatMap {
          case Some(Waiting(k, cb, _)) =>
            // This is the first waiter not blocked on the request key limit.
            // close the undesired connection and wait for another
            F.delay(connection.shutdown()) *>
              decrConnection(key) *>
              createConnection(k, cb)

          case None =>
            // We're blocked not because of too many connections, but
            // because of too many connections per key.
            // We might be able to reuse this request.
            addToIdleQueue(connection, key)
        }
    }

  private def releaseNonRecyclable(key: RequestKey, connection: A): F[Unit] =
    decrConnection(key) *>
      F.delay {
        if (!connection.isClosed) {
          logger.debug(s"Connection returned was busy.  Shutting down: $stats")
          connection.shutdown()
        }
      } *>
      findFirstAllowedWaiter.flatMap {
        case Some(Waiting(k, callback, _)) =>
          F.delay(logger
            .debug(s"Connection returned could not be recycled, new connection needed: $stats")) *>
            createConnection(k, callback)

        case None =>
          F.delay(
            logger.debug(
              s"Connection could not be recycled, no pending requests. Shrinking pool: $stats"))
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
  def release(connection: A): F[Unit] = semaphore.withPermit {
    logger.debug(s"Recycling connection: $stats")
    val key = connection.requestKey
    if (connection.isRecyclable) {
      releaseRecyclable(key, connection)
    } else {
      releaseNonRecyclable(key, connection)
    }
  }

  private def findFirstAllowedWaiter: F[Option[Waiting]] = F.delay {
    val (expired, rest) = waitQueue.span(w => isExpired(w.at))
    expired.foreach(_.callback(Left(WaitQueueTimeoutException)))
    if (expired.nonEmpty) {
      logger.debug(s"expired requests: ${expired.length}")
      waitQueue = rest
      logger.debug(s"Dropped expired requests: $stats")
    }
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
  override def invalidate(connection: A): F[Unit] = semaphore.withPermit {
    decrConnection(connection.requestKey) *>
      F.delay(if (!connection.isClosed) connection.shutdown()) *>
      findFirstAllowedWaiter.flatMap {
        case Some(Waiting(k, callback, _)) =>
          F.delay(logger.debug(s"Invalidated connection, new connection needed: $stats")) *>
            createConnection(k, callback)

        case None =>
          F.delay(
            logger.debug(s"Invalidated connection, no pending requests. Shrinking pool: $stats"))
      }
  }

  /**
    * Synchronous Immediate Disposal of a Connection and Its Resources.
    *
    * By taking an Option of a connection this also serves as a synchronized allocated decrease.
    *
    * @param key        The request key for the connection. Not used internally.
    * @param connection An Option of a Connection to Dispose Of.
    */
  private def disposeConnection(key: RequestKey, connection: Option[A]): F[Unit] =
    semaphore.withPermit {
      F.delay(logger.debug(s"Disposing of connection: $stats")) *>
        decrConnection(key) *>
        F.delay {
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
  def shutdown: F[Unit] = semaphore.withPermit {
    F.delay {
      logger.info(s"Shutting down connection pool: $stats")
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

final case class NoConnectionAllowedException(key: RequestKey)
    extends IllegalArgumentException(s"No client connections allowed to $key")

package org.http4s
package client

import org.log4s.getLogger
import fs2.{NonEmptyChunk, Strategy, Task}
import fs2.async.immutable.Signal
import fs2.async.mutable.{Queue, Semaphore}
import fs2.async.mutable.Queue._
import fs2.async.immutable.Signal._
import fs2.util.syntax._
import fs2.util.Async
import fs2.util.Monad

private final class PoolManager[A <: Connection](
                                                  builder: ConnectionBuilder[A],
                                                  maxTotal: Int
                                                )(implicit strategy: Strategy)
  extends ConnectionManager[A] {

  private[this] val logger = getLogger

  private def stats : Task[String] = connectionBuffer.flatMap{ q =>
    q.size.get.map{ bufferSize => s"Buffer Size: ${bufferSize}" }
  }

  private trait BuildDestroyQueue[F[_], B, C] extends Queue[F, C] {
    def build(b: B): F[Unit]
    def destroy(c: C): F[Unit]
  }

  /**
    * A Circular Buffer That Does Not Block on Enqueue but,
    * will dequeue and shutdown the oldest connection in the buffer.
    */
  private def connectionBuffer(implicit async: Async[Task]): Task[BuildDestroyQueue[Task, RequestKey, A]] =
    Semaphore.apply(maxTotal.toLong).flatMap { permits =>
    unbounded[Task,A].map { q =>
      new BuildDestroyQueue[Task,RequestKey, A] {
        def upperBound: Option[Int] = Some(maxTotal)
        def enqueue1(a:A): Task[Unit] =
          permits.tryDecrement.flatMap { b => if (b) q.enqueue1(a) else q.dequeue1.flatMap(destroy) >> q.enqueue1(a) }
        def offer1(a: A): Task[Boolean] =
          enqueue1(a).as(true)
        def dequeue1: Task[A] = cancellableDequeue1.flatMap { _._1 }
        def dequeueBatch1(batchSize: Int): Task[NonEmptyChunk[A]] = cancellableDequeueBatch1(batchSize).flatMap { _._1 }
        def cancellableDequeue1: Task[(Task[A], Task[Unit])] = cancellableDequeueBatch1(1).map { case (deq,cancel) => (deq.map(_.head),cancel) }
        def cancellableDequeueBatch1(batchSize: Int): Task[(Task[NonEmptyChunk[A]],Task[Unit])] =
          q.cancellableDequeueBatch1(batchSize).map { case (deq,cancel) => (deq.flatMap(a => permits.incrementBy(a.size.toLong).as(a)), cancel) }

        def size = q.size
        def full: Signal[Task, Boolean] = q.size.map(_ >= maxTotal)
        def available: Signal[Task, Int] = q.size.map(maxTotal - _)

        override def build(key: RequestKey): Task[Unit] =
          permits.tryDecrement.flatMap { b =>
            if (b) builder(key).flatMap(connection => enqueue1(connection))
            else q.dequeue1.flatMap(destroy) >> builder(key).flatMap(connection => enqueue1(connection))
          }

        override def destroy(connection: A): Task[Unit] =
          connection.isClosed.flatMap{ closed => if (!closed) connection.shutdown() else Task.now(()) }

      }
    }}

  /**
    * This method is similar to dequeue except that it works with the key for a Request rather than an element of
    * the queue directly.
    * If the buffer is full, we dequeue an element and if it is still open, we either use it if it matches the
    * RequestKey or we add it back to the queue. If the connection is closed, we add a connection to the pool with the
    * new RequestKey. If the buffer is not full we create a connection and add it to the queue. This builds the buffer
    * pool initially.
    * @param key The Request Key We Need A Connection For
    * @return A Task of NextConnection which has a connection to use.(NextConnection is wasted here)
    */
  def borrow(key: RequestKey): Task[NextConnection] = connectionBuffer.flatMap{q =>
    q.full.get.flatMap { full =>
      if (full) {
        q.dequeue1.flatMap { conn =>
          conn.isClosed.flatMap { closed =>
            if (!closed && key == conn.requestKey) {
              Task.delay(NextConnection(conn, false))
            }
            else if (!closed) {
              q.enqueue1(conn) >>  borrow(key)
            }
            else {
              q.build(key) >> borrow(key)
            }
          }
        }
      }
      else {
        q.build(key) >> borrow(key)
      }
    }
  }

  /**
    * This checks to see if the connection is already closed. If it is, then we are finished.
    * If it is recyclable it is added back to the connection buffer,
    * and if it is not it is shutdown as we no longer can use the connection.
    * @param connection The Connection We Are Letting Go Of
    * @return A Task of Unit
    */
  def release(connection: A): Task[Unit] =  connectionBuffer.flatMap{ q =>
    connection.isClosed.flatMap{ closed =>
      connection.isRecyclable.flatMap{ recyclable =>
        if (closed){
          Task.now(())
        } else if (recyclable){
          q.enqueue1(connection)
        } else {
          connection.shutdown()
        }
      }
    }
  }

  /**
    * We invalidate connections, as leases of connections have been given out
    * @param connection The connection to invalidate
    * @return A Task of Unit
    */
  override def invalidate(connection: A): Task[Unit] =
    connection.isClosed.flatMap{ closed => if (!closed) connection.shutdown() else Task.now(()) }

  /**
    * Transforms the Queue of Connections into A Task of Unit by evaluating an invalidation of all
    * the connections. Emptying out the Pool completely when the effect is evaluated.
    * @return
    */
  def shutdown() : Task[Unit] = connectionBuffer.flatMap{ q =>
    q.dequeue.evalMap[Task, Task, Unit]{invalidate}.run
  }
}

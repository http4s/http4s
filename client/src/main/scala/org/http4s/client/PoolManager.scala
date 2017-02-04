package org.http4s
package client

import org.log4s.getLogger
import fs2.{Pipe, Strategy, Stream, Task}
import fs2.async.mutable.Queue


private final class PoolManager[A <: Connection](
                                                  builder: ConnectionBuilder[A],
                                                  maxTotal: Int
                                                )(implicit fs2: Strategy)
  extends ConnectionManager[A] {

  private[this] val logger = getLogger

  private val connectionBuffer: Task[Queue[Task,A]] = Queue.circularBuffer[Task,A](maxTotal)

  def borrow(key: RequestKey): Task[NextConnection] = connectionBuffer.flatMap{q =>
    q.dequeue1.flatMap{ conn =>
      conn.isClosed.flatMap{ closed =>
        if (!closed && key == conn.requestKey) {
          Task.delay(NextConnection(conn, false))
        }
        else if (!closed){
          q.enqueue1(conn).flatMap(_ => borrow(key))
        }
        else {
          builder(key).flatMap(q.enqueue1).flatMap(_ => borrow(key))
        }
      }
    }
  }
  
  def release(connection: A): Task[Unit] =  connectionBuffer.flatMap{q =>
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

  override def invalidate(connection: A): Task[Unit] =
    connection.isClosed.flatMap{ closed => if (!closed) connection.shutdown() else Task.now(()) }

  def shutdown() : Task[Unit] = connectionBuffer.flatMap{ q =>
    q.dequeueAvailable.evalMap[Task, Task, Unit]{invalidate}.run
  }
}

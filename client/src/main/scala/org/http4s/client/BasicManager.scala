package org.http4s
package client

import scalaz.concurrent.Task

private final class BasicManager[A <: Connection](builder: ConnectionBuilder[A]) extends ConnectionManager[A] {
  override def borrow(requestKey: RequestKey): Task[A] =
    builder(requestKey)

  override def shutdown(): Task[Unit] =
    Task.now(())

  override def invalidate(connection: A): Task[Unit] =
    Task.delay(connection.shutdown())

  override def release(connection: A): Task[Unit] =
    invalidate(connection)
}

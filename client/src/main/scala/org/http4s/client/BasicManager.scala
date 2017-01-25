package org.http4s
package client

import fs2.Task

private final class BasicManager[A <: Connection](builder: ConnectionBuilder[A]) extends ConnectionManager[A] {
  def borrow(requestKey: RequestKey): Task[NextConnection] =
    builder(requestKey).map(NextConnection(_, true))

  override def shutdown(): Task[Unit] =
    Task.now(())

  override def invalidate(connection: A): Task[Unit] =
    Task.delay(connection.shutdown())

  override def release(connection: A): Task[Unit] =
    invalidate(connection)
}

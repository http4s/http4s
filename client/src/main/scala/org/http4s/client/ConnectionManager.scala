package org.http4s
package client

import cats.effect._

import scala.concurrent.ExecutionContext

/** Type that is responsible for the client lifecycle
  *
  * The [[ConnectionManager]] is a general wrapper around a [[ConnectionBuilder]]
  * that can pool resources in order to conserve resources such as socket connections,
  * CPU time, SSL handshakes, etc. Because it can contain significant resources it
  * must have a mechanism to free resources associated with it.
  */
trait ConnectionManager[F[_], A <: Connection[F]] {

  /** Bundle of the connection and wheither its new or not */
  // Sealed, rather than final, because SI-4440.
  sealed case class NextConnection(connection: A, fresh: Boolean)

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): F[Unit]

  /** Get a connection for the provided request key. */
  def borrow(requestKey: RequestKey): F[NextConnection]

  /**
    * Release a connection.  The connection manager may choose to keep the connection for
    * subsequent calls to [[borrow]], or dispose of the connection.
    */
  def release(connection: A): F[Unit]

  /**
    * Invalidate a connection, ensuring that its resources are freed.  The connection
    * manager may not return this connection on another borrow.
    */
  def invalidate(connection: A): F[Unit]
}

object ConnectionManager {

  /** Create a [[ConnectionManager]] that creates new connections on each request
    *
    * @param builder generator of new connections
    * */
  def basic[F[_]: Sync, A <: Connection[F]](
      builder: ConnectionBuilder[F, A]): ConnectionManager[F, A] =
    new BasicManager[F, A](builder)

  /** Create a [[ConnectionManager]] that will attempt to recycle connections
    *
    * @param builder generator of new connections
    * @param maxTotal max total connections
    * @param executionContext `ExecutionContext` where async operations will execute
    */
  def pool[F[_]: Effect, A <: Connection[F]](
      builder: ConnectionBuilder[F, A],
      maxTotal: Int,
      executionContext: ExecutionContext): ConnectionManager[F, A] =
    new PoolManager[F, A](builder, maxTotal, executionContext)
}

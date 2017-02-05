package org.http4s
package client

import java.util.concurrent.ExecutorService
import fs2.Task

/** Type that is responsible for the client lifecycle
  *
  * The [[ConnectionManager]] is a general wrapper around a [[ConnectionBuilder]]
  * that can pool resources in order to conserve resources such as socket connections,
  * CPU time, SSL handshakes, etc. Because it can contain significant resources it
  * must have a mechanism to free resources associated with it.
  */
trait ConnectionManager[A <: Connection] {

  /** Bundle of the connection and wheither its new or not */
  // Sealed, rather than final, because SI-4440.
  sealed case class NextConnection(connection: A, fresh: Boolean)

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  /** Get a connection for the provided request key. */
   def borrow(requestKey: RequestKey): Task[NextConnection]

  /**
    * Release a connection.  The connection manager may choose to keep the connection for
    * subsequent calls to [[borrow]], or dispose of the connection.
    */
  def release(connection: A): Task[Unit]

  /**
    * Invalidate a connection, ensuring that its resources are freed.  The connection
    * manager may not return this connection on another borrow.
    */
  def invalidate(connection: A): Task[Unit]
}

object ConnectionManager {
  /** Create a [[ConnectionManager]] that creates new connections on each request
    *
    * @param builder generator of new connections
    * */
  def basic[A <: Connection](builder: ConnectionBuilder[A]): ConnectionManager[A] =
    new BasicManager[A](builder)

  /** Create a [[ConnectionManager]] that will attempt to recycle connections
    *
    * @param builder generator of new connections
    * @param maxTotal max total connections
    * @param es `ExecutorService` where async operations will execute
    */
  def pool[A <: Connection](builder: ConnectionBuilder[A], maxTotal: Int, es: ExecutorService): ConnectionManager[A] =
    new PoolManager[A](builder, maxTotal, es)
}

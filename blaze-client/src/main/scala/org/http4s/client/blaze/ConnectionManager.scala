package org.http4s.client.blaze

import org.http4s.Request

import scalaz.concurrent.Task

/** type that is responsible for the client lifecycle
  *
  * The [[ConnectionManager]] is a general wrapper around a [[ConnectionBuilder]]
  * that can pool resources in order to conserve resources such as socket connections,
  * CPU time, SSL handshakes, etc. Because It can contain significant resources it
  * must have a mechanism to free resources associated with it.
  */
trait ConnectionManager {

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  /** Get a connection to the provided address
    * @param request [[Request]] to connect too
    * @param freshClient if the client should force a new connection
    * @return a Future with the connected [[BlazeClientStage]] of a blaze pipeline
    */
  def getClient(request: Request, freshClient: Boolean): Task[BlazeClientStage]
  
  /** Recycle or close the connection
    * Allow for smart reuse or simple closing of a connection after the completion of a request
    * @param request [[Request]] to connect too
    * @param stage the [[BlazeClientStage]] which to deal with
    */
  def recycleClient(request: Request, stage: BlazeClientStage): Unit
}

object ConnectionManager {
  /** Create a [[ConnectionManager]] that creates new connections on each request
    *
    * @param builder generator of new connections
    * */
  def basic(builder: ConnectionBuilder): ConnectionManager =
    new BasicManager(builder)

  /** Create a [[ConnectionManager]] that will attempt to recycle connections
    *
    * @param maxPooledConnections max pool size before connections are closed
    * @param builder generator of new connections
    */
  def pool(maxPooledConnections: Int, builder: ConnectionBuilder): ConnectionManager =
    new PoolManager(maxPooledConnections, builder)
}

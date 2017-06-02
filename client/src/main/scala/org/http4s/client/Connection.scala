package org.http4s
package client

import org.log4s.getLogger

trait Connection {
  private[this] val logger = getLogger

  def runRequest[F[_]](req: Request[F]): F[Response[F]]

  /** Determine if the connection is closed and resources have been freed */
  def isClosed: Boolean

  /** Determine if the connection is in a state that it can be recycled for another request. */
  def isRecyclable: Boolean

  /** Close down the connection, freeing resources and potentially aborting a [[Response]] */
  def shutdown(): Unit

  /** The key for requests we are able to serve */
  def requestKey: RequestKey
}

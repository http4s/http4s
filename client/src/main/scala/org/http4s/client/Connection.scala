package org.http4s
package client

import scala.util.control.NonFatal
import scalaz.concurrent.Task

import org.log4s.getLogger

trait Connection {
  private[this] val logger = getLogger

  def runRequest(req: Request): Task[Response]

  /** Determine if the connection is closed and resources have been freed */
  def isClosed: Boolean

  /** Determine if the connection is in a state that it can be recycled for another request. */
  def isRecyclable: Boolean

  /** Close down the connection, freeing resources and potentially aborting a [[Response]] */
  def shutdown(): Unit

  /** The key for requests we are able to serve */
  def requestKey: RequestKey
}

package org.http4s.client.blaze

import scala.concurrent.Future

import org.http4s.Request

import scalaz.concurrent.Task

trait ConnectionBuilder {

  /** Free resources associated with this client factory */
  def shutdown(): Task[Unit]

  /** Attempt to make a new client connection */
  def makeClient(req: Request): Future[BlazeClientStage]
}

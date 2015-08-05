package org.http4s.client.blaze

import org.http4s.Uri

import scalaz.concurrent.Task

trait ConnectionBuilder {

  /** Free resources associated with this client factory */
  def shutdown(): Task[Unit]

  /** Attempt to make a new client connection */
  def makeClient(req: Uri): Task[BlazeClientStage]
}

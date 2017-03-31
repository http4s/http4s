package org.http4s
package client
package blaze

import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.TailStage

import scalaz.concurrent.Task

private trait BlazeConnection extends TailStage[ByteBuffer] with Connection {
  def runRequest(req: Request): Task[Response]
}

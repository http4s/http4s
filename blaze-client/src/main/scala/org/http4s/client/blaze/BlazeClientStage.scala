package org.http4s.client.blaze

import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.TailStage
import org.http4s.{Request, Response}

import scalaz.concurrent.Task

trait BlazeClientStage extends TailStage[ByteBuffer] {
  def runRequest(req: Request): Task[Response]

  def isClosed(): Boolean

  def shutdown(): Unit
}

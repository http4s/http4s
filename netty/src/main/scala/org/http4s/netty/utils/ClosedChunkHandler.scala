package org.http4s.netty.utils

import org.http4s.Chunk

import scalaz.-\/
import scalaz.stream.Process.End

/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */
/** Stub handler to speed up an already closed Handler */
class ClosedChunkHandler extends ChunkHandler(1, 0) {
  def onQueueFull() {}

  def onQueueReady() {}

  override def enque(chunk: Chunk): Boolean = false

  override def request(cb: CB): Unit =  cb(-\/(End))
}

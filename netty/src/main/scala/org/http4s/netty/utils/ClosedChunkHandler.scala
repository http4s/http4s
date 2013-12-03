package org.http4s.netty.utils

import org.http4s.Chunk

import scalaz.-\/
import scalaz.stream.Process.End

/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */
/** Stub handler to speed up an already closed Handler */
class ClosedChunkHandler extends ChunkHandler(1) {

  override def enque(chunk: Chunk) = 0

  override def request(cb: CB) =  {
    cb(-\/(End))
    0
  }
}

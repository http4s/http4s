package org.http4s.netty.utils

import scalaz.{\/-, -\/, \/}
import scalaz.stream.Process.End

import org.http4s.{TrailerChunk, Chunk}
import java.util.{Queue, LinkedList}

/**
 * @author Bryce Anderson
 *         Created on 11/27/13
 */
abstract class ChunkHandler(highWater: Int, lowWater: Int) {

  type CB = Throwable \/ Chunk => Unit
  private val queue: Queue[Chunk] = new LinkedList[Chunk]()
  private var cb: CB = null
  private var endThrowable: Throwable = null
  private var closed = false
  private var queuesize = 0

  override def toString() = {
    val sb = new StringBuilder
    sb.append(s"${this.getClass.getName}(")
    val it = queue.iterator()
    while(it.hasNext) sb.append(it.next().toString())
    sb.result()
  }

  def queueSize(): Int = queuesize

  def onQueueFull(): Unit

  def onQueueReady(): Unit

  def close(trailer: TrailerChunk): Unit = queue.synchronized {
    enque(trailer)
    close()
  }

  def close(): Unit = queue.synchronized {
    closed = true
    endThrowable = End
  }

  def kill(t: Throwable): Unit = queue.synchronized {
    queuesize = 0
    closed = true
    endThrowable = t
    queue.clear()
    if (cb != null) {
      cb(-\/(t))
      cb = null
    }
  }

  def request(cb: CB): Unit = queue.synchronized {
    assert(this.cb == null)
    if (closed && queue.isEmpty) cb(-\/(endThrowable))
    else {
      queuesize -= 1
      val chunk = queue.poll()
      if (chunk == null) {
        this.cb = cb
      } else {
        cb(\/-(chunk))
        if (queuesize <= lowWater) onQueueReady()
      }
    }
  }

  /** enqueues a chunk if the channel is open
    *
    * @param chunk Chunk to enqueue
    * @return whether the Handler is still accepting chunks
    */
  def enque(chunk: Chunk): Boolean = queue.synchronized {
    //println("Enqueing chunk " + this.cb)
    if (closed) return false
    queuesize += 1
    if (this.cb != null) {
      val cb = this.cb
      this.cb = null
      cb(\/-(chunk))
    } else {
      queue.add(chunk)
      if (queuesize >= highWater) onQueueFull()
    }
    true
  }
}
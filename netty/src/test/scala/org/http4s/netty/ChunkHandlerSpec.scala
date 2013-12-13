package org.http4s.netty

import org.scalatest.{Matchers, WordSpec}
import scalaz.\/
import org.http4s.{TrailerChunk, BodyChunk, Chunk}
import scalaz.stream.Process.End
import org.http4s.netty.utils.ChunkHandler

/**
 * @author Bryce Anderson
 *         Created on 11/27/13
 */
class ChunkHandlerSpec extends WordSpec with Matchers {
  var lastFromCb: Throwable \/ Chunk = null

  val c = BodyChunk("12")

  def cb(c: Throwable \/ Chunk) {
    lastFromCb = c
  }

  def lastRight = lastFromCb.toOption.get


  "ChunkHandler" should {
    val handler = new TestChunker(6)

    "start ready" in {
      handler.queueSize() should equal(0)
      handler.queueFull should equal(false)
    }

    "hold a callback if there is no chunks available" in {
      handler.request(cb)
      lastFromCb should equal(null)
      handler.queueSize() should equal(-1)
      handler.enque(c)
      lastRight should be theSameInstanceAs(c)
      handler.queueSize() should equal(0)
    }

    "enqueue a chunk and not be full" in {
      handler.enque(c)
      handler.queueFull should equal(false)
      handler.queueSize() should equal(2)
    }

    "enqueue two more chunks and be full" in {
      handler.enque(c)
      handler.enque(c)
      handler.queueFull should equal(true)
      handler.queueSize() should equal(6)
    }

    "dequeue a chunk and be ready" in {
      handler.request(cb)
      handler.queueFull should equal(false)
      handler.queueSize() should equal(0)
    }

    "dequeue another chunk and be waiting" in {
      handler.request(cb)
      handler.queueFull should equal(false)
      handler.queueSize() should equal(-1)
    }

    "close with a TrailerChunk" in {
      handler.close(TrailerChunk())
      handler.queueFull should equal(false)
      lastRight.isInstanceOf[TrailerChunk] should equal(true)
      handler.queueSize() should equal(0)
    }

    "reject offered chunks after close" in {
      handler.enque(c) should equal(-1)
      handler.queueSize() should equal(0)
    }

    "start to receive End processes" in {
      handler.request(cb)
      lastFromCb.isLeft should equal(true)
      lastFromCb.swap.toOption.get should be theSameInstanceAs(End)
      handler.queueSize() should equal(0)
    }

    "dump chunks on failure" in {
      val handler2 = new TestChunker(3)
      handler2.enque(c)
      val t = new Exception("Failed.")
      handler2.kill(t)
      handler2.queueSize() should equal(0)
      handler2.request(cb)
      lastFromCb.swap.toOption.get should be theSameInstanceAs(t)
      handler.queueSize() should equal(0)
    }

    "terminate a waiting request with the ending throwable" in {
      val handler2 = new TestChunker(3)
      handler2.enque(c)
      handler2.request(cb)
      lastRight should be theSameInstanceAs(c)
      val t = new Exception("Failed.")
      handler2.request(cb)
      handler2.kill(t)
      handler2.queueSize() should equal(0)
      lastFromCb.swap.toOption.get should be theSameInstanceAs(t)
      handler.queueSize() should equal(0)
    }
  }

  // Simple instance for testing purposes
  class TestChunker(high: Int) extends ChunkHandler(high) {
    var sentBytes = 0
    var queueFull = false

    override def onQueueFull(): Unit = {
      queueFull = true
    }

    override def onBytesSent(n: Int): Unit = {
      sentBytes = n
      queueFull = false
    }
  }

}

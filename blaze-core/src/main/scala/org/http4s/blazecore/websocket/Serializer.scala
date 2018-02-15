package org.http4s.blazecore.websocket

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import org.http4s.blaze.pipeline.TailStage

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.collection.mutable.ArrayBuffer
import org.http4s.blaze.util.Execution._

import scala.util.{Failure, Success, Try}

/** Combined [[WriteSerializer]] and [[ReadSerializer]] */
trait Serializer[I] extends WriteSerializer[I] with ReadSerializer[I]

/** Serializes write requests, storing intermediates in a queue */
trait WriteSerializer[I] extends TailStage[I] { self =>

  def maxWriteQueue: Int = 0

  ////////////////////////////////////////////////////////////////////////

  private val _serializerWriteLock = new AnyRef
  private var _serializerWriteQueue = new ArrayBuffer[I]
  private var _serializerWritePromise: Promise[Unit] = null

  ///  channel writing bits //////////////////////////////////////////////
  override def channelWrite(data: I): Future[Unit] = _serializerWriteLock.synchronized {
    if (maxWriteQueue > 0 && _serializerWriteQueue.length > maxWriteQueue) {
      Future.failed(new Exception(s"$name Stage max write queue exceeded: $maxWriteQueue"))
    }
    else {
      if (_serializerWritePromise == null) {   // there is no queue!
        _serializerWritePromise = Promise[Unit]
        val f = super.channelWrite(data)
        f.onComplete(_checkQueue)(directec)
        f
      }
      else {
        _serializerWriteQueue += data
        _serializerWritePromise.future
      }
    }
  }

  override def channelWrite(data: Seq[I]): Future[Unit] = _serializerWriteLock.synchronized {
    if (maxWriteQueue > 0 && _serializerWriteQueue.length > maxWriteQueue) {
      Future.failed(new Exception(s"$name Stage max write queue exceeded: $maxWriteQueue"))
    }
    else {
      if (_serializerWritePromise == null) {   // there is no queue!
        _serializerWritePromise = Promise[Unit]
        val f = super.channelWrite(data)
        f.onComplete(_checkQueue)(directec)
        f
      }
      else {
        _serializerWriteQueue ++= data
        _serializerWritePromise.future
      }
    }
  }

  // Needs to be in a synchronized because it is called from continuations
  private def _checkQueue(t: Try[Unit]): Unit = _serializerWriteLock.synchronized {
    t match {
      case f@ Failure(_) =>
        _serializerWriteQueue.clear()
        val p = _serializerWritePromise
        _serializerWritePromise = null
        p.tryComplete(f)
        ()

      case Success(_) =>
        if (_serializerWriteQueue.isEmpty) {
          // Nobody has written anything
          _serializerWritePromise = null
        } else {
          // stuff to write
          val f = {
            if (_serializerWriteQueue.length > 1) { // multiple messages, just give them the queue
              val a = _serializerWriteQueue
              _serializerWriteQueue = new ArrayBuffer[I](a.size + 10)
              super.channelWrite(a)
            } else {          // only a single element to write, don't send the while queue
              val h = _serializerWriteQueue.head
              _serializerWriteQueue.clear()
              super.channelWrite(h)
            }
          }

          val p = _serializerWritePromise
          _serializerWritePromise = Promise[Unit]

          f.onComplete { t =>
            _checkQueue(t)
            p.complete(t)
          }(trampoline)
        }
    }
  }
}

/** Serializes read requests */
trait ReadSerializer[I] extends TailStage[I] {
  def maxReadQueue: Int = 0

  private val _serializerReadRef = new AtomicReference[Future[I]](null)
  private val _serializerWaitingReads  = if (maxReadQueue > 0) new AtomicInteger(0) else null

  ///  channel reading bits //////////////////////////////////////////////

  override def channelRead(size: Int = -1, timeout: Duration = Duration.Inf): Future[I] = {
    if (maxReadQueue > 0 && _serializerWaitingReads.incrementAndGet() > maxReadQueue) {
      _serializerWaitingReads.decrementAndGet()
      Future.failed(new Exception(s"$name Stage max read queue exceeded: $maxReadQueue"))
    }
    else  {
      val p = Promise[I]
      val pending = _serializerReadRef.getAndSet(p.future)

      if (pending == null) _serializerDoRead(p, size, timeout)  // no queue, just do a read
      else {
        val started = if (timeout.isFinite()) System.currentTimeMillis() else 0
        pending.onComplete { _ =>
          val d = if (timeout.isFinite()) {
            val now = System.currentTimeMillis()
            timeout - Duration(now - started, TimeUnit.MILLISECONDS)
          } else timeout

          _serializerDoRead(p, size, d)
        }(trampoline)
      } // there is a queue, need to serialize behind it

      p.future
    }
  }

  private def _serializerDoRead(p: Promise[I], size: Int, timeout: Duration): Unit = {
    super.channelRead(size, timeout).onComplete { t =>
      if (maxReadQueue > 0) _serializerWaitingReads.decrementAndGet()

      _serializerReadRef.compareAndSet(p.future, null)  // don't hold our reference if the queue is idle
      p.complete(t)
    }(directec)
  }

}

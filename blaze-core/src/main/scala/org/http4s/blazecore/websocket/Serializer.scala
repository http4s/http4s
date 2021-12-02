/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.blazecore.websocket

import org.http4s.blaze.pipeline.TailStage
import org.http4s.blaze.util.Execution._

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/** Combined [[WriteSerializer]] and [[ReadSerializer]] */
private trait Serializer[I] extends WriteSerializer[I] with ReadSerializer[I]

/** Serializes write requests, storing intermediates in a queue */
private trait WriteSerializer[I] extends TailStage[I] { self =>

  // //////////////////////////////////////////////////////////////////////

  private var serializerWriteQueue = new ArrayBuffer[I]
  private var serializerWritePromise: Promise[Unit] = null

  // /  channel writing bits //////////////////////////////////////////////
  override def channelWrite(data: I): Future[Unit] =
    channelWrite(data :: Nil)

  override def channelWrite(data: collection.Seq[I]): Future[Unit] =
    synchronized {
      if (serializerWritePromise == null) { // there is no queue!
        serializerWritePromise = Promise[Unit]()
        val f = super.channelWrite(data)
        f.onComplete(checkQueue)(directec)
        f
      } else {
        serializerWriteQueue ++= data
        serializerWritePromise.future
      }
    }

  private def checkQueue(t: Try[Unit]): Unit =
    t match {
      case f @ Failure(_) =>
        val p = synchronized {
          serializerWriteQueue.clear()
          val p = serializerWritePromise
          serializerWritePromise = null
          p
        }
        p.tryComplete(f)
        ()

      case Success(_) =>
        synchronized {
          if (serializerWriteQueue.isEmpty)
            // Nobody has written anything
            serializerWritePromise = null
          else {
            // stuff to write
            val f = {
              if (serializerWriteQueue.length > 1) { // multiple messages, just give them the queue
                val a = serializerWriteQueue
                serializerWriteQueue = new ArrayBuffer[I](a.size + 10)
                super.channelWrite(a)
              } else { // only a single element to write, don't send the while queue
                val h = serializerWriteQueue.head
                serializerWriteQueue.clear()
                super.channelWrite(h)
              }
            }

            val p = serializerWritePromise
            serializerWritePromise = Promise[Unit]()

            f.onComplete { t =>
              checkQueue(t)
              p.complete(t)
            }(trampoline)
          }
        }
    }
}

/** Serializes read requests */
trait ReadSerializer[I] extends TailStage[I] {
  private val serializerReadRef = new AtomicReference[Future[I]](null)

  // /  channel reading bits //////////////////////////////////////////////

  override def channelRead(size: Int = -1, timeout: Duration = Duration.Inf): Future[I] = {
    val p = Promise[I]()
    val pending = serializerReadRef.getAndSet(p.future)

    if (pending == null) serializerDoRead(p, size, timeout) // no queue, just do a read
    else {
      val started = if (timeout.isFinite) System.currentTimeMillis() else 0
      pending.onComplete { _ =>
        val d = if (timeout.isFinite) {
          val now = System.currentTimeMillis()
          // make sure now is `now` is not before started since
          // `currentTimeMillis` can return non-monotonic values.
          if (now <= started) timeout
          else timeout - Duration(now - started, TimeUnit.MILLISECONDS)
        } else timeout

        serializerDoRead(p, size, d)
      }(trampoline)
    } // there is a queue, need to serialize behind it

    p.future
  }

  private def serializerDoRead(p: Promise[I], size: Int, timeout: Duration): Unit =
    super
      .channelRead(size, timeout)
      .onComplete { t =>
        serializerReadRef.compareAndSet(
          p.future,
          null,
        ) // don't hold our reference if the queue is idle
        p.complete(t)
      }(directec)
}

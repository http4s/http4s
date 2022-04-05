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

package org.http4s
package blazecore

import cats.effect.IO
import fs2.concurrent.Queue
import org.http4s.blaze.pipeline.Command._
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.util.TickWheelExecutor
import scodec.bits.ByteVector

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BinaryOperator
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

abstract class TestHead(val name: String) extends HeadStage[ByteBuffer] {
  private val acc = new AtomicReference[ByteVector](ByteVector.empty)
  private val p = Promise[ByteBuffer]()
  private val binaryOperator: BinaryOperator[ByteVector] = (x: ByteVector, y: ByteVector) => x ++ y

  @volatile var closed = false

  @volatile var closeCauses: Vector[Option[Throwable]] = Vector[Option[Throwable]]()

  def getBytes(): Array[Byte] = acc.get().toArray

  val result = p.future

  override def writeRequest(data: ByteBuffer): Future[Unit] =
    if (closed) Future.failed(EOF)
    else {
      acc.accumulateAndGet(ByteVector.view(data), binaryOperator)
      util.FutureUnit
    }

  override def stageShutdown(): Unit = {
    closed = true
    super.stageShutdown()
    p.trySuccess(ByteBuffer.wrap(getBytes()))
    ()
  }

  override def doClosePipeline(cause: Option[Throwable]): Unit = {
    closeCauses :+= cause
    cause.foreach(logger.error(_)(s"$name received unhandled error command"))
    sendInboundCommand(Disconnected)
  }
}

class SeqTestHead(body: Seq[ByteBuffer]) extends TestHead("SeqTestHead") {
  private val bodyIt = body.iterator

  override def readRequest(size: Int): Future[ByteBuffer] =
    if (!closed && bodyIt.hasNext) Future.successful(bodyIt.next())
    else {
      stageShutdown()
      sendInboundCommand(Disconnected)
      Future.failed(EOF)
    }
}

final class QueueTestHead(queue: Queue[IO, Option[ByteBuffer]]) extends TestHead("QueueTestHead") {
  private val closedP = Promise[Nothing]()

  override def readRequest(size: Int): Future[ByteBuffer] = {
    val p = Promise[ByteBuffer]()
    p.completeWith(
      queue.dequeue1
        .flatMap {
          case Some(bb) => IO.pure(bb)
          case None => IO.raiseError(EOF)
        }
        .unsafeToFuture()
    )
    p.completeWith(closedP.future)
    p.future
  }

  override def stageShutdown(): Unit = {
    closedP.tryFailure(EOF)
    super.stageShutdown()
  }
}

final class SlowTestHead(body: Seq[ByteBuffer], pause: Duration, scheduler: TickWheelExecutor)
    extends TestHead("Slow TestHead") { self =>

  private val bodyIt = body.iterator
  private var currentRequest: Option[Promise[ByteBuffer]] = None

  private def resolvePending(result: Try[ByteBuffer]): Unit = {
    currentRequest.foreach(_.tryComplete(result))
    currentRequest = None
  }

  private def clear(): Unit = {
    while (bodyIt.hasNext) bodyIt.next()
    resolvePending(Failure(EOF))
  }

  override def stageShutdown(): Unit = {
    clear()
    super.stageShutdown()
  }

  override def readRequest(size: Int): Future[ByteBuffer] =
    currentRequest match {
      case Some(_) =>
        Future.failed(new IllegalStateException("Cannot serve multiple concurrent read requests"))
      case None =>
        val p = Promise[ByteBuffer]()
        currentRequest = Some(p)

        scheduler.schedule(
          new Runnable {
            override def run(): Unit =
              resolvePending {
                if (!closed && bodyIt.hasNext) Success(bodyIt.next())
                else Failure(EOF)
              }
          },
          pause,
        )

        p.future
    }
}

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
package blaze
package client

import java.util.concurrent.atomic.AtomicInteger
import org.http4s.blaze.pipeline.{Command, HeadStage, LeafBuilder, TailStage}
import org.http4s.blazecore.util.FutureUnit
import scala.concurrent.duration._
import scala.concurrent.{Await, Awaitable, Future, Promise}

class ReadBufferStageSuite extends Http4sSuite {
  test("Launch read request on startup") {
    val (readProbe, _) = makePipeline

    readProbe.inboundCommand(Command.Connected)
    assertEquals(readProbe.readCount.get, 1)
  }

  test("Trigger a buffered read after a read takes the already resolved read") {
    // The ReadProbe class returns futures that are already satisifed,
    // so buffering happens during each read call
    val (readProbe, tail) = makePipeline

    readProbe.inboundCommand(Command.Connected)
    assertEquals(readProbe.readCount.get, 1)

    awaitResult(tail.channelRead())
    assertEquals(readProbe.readCount.get, 2)
  }

  test(
    "Trigger a buffered read after a read command takes a pending read, and that read resolves") {
    // The ReadProbe class returns futures that are already satisifed,
    // so buffering happens during each read call
    val slowHead = new ReadHead
    val tail = new NoopTail
    makePipeline(slowHead, tail)

    slowHead.inboundCommand(Command.Connected)
    assertEquals(slowHead.readCount.get, 1)

    val firstRead = slowHead.lastRead
    val f = tail.channelRead()
    assert(!f.isCompleted)
    assertEquals(slowHead.readCount.get, 1)

    firstRead.success(())
    assert(f.isCompleted)

    // Now we have buffered a second read
    assertEquals(slowHead.readCount.get, 2)
  }

  test("Return an IllegalStateException when trying to do two reads at once") {
    val slowHead = new ReadHead
    val tail = new NoopTail
    makePipeline(slowHead, tail)

    slowHead.inboundCommand(Command.Connected)
    tail.channelRead()
    intercept[IllegalStateException] {
      awaitResult(tail.channelRead())
    }
  }

  def awaitResult[T](f: Awaitable[T]): T = Await.result(f, 5.seconds)

  def makePipeline: (ReadProbe, NoopTail) = {
    val readProbe = new ReadProbe
    val noopTail = new NoopTail
    makePipeline(readProbe, noopTail)
    readProbe -> noopTail
  }

  def makePipeline[T](h: HeadStage[T], t: TailStage[T]): Unit = {
    LeafBuilder(t)
      .prepend(new ReadBufferStage[T])
      .base(h)
    ()
  }

  class ReadProbe extends HeadStage[Unit] {
    override def name: String = ""
    val readCount = new AtomicInteger(0)
    override def readRequest(size: Int): Future[Unit] = {
      readCount.incrementAndGet()
      FutureUnit
    }

    override def writeRequest(data: Unit): Future[Unit] = ???
    override def doClosePipeline(cause: Option[Throwable]) = {}
  }

  class ReadHead extends HeadStage[Unit] {
    var lastRead: Promise[Unit] = _
    val readCount = new AtomicInteger(0)
    override def readRequest(size: Int): Future[Unit] = {
      lastRead = Promise[Unit]()
      readCount.incrementAndGet()
      lastRead.future
    }
    override def writeRequest(data: Unit): Future[Unit] = ???
    override def name: String = "SlowHead"
    override def doClosePipeline(cause: Option[Throwable]) = {}
  }

  class NoopTail extends TailStage[Unit] {
    override def name: String = "noop"
  }
}

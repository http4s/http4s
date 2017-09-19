package org.http4s.client.blaze

import java.util.concurrent.atomic.AtomicInteger
import org.http4s.Http4sSpec
import org.http4s.blaze.pipeline.{Command, HeadStage, LeafBuilder, TailStage}
import org.http4s.blazecore.util.FutureUnit
import scala.concurrent.{Await, Awaitable, Future, Promise}
import scala.concurrent.duration._

class ReadBufferStageSpec extends Http4sSpec {
  "ReadBufferStage" should {
    "Launch read request on startup" in {
      val (readProbe, _) = makePipeline

      readProbe.inboundCommand(Command.Connected)
      readProbe.readCount.get must_== 1
    }

    "Trigger a buffered read after a read takes the already resolved read" in {
      // The ReadProbe class returns futures that are already satisifed,
      // so buffering happens during each read call
      val (readProbe, tail) = makePipeline

      readProbe.inboundCommand(Command.Connected)
      readProbe.readCount.get must_== 1

      awaitResult(tail.channelRead())
      readProbe.readCount.get must_== 2
    }

    "Trigger a buffered read after a read command takes a pending read, and that read resolves" in {
      // The ReadProbe class returns futures that are already satisifed,
      // so buffering happens during each read call
      val slowHead = new ReadHead
      val tail = new NoopTail
      makePipeline(slowHead, tail)

      slowHead.inboundCommand(Command.Connected)
      slowHead.readCount.get must_== 1

      val firstRead = slowHead.lastRead
      val f = tail.channelRead()
      f.isCompleted must_== false
      slowHead.readCount.get must_== 1

      firstRead.success(())
      f.isCompleted must_== true

      // Now we have buffered a second read
      slowHead.readCount.get must_== 2
    }

    "Return an IllegalStateException when trying to do two reads at once" in {
      val slowHead = new ReadHead
      val tail = new NoopTail
      makePipeline(slowHead, tail)

      slowHead.inboundCommand(Command.Connected)
      tail.channelRead()
      awaitResult(tail.channelRead()) must throwA[IllegalStateException]
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
      lastRead = Promise[Unit]
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

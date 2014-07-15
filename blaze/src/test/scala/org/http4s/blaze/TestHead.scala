package org.http4s.blaze

import org.http4s.blaze.pipeline.HeadStage
import java.nio.ByteBuffer
import scala.concurrent.{Promise, Future}
import org.http4s.blaze.pipeline.Command.EOF

abstract class TestHead(val name: String) extends HeadStage[ByteBuffer] {

  @volatile
  private var acc = Vector[Array[Byte]]()

  private val p = Promise[ByteBuffer]

  def getBytes(): Array[Byte] = acc.toArray.flatten

  def result = p.future

  override def writeRequest(data: ByteBuffer): Future[Unit] = {
    val cpy = new Array[Byte](data.remaining())
    data.get(cpy)
    acc :+= cpy
    Future.successful(())
  }

  override def stageShutdown(): Unit = {
    super.stageShutdown()
    p.trySuccess(ByteBuffer.wrap(getBytes()))
  }
}

class SeqTestHead(body: Seq[ByteBuffer]) extends TestHead("SeqTestHead") {
  private val bodyIt = body.iterator

  override def readRequest(size: Int): Future[ByteBuffer] = {
    if (bodyIt.hasNext) Future.successful(bodyIt.next())
    else Future.failed(EOF)
  }
}

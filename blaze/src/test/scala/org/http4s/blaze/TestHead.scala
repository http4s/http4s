package org.http4s.blaze

import org.http4s.blaze.pipeline.HeadStage
import java.nio.ByteBuffer
import scala.concurrent.{Promise, Future}
import org.http4s.blaze.pipeline.Command.{Command, EOF}
import org.http4s.Request
import java.nio.charset.StandardCharsets

/**
 * Created by Bryce Anderson on 3/28/14.
 */
abstract class TestHead(val name: String) extends HeadStage[ByteBuffer] {

  private var acc = Vector[Array[Byte]]()

  private val p = Promise[ByteBuffer]

  def getBytes(): Array[Byte] = acc.toArray.flatten

  def result = p.future

  override def writeRequest(data: ByteBuffer): Future[Any] = {
    val cpy = new Array[Byte](data.remaining())
    Array.copy(data.array(), data.position(), cpy, 0, data.remaining())
    acc :+= cpy
    Future.successful(())
  }

  override protected def stageShutdown(): Unit = {
    super.stageShutdown()
    p.success(ByteBuffer.wrap(getBytes()))
  }
}

class SeqTestHead(body: Seq[ByteBuffer]) extends TestHead("SeqTestHead") {
  private val bodyIt = body.iterator

  override def readRequest(size: Int): Future[ByteBuffer] = {
    if (bodyIt.hasNext) Future.successful(bodyIt.next())
    else Future.failed(EOF)
  }
}

package org.http4s.blazecore.websocket

import org.http4s.blaze.pipeline.MidStage

import scala.concurrent.Future

private final class SerializingStage[I](val name: String = "SerializingStage",
                                override val maxReadQueue: Int = 0,
                                override val maxWriteQueue: Int = 0)
    extends PassThrough[I] with Serializer[I]

abstract class PassThrough[I] extends MidStage[I, I] {
  def readRequest(size: Int): Future[I] = channelRead(size)

  def writeRequest(data: I): Future[Unit] = channelWrite(data)

  override def writeRequest(data: Seq[I]): Future[Unit] = channelWrite(data)
}


package org.http4s.server.blaze

import java.nio.ByteBuffer
import org.http4s.blaze.pipeline.stages.ByteToObjectStage
import org.http4s.websocket.{FrameTranscoder, WebSocketFrame}
import org.http4s.websocket.FrameTranscoder.TranscodeError

private class WebSocketDecoder
    extends FrameTranscoder(isClient = false)
    with ByteToObjectStage[WebSocketFrame] {

  // unbounded
  val maxBufferSize: Int = 0

  val name = "Websocket Decoder"

  /** Encode objects to buffers
    * @param in object to decode
    * @return sequence of ByteBuffers to pass to the head
    */
  @throws[TranscodeError]
  def messageToBuffer(in: WebSocketFrame): Seq[ByteBuffer] = frameToBuffer(in)

  /** Method that decodes ByteBuffers to objects. None reflects not enough data to decode a message
    * Any unused data in the ByteBuffer will be recycled and available for the next read
    * @param in ByteBuffer of immediately available data
    * @return optional message if enough data was available
    */
  @throws[TranscodeError]
  def bufferToMessage(in: ByteBuffer): Option[WebSocketFrame] = Option(bufferToFrame(in))
}

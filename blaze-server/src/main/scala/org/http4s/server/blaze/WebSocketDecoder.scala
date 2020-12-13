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
  def messageToBuffer(in: WebSocketFrame): collection.Seq[ByteBuffer] = frameToBuffer(in)

  /** Method that decodes ByteBuffers to objects. None reflects not enough data to decode a message
    * Any unused data in the ByteBuffer will be recycled and available for the next read
    * @param in ByteBuffer of immediately available data
    * @return optional message if enough data was available
    */
  @throws[TranscodeError]
  def bufferToMessage(in: ByteBuffer): Option[WebSocketFrame] = Option(bufferToFrame(in))
}

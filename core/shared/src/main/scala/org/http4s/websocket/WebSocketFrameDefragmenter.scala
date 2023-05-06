/*
 * Copyright 2013 http4s.org
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

package org.http4s.websocket

import fs2.Chunk
import fs2.Pipe
import scodec.bits.ByteVector

private[http4s] object WebSocketFrameDefragmenter {

  /** This function provides a pipe that defrags a sequence of fragmented WebSocket Frames,
    * according to RFC 6455.
    *
    * For example, the sequence of fragmented frames below is transformed by the pipe provided
    * by this function as follows:
    *
    * - Original webSocketFrame sequence
    *     |
    *     +-- Text("h", false)
    *     |
    *     +-- Continuation("e", false)
    *     |
    *     +-- Continuation("l", false)
    *     |
    *     +-- Continuation("l", false)
    *     |
    *     +-- Continuation("o", true)
    *
    * - Converted webSocketFrame sequence
    *     |
    *     +-- Text("hello", true)
    *
    * The above diagram represents a sequence where a single Text Frame is followed by
    * four Continuation frames and these frames are transformed into a single defragmented Text frame.
    * (note that the first argument of each frame indicates its data and the second indicates its fin bit)
    *
    * This function is only effective for valid sequences that have been defined in the RFC for WebSocket,
    * and please note that defrag processing will NOT be performed for any other invalid sequence.
    *
    * For example, the following is an illustration of the transformation for an invalid sequence:
    *
    * - Original webSocketFrame sequence
    *     |
    *     +-- Text("text1", false)
    *     |
    *     +-- Continuation("text2", false)
    *     |
    *     +-- Continuation("text3", false)
    *     |
    *     +-- Close("close")
    *
    * - Converted webSocketFrame sequence
    *     |
    *     +-- Text("text1", false)
    *     |
    *     +-- Continuation("text2", false)
    *     |
    *     +-- Continuation("text3", false)
    *     |
    *     +-- Close("close")
    *
    * The fragmented sequence that is started with `Text("text1", false)` should be closed
    * by a Continuation frame with the fin bit true, but the original webSocketFrame sequence above
    * does not fulfill that requirement. This pipe does not perform defragmentation
    * for such sequences and just emits the invalid sequence as is.
    *
    * @return A [[Pipe]] that defrags the fragmented frames
    */
  def defragFragment[F[_]]: Pipe[F, WebSocketFrame, WebSocketFrame] =
    stream => {
      def defrag(
          frames: Chunk[WebSocketFrame]
      ): (Chunk[WebSocketFrame], Chunk[WebSocketFrame]) = {
        val initialState = (Chunk.empty[WebSocketFrame], Chunk.empty[WebSocketFrame])
        frames.foldLeft(initialState) {
          case ((fragments, result), curFrame @ WebSocketFrame.Continuation(_, true)) =>
            // Current frame is the last one of a sequence of fragments.
            // Defrag all data accumulated in `fragments` into a single frame
            // and push it to `result` chunks.
            val fragmentSum = fragments ++ Chunk.singleton(curFrame)
            val defraggedData =
              fragmentSum.foldLeft(ByteVector.empty)((sum, f) => sum ++ f.data)
            val defraggedFrame = fragmentSum.head.fold(result ++ Chunk(curFrame)) { firstFrame =>
              firstFrame match {
                case WebSocketFrame.Text(_, _) =>
                  result ++ Chunk.singleton(WebSocketFrame.Text(defraggedData, true))
                case WebSocketFrame.Binary(_, _) =>
                  result ++ Chunk.singleton(WebSocketFrame.Binary(defraggedData, true))
                case _: WebSocketFrame =>
                  // Here is an illegal path, since the first frame of a fragmented frame
                  // must be Text or Binary.
                  // We just push `fragments` and `curFrame` to `result` chunks without any defragmentation.
                  result ++ fragments ++ Chunk.singleton(curFrame)
              }
            }
            (Chunk.empty, defraggedFrame)
          case ((fragments, result), curFrame) if curFrame.last && fragments.isEmpty =>
            // Current frame is a single, not fragmented frame.
            // Just pushing `curFrame` into the `result` chunks.
            (Chunk.empty, result ++ Chunk.singleton(curFrame))
          case ((fragments, result), curFrame) if !curFrame.last =>
            // Current frame is in the middle of a sequence of fragments.
            // Just pushing `curFrame` into the `fragments` chunks.
            (fragments ++ Chunk.singleton(curFrame), result)
          case ((fragments, result), curFrame) =>
            // Here is an illegal path, e.g. the fragmented frame is not terminated
            // by a continuation frame with fin bit true.
            // We just push `fragments` and `curFrame` to `result` chunks without any defragmentation.
            (Chunk.empty, result ++ fragments ++ Chunk.singleton(curFrame))
        }
      }

      stream
        .scanChunks(Chunk.empty[WebSocketFrame]) { (remaining, chunk) =>
          val (next, defragged) = defrag(remaining ++ chunk)
          (next, defragged)
        }
    }
}

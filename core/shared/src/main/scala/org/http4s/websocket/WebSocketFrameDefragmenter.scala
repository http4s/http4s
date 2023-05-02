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
  def defragFragment[F[_]]: Pipe[F, WebSocketFrame, WebSocketFrame] =
    stream => {
      // Takes a chunk of WebSocketFrames and defrags fragmented frames into one frame.
      def defrag(
          frames: Chunk[WebSocketFrame]
      ): (Chunk[WebSocketFrame], Chunk[WebSocketFrame]) = {
        val initialState = (Chunk.empty[WebSocketFrame], Chunk.empty[WebSocketFrame])
        frames.foldLeft(initialState) {
          case ((fragments, result), curFrame) if curFrame.last =>
            // Current frame is a single frame (not fragmented), or the last one of a sequence of fragments.
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
                  // Here we handle ControlFrames (such as `Ping` or `Close`) that come in singly.
                  result ++ Chunk.singleton(curFrame)
              }
            }
            (Chunk.empty, defraggedFrame)
          case ((fragments, result), curFrame) =>
            // Current frame is in the middle of a sequence of fragments.
            (fragments ++ Chunk.singleton(curFrame), result)
        }
      }

      stream
        .scanChunks(Chunk.empty[WebSocketFrame]) { (remaining, chunk) =>
          val (next, defragged) = defrag(remaining ++ chunk)
          (next, defragged)
        }
    }
}

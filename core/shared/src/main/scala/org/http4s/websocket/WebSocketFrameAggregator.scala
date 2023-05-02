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

private[http4s] object WebSocketFrameAggregator {
  def aggregateFragment[F[_]]: Pipe[F, WebSocketFrame, WebSocketFrame] =
    stream => {
      // Takes a chunk of WebSocketFrames and aggregates fragmented frames into one frame.
      def aggregate(
          frames: Chunk[WebSocketFrame]
      ): (Chunk[WebSocketFrame], Chunk[WebSocketFrame]) = {
        val initialState = (Chunk.empty[WebSocketFrame], Chunk.empty[WebSocketFrame])
        frames.foldLeft(initialState) {
          case ((fragments, result), curFrame) if curFrame.last =>
            // Current frame is a single frame (not fragmented), or the last one of a sequence of fragments.
            val fragmentSum = fragments ++ Chunk(curFrame)
            val aggregatedData =
              fragmentSum.foldLeft(ByteVector.empty)((sum, f) => sum ++ f.data)
            val aggregatedFrame = fragmentSum.head.fold(result ++ Chunk(curFrame)) { firstFrame =>
              firstFrame match {
                case WebSocketFrame.Text(_, _) =>
                  result ++ Chunk(WebSocketFrame.Text(aggregatedData, true))
                case WebSocketFrame.Binary(_, _) =>
                  result ++ Chunk(WebSocketFrame.Binary(aggregatedData, true))
                case _: WebSocketFrame =>
                  // Here we handle ControlFrames (such as `Ping` or `Close`) that come in singly.
                  result ++ Chunk(curFrame)
              }
            }
            (Chunk.empty, aggregatedFrame)
          case ((fragments, result), curFrame) =>
            // Current frame is in the middle of a sequence of fragments.
            (fragments ++ Chunk(curFrame), result)
        }
      }

      stream
        .scanChunks(Chunk.empty[WebSocketFrame]) { (remaining, chunk) =>
          val (next, aggregated) = aggregate(remaining ++ chunk)
          (next, aggregated)
        }
    }
}

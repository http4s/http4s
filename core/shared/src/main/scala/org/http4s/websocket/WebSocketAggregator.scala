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
import fs2.Pull
import fs2.Stream
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
          // Current frame is a single frame (not fragmented), or the last one of a sequence of fragments.
          case ((result, fragments), curFrame) if curFrame.last =>
            val fragmentSum = fragments ++ Chunk(curFrame)
            val aggregatedData = fragmentSum.map(_.data).foldLeft(ByteVector.empty)(_ ++ _)
            val aggregatedFrame = fragmentSum.head.fold(result ++ Chunk(curFrame)) { firstFrame =>
              firstFrame match {
                case WebSocketFrame.Text(_, _) =>
                  result ++ Chunk(WebSocketFrame.Text(aggregatedData, true))
                case WebSocketFrame.Binary(_, _) =>
                  result ++ Chunk(WebSocketFrame.Binary(aggregatedData, true))
                case _: WebSocketFrame =>
                  result ++ Chunk(curFrame)
              }
            }
            (aggregatedFrame, Chunk.empty)
          // Current frame is in the middle of a sequence of fragments.
          case ((result, fragments), curFrame) =>
            (result, fragments ++ Chunk(curFrame))
        }
      }

      def go(
          rest: Stream[F, WebSocketFrame],
          next: Chunk[WebSocketFrame],
      ): Pull[F, WebSocketFrame, Unit] =
        rest.pull.uncons.flatMap {
          case Some((chunk, stream)) =>
            val (aggregated, remaining) = aggregate(next ++ chunk)
            Pull.output(aggregated) >> go(stream, remaining)
          case None => Pull.done
        }

      go(stream, Chunk.empty).void.stream
    }
}

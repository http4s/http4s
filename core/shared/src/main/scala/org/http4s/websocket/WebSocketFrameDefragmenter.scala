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

import cats.ApplicativeThrow
import cats.syntax.all._
import fs2.Chunk
import fs2.Pipe
import fs2.Pull
import fs2.Stream
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
  def defragFragment[F[_]](
      maxMessageSize: Long
  ): Pipe[F, WebSocketFrame, WebSocketFrame] =
    stream => {
      def defrag(
          frames: Chunk[WebSocketFrame]
      ): Either[Throwable, (Chunk[WebSocketFrame], Chunk[WebSocketFrame])] = {
        case class State(
            fragments: Chunk[WebSocketFrame],
            bytes: Long,
            result: Chunk[WebSocketFrame],
        )
        val initialState = State(Chunk.empty[WebSocketFrame], 0L, Chunk.empty[WebSocketFrame])
        def checkLimit(bytes: Long): Unit =
          // Nasty throw, but always caught in the Either that follows
          if (bytes > maxMessageSize) throw new MessageTooLong(maxMessageSize)

        Either
          .catchNonFatal(frames.foldLeft(initialState) {
            case (
                  State(fragments, bytes, result),
                  curFrame @ WebSocketFrame.Continuation(_, true),
                ) =>
              // Current frame is the last one of a sequence of fragments.
              checkLimit(bytes + curFrame.data.size)
              // Defrag all data accumulated in `fragments` into a single frame
              // and push it to `result` chunks.
              val fragmentSum = fragments ++ Chunk.singleton(curFrame)
              val defraggedData =
                fragmentSum.foldLeft(ByteVector.empty)((sum, f) => sum ++ f.data)
              val defraggedFrame = fragmentSum.head.fold(result ++ Chunk(curFrame)) { firstFrame =>
                firstFrame match {
                  case WebSocketFrame.Text(_, _) =>
                    result ++ Chunk.singleton(WebSocketFrame.Text(defraggedData, last = true))
                  case WebSocketFrame.Binary(_, _) =>
                    result ++ Chunk.singleton(WebSocketFrame.Binary(defraggedData, last = true))
                  case _: WebSocketFrame =>
                    // Here is an illegal path, since the first frame of a fragmented frame
                    // must be Text or Binary.
                    // We just push `fragments` and `curFrame` to `result` chunks without any defragmentation.
                    result ++ fragments ++ Chunk.singleton(curFrame)
                }
              }
              State(Chunk.empty, 0L, defraggedFrame)
            case (State(fragments, _, result), curFrame) if curFrame.last && fragments.isEmpty =>
              // Current frame is a single, not fragmented frame.
              // Just pushing `curFrame` into the `result` chunks.
              checkLimit(curFrame.data.size)
              State(Chunk.empty, 0L, result ++ Chunk.singleton(curFrame))
            case (State(fragments, bytes, result), curFrame) if !curFrame.last =>
              // Current frame is in the middle of a sequence of fragments.
              // Just pushing `curFrame` into the `fragments` chunks.
              val bytes_ = bytes + curFrame.data.size
              checkLimit(bytes_)
              State(fragments ++ Chunk.singleton(curFrame), bytes_, result)
            case (State(fragments, bytes, result), curFrame) =>
              // Here is an illegal path, e.g. the fragmented frame is not terminated
              // by a continuation frame with fin bit true.
              // We just push `fragments` and `curFrame` to `result` chunks without any defragmentation.
              val bytes_ = bytes + curFrame.data.size
              checkLimit(bytes_)
              State(Chunk.empty, 0L, result ++ fragments ++ Chunk.singleton(curFrame))
          })
          .map(state => (state.fragments, state.result))
      }

      def go(
          s: Stream[F, WebSocketFrame],
          remaining: Chunk[WebSocketFrame],
      ): Pull[F, WebSocketFrame, Unit] =
        s.pull.uncons.flatMap {
          case Some((chunk, next)) =>
            defrag(remaining ++ chunk) match {
              case Left(e) =>
                ApplicativeThrow[Pull[F, WebSocketFrame, *]].raiseError[Unit](e)
              case Right((remaining, defragged)) =>
                Pull.output(defragged) >> go(next, remaining)
            }
          case None =>
            // The old scanChunks implementation through 0.23.35 dropped
            // incomplete trailers on the floor.  So shall we, but this
            // seems dubious.
            Pull.done
        }

      go(stream, Chunk.empty[WebSocketFrame]).stream
    }

  @deprecated("Preserved for binary compatibility", "0.23.36")
  private[WebSocketFrameDefragmenter] def defragFragment[F[_]]
      : Pipe[F, WebSocketFrame, WebSocketFrame] =
    defragFragment(DefaultMaxMessageSize.toLong)

  /** Raised when a defragmented WebSocket message would exceed the configured
    * maximum size.
    */
  final class MessageTooLong(val maxBytes: Long)
      extends Exception(s"WebSocket message exceeds the maximum of $maxBytes bytes")
}

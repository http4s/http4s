/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core

import cats.ApplicativeThrow
import cats.MonadThrow
import cats.syntax.all._
import fs2.Chunk
import fs2.Pipe
import fs2.Pull
import fs2.Stream
import org.http4s.crypto.Hash
import org.http4s.crypto.HashAlgorithm
import org.http4s.websocket._
import scodec.bits.ByteVector

import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer

private[ember] object WebSocketHelpers {

  def frameToBytes(frame: WebSocketFrame, isClient: Boolean): List[Chunk[Byte]] = {
    val transcoder = new FrameTranscoder(isClient)
    transcoder.frameToBuffer(frame).toList.map { buffer =>
      // TODO followup: improve the buffering here
      val bytes = new Array[Byte](buffer.remaining())
      buffer.get(bytes)
      Chunk.array(bytes)
    }
  }

  private[this] val magic = ByteVector.view(Rfc6455.handshakeMagicBytes)

  def serverHandshake[F[_]](value: String)(implicit F: MonadThrow[F]): F[ByteVector] = for {
    value <- ByteVector.encodeAscii(value).liftTo[F]
    digest <- Hash[F].digest(HashAlgorithm.SHA1, value ++ magic)
  } yield digest

  def readStream[F[_]](read: Read[F]): Stream[F, Byte] =
    Stream.eval(read).flatMap {
      case Some(bytes) =>
        Stream.chunk(bytes) ++ readStream(read)
      case None => Stream.empty
    }

  def decodeFrames[F[_]](
      isClient: Boolean
  )(implicit F: ApplicativeThrow[F]): Pipe[F, Byte, WebSocketFrame] =
    stream => {
      def go(rest: Stream[F, Byte], acc: Array[Byte]): Pull[F, WebSocketFrame, Unit] =
        rest.pull.uncons.flatMap {
          case Some((chunk, next)) =>
            ApplicativeThrow[Pull[F, WebSocketFrame, *]]
              .catchNonFatal { // `bufferToFrame` might throw
                val buffer = acc ++ chunk.toArray[Byte]
                // A single chunk might contain multiple frames
                // but `bufferToFrame` decodes at most one, so we
                // call it repeatedly until all frames in the buffer are decoded.
                val frames = ArrayBuffer.empty[WebSocketFrame]
                var byteBuffer = ByteBuffer.wrap(buffer)
                val transcoder = new FrameTranscoder(isClient)
                var frame = transcoder.bufferToFrame(byteBuffer)
                while (frame != null) {
                  frames += frame
                  // We need to slice b/c `bufferToFrame` does absolute reads.
                  byteBuffer = byteBuffer.slice()
                  frame = transcoder.bufferToFrame(byteBuffer)
                }
                if (frames.nonEmpty) {
                  val remaining = new Array[Byte](byteBuffer.remaining())
                  byteBuffer.get(remaining)
                  (Some(Chunk.array(frames.toArray)), remaining)
                } else {
                  (None, buffer)
                }
              }
              .flatMap {
                case (Some(frames), remaining) =>
                  Pull.output(frames) >> go(next, remaining)
                case (None, remaining) =>
                  go(next, remaining)
              }
          case None =>
            // TODO followup: sometimes the peer closes connection before stream can interrupt itself
            Pull.raiseError(EndOfStreamError())
        }

      go(stream, Array.emptyByteArray).void.stream
    }

  final case class EndOfStreamError() extends Exception("Reached End Of Stream")

}

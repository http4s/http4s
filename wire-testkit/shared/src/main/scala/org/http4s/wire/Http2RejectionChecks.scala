/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

/** Checks only observable control-frame obligations, independently of the rejection verdict.
  * RFC 9113 sections 6.3, 6.4, 6.5, 6.7, 6.8, and 6.9.
  */
private[wire] object Http2RejectionChecks {
  import Http2Wire._

  final case class State(lastStreamId: Option[Long] = None)

  def validate(
      frame: Frame,
      fromServer: Boolean,
      state: State = State(),
  ): Either[ProtocolFailure, State] = {
    def require(condition: Boolean, detail: String): Either[ProtocolFailure, Unit] =
      if (condition) Right(()) else Left(new ProtocolFailure(detail))

    val envelope = frame.frameType match {
      case Settings =>
        for {
          _ <- require(frame.streamId == 0, "SETTINGS on a nonzero stream")
          _ <- require(frame.payload.size % 6L == 0L, "invalid SETTINGS length")
          _ <- require(!frame.hasFlag(Ack) || frame.payload.isEmpty, "SETTINGS ACK has a payload")
          _ <- frame.payload.grouped(6).foldLeft[Either[ProtocolFailure, Unit]](Right(())) {
            (checked, entry) =>
              checked.flatMap { _ =>
                val key = ((entry(0) & 0xff) << 8) | (entry(1) & 0xff)
                val value = readUint32(entry.drop(2))
                key match {
                  case 2 => require(value <= (if (fromServer) 0L else 1L), "invalid ENABLE_PUSH")
                  case 4 => require(value <= 0x7fffffffL, "invalid INITIAL_WINDOW_SIZE")
                  case 5 =>
                    require(value >= 16384L && value <= 0xffffffL, "invalid MAX_FRAME_SIZE")
                  case _ => Right(())
                }
              }
          }
        } yield ()
      case Ping => require(frame.streamId == 0 && frame.payload.size == 8L, "invalid PING")
      case WindowUpdate =>
        for {
          _ <- require(frame.payload.size == 4L, "invalid WINDOW_UPDATE length")
          _ <- require((readUint32(frame.payload) & 0x7fffffffL) != 0L, "zero WINDOW_UPDATE")
        } yield ()
      // PRIORITY is deprecated. Its old dependency tree semantics are not requirements.
      case Priority => require(frame.streamId != 0 && frame.payload.size == 5L, "invalid PRIORITY")
      case RstStream =>
        require(frame.streamId != 0 && frame.payload.size == 4L, "invalid RST_STREAM")
      case GoAway => require(frame.streamId == 0 && frame.payload.size >= 8L, "invalid GOAWAY")
      // Unknown extension frames and undefined flags are deliberately unconstrained.
      case _ => Right(())
    }
    envelope.flatMap { _ =>
      if (frame.frameType != GoAway) Right(state)
      else {
        val last = readUint32(frame.payload.take(4)) & 0x7fffffffL
        for {
          _ <- require(
            last == 0L || (last & 1L) == (if (fromServer) 1L else 0L),
            "GOAWAY Last-Stream-ID has the wrong initiator",
          )
          _ <- require(state.lastStreamId.forall(last <= _), "GOAWAY Last-Stream-ID increased")
        } yield State(Some(last))
      }
    }
  }
}

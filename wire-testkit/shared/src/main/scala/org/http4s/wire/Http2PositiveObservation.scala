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

import cats.effect.Async
import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all._

/** Connection-local positive evidence. Closure only disables incidental writes: callers still
  * have to observe every required frame, field section, body octet, and acknowledgment.
  */
private[wire] final class Http2PositiveObservation[F[_]] private (
    state: Ref[F, Http2PositiveObservation.State]
)(implicit F: Sync[F]) {
  import Http2PositiveObservation._

  /** Connection-global evidence survives changes between positive and rejection policies. */
  def observeControl(frame: Http2Wire.Frame, fromServer: Boolean): F[Http2RejectionChecks.State] =
    state
      .modify { previous =>
        Http2RejectionChecks.validate(frame, fromServer, previous.checks) match {
          case Right(next) => (previous.copy(checks = next), Right(next))
          case Left(error) => (previous, Left(error))
        }
      }
      .flatMap(F.fromEither)

  def observe(frame: Http2Wire.Frame, fromServer: Boolean): F[Unit] =
    state
      .modify { previous =>
        previous.observe(frame, fromServer) match {
          case Right(next) => (next, Right(()))
          case Left(error) => (previous, Left(error))
        }
      }
      .flatMap(F.fromEither)

  /** Attempted grants may have reached the peer; skipped grants provide no possible credit. */
  def incidental(write: F[Unit]): F[Write] =
    state.get.flatMap { current =>
      if (!current.writable) F.pure(Write(attempted = false, writable = false))
      else
        Http2RejectionWrite.reply(write).flatMap { writable =>
          (if (writable) F.unit else state.update(_.copy(writable = false)))
            .as(Write(attempted = true, writable = writable))
        }
    }

  def expecting(streams: Set[Int]): F[Unit] =
    state
      .modify { current =>
        if (current.checks.lastStreamId.exists(last => streams.exists(_.toLong > last)))
          (
            current,
            Left(
              new WireObservationLimit(
                "GOAWAY excludes an outstanding request; completion obligation unexercised"
              )
            ),
          )
        else (current.copy(expected = current.expected ++ streams), Right(()))
      }
      .flatMap(F.fromEither)

  /** Retire only after the caller has observed its complete required message. */
  def completed(streams: Set[Int]): F[Unit] =
    state.update(s => s.copy(expected = s.expected -- streams))

  /** Only observations opted into this helper activate this fixture admission guard. */
  def openingStream(stream: Int): F[Unit] = state.get.flatMap { current =>
    if (current.checks.lastStreamId.isEmpty) F.unit
    else
      F.raiseError(
        new WireObservationLimit(
          s"received GOAWAY before request stream $stream could open; later-stream obligation unexercised"
        )
      )
  }
}

private[wire] object Http2PositiveObservation {
  import Http2Wire._

  final case class Write(attempted: Boolean, writable: Boolean)
  final case class State(
      writable: Boolean = true,
      checks: Http2RejectionChecks.State = Http2RejectionChecks.State(),
      expected: Set[Int] = Set.empty,
  ) {
    def observe(frame: Frame, fromServer: Boolean): Either[Throwable, State] =
      Http2RejectionChecks.validate(frame, fromServer, checks).flatMap { next =>
        if (frame.frameType == GoAway && readUint32(frame.payload.slice(4, 8)) != 0L)
          Left(new ProtocolFailure("error GOAWAY during a valid exchange"))
        else if (fromServer && next.lastStreamId.exists(last => expected.exists(_.toLong > last)))
          Left(
            new WireObservationLimit(
              "GOAWAY excludes an outstanding request; completion obligation unexercised"
            )
          )
        else Right(copy(checks = next))
      }
  }

  /** Explicit opt-in reader. The raw Http2Wire.readUntil contract is unchanged. */
  def readUntil[F[_]: Async](peer: RawPeer[F], fromServer: Boolean)(
      accept: Frame => Boolean
  )(implicit
      peerSettings: Http2PeerSettings[F],
      dataBudget: DataBudget,
  ): F[Frame] = peerSettings.positive(peer).flatMap { positive =>
    Http2FrameReader.readUntil(
      Http2Wire.readFrame(peer),
      1024,
      "too many HTTP/2 control frames without the expected frame",
    )(
      frame => positive.observe(frame, fromServer),
      frame => frame.frameType != GoAway && accept(frame),
      frame =>
        frame.frameType match {
          case Settings =>
            Async[F].delay(dataBudget.check(frame)) *>
              (if (frame.hasFlag(Ack)) Async[F].unit
               else positive.incidental(writeFrame(peer, settings(ack = true))).void)
          case Ping =>
            if (frame.hasFlag(Ack)) Async[F].unit
            else positive.incidental(writeFrame(peer, frame.copy(flags = Ack))).void
          case GoAway | WindowUpdate | Priority => Async[F].unit
          case kind if kind > Continuation => Async[F].unit
          case _ =>
            Async[F].raiseError[Unit](
              new WireDecodingFailure(
                s"unexpected HTTP/2 frame type ${frame.frameType} on stream ${frame.streamId}"
              )
            )
        },
    )
  }

  def create[F[_]: Sync]: F[Http2PositiveObservation[F]] =
    Ref.of[F, State](State()).map(new Http2PositiveObservation[F](_))
}

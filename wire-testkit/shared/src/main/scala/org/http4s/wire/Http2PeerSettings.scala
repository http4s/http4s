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
import cats.syntax.all._
import scodec.bits.ByteVector

/** Per-case, per-connection prerequisites. These limits describe fixture capabilities, not
  * additional conformance requirements on a sender or an implementation's acceptance policy.
  */
private[wire] trait Http2PeerSettings[F[_]] {
  def received(peer: RawPeer[F], frame: Http2Wire.Frame): F[Unit]
  def knownFields(peer: RawPeer[F], octetFields: Vector[(String, String)]): F[Unit]
  def sending(
      peer: RawPeer[F],
      frames: Vector[Http2Wire.Frame],
      requireFieldAcceptance: Boolean = true,
  ): F[Unit]
}

private[wire] object Http2PeerSettings {
  import Http2Wire._

  private[wire] final case class State(
      concurrent: Long = Long.MaxValue,
      fieldBytes: Long = Long.MaxValue,
      highest: Int = 0,
      streams: Map[Int, (Boolean, Boolean)] = Map.empty,
      pendingLocalEnd: Set[Int] = Set.empty,
      pendingRemoteEnd: Set[Int] = Set.empty,
  ) {
    def received(frame: Frame): State =
      if (
        frame.frameType == Settings && !frame.hasFlag(Ack) &&
        frame.streamId == 0 && frame.payload.size % 6L == 0L
      )
        frame.payload.grouped(6).foldLeft(this) { (state, entry) =>
          val key = ((entry(0) & 0xff) << 8) | (entry(1) & 0xff)
          val value = readUint32(entry.drop(2))
          key match {
            case 3 => state.copy(concurrent = value)
            case 6 => state.copy(fieldBytes = value)
            case _ => state
          }
        }
      else end(frame, local = false)

    def end(frame: Frame, local: Boolean): State = {
      val unfinishedEnds = if (local) pendingLocalEnd else pendingRemoteEnd
      def updatePending(next: Set[Int]): State =
        if (local) copy(pendingLocalEnd = next) else copy(pendingRemoteEnd = next)
      if (frame.frameType == RstStream)
        copy(
          streams = streams - frame.streamId,
          pendingLocalEnd = pendingLocalEnd - frame.streamId,
          pendingRemoteEnd = pendingRemoteEnd - frame.streamId,
        )
      else if (frame.frameType == Headers && frame.hasFlag(EndStream) && !frame.hasFlag(EndHeaders))
        updatePending(unfinishedEnds + frame.streamId)
      else {
        val continuedEnd =
          frame.frameType == Continuation && frame.hasFlag(EndHeaders) && unfinishedEnds(
            frame.streamId
          )
        val ended = ((frame.frameType == Headers || frame.frameType == Data) && frame.hasFlag(
          EndStream
        )) || continuedEnd
        val next = if (continuedEnd) updatePending(unfinishedEnds - frame.streamId) else this
        if (!ended) next
        else
          streams.get(frame.streamId).fold(next) { case (sent, received) =>
            val ends = (sent || local, received || !local)
            next.copy(streams =
              if (ends._1 && ends._2) streams - frame.streamId
              else streams.updated(frame.streamId, ends)
            )
          }
      }
    }

    def sending(frame: Frame, initiatesRequests: Boolean): State = {
      // Invalid stream zero/even/reused/decreasing IDs are intentional stimuli, not new admissions.
      val opens = initiatesRequests && frame.frameType == Headers &&
        frame.streamId > highest && (frame.streamId & 1) == 1
      val next =
        if (!opens) this
        else {
          if (streams.size.toLong >= concurrent)
            throw new WireObservationLimit(
              s"request stream ${frame.streamId} needs an available MAX_CONCURRENT_STREAMS slot ($concurrent advertised, ${streams.size} open)"
            )
          copy(highest = frame.streamId, streams = streams.updated(frame.streamId, (false, false)))
        }
      next.end(frame, local = true)
    }
  }

  /** The independent decoder returns octet strings, so UTF-8 bytes must not be encoded again. */
  private[wire] def fieldSectionSize(octetFields: Vector[(String, String)]): Long =
    octetFields.foldLeft(0L) { case (n, (name, value)) =>
      n + name.length.toLong + value.length.toLong + 32L
    }

  private final case class Connection[F[_]](
      state: Ref[F, State],
      decoder: HpackWire.Decoder[F],
      fieldBlock: Ref[F, Option[(Int, ByteVector)]],
  )

  // Existing independent codec callers do not need a case prerequisite scope.
  implicit def untracked[F[_]: Async]: Http2PeerSettings[F] = new Http2PeerSettings[F] {
    def received(peer: RawPeer[F], frame: Frame): F[Unit] = Async[F].unit
    def knownFields(peer: RawPeer[F], octetFields: Vector[(String, String)]): F[Unit] =
      Async[F].unit
    def sending(peer: RawPeer[F], frames: Vector[Frame], requireFieldAcceptance: Boolean): F[Unit] =
      Async[F].unit
  }

  def scoped[F[_]: Async](initiatesRequests: Boolean)(
      run: Http2PeerSettings[F] => F[Unit]
  ): F[Unit] =
    Ref.of[F, Map[RawPeer[F], Connection[F]]](Map.empty).flatMap { connections =>
      def connection(peer: RawPeer[F]): F[Connection[F]] = connections.get.flatMap { current =>
        current.get(peer) match {
          case Some(value) => value.pure[F]
          case None =>
            (
              Ref.of[F, State](State()),
              HpackWire.decoder[F](maxTableSize = Int.MaxValue),
              Ref.of[F, Option[(Int, ByteVector)]](None),
            ).mapN(Connection(_, _, _))
              .flatMap(value =>
                connections.modify { latest =>
                  val selected = latest.getOrElse(peer, value)
                  (latest.updated(peer, selected), selected)
                }
              )
        }
      }
      run(new Http2PeerSettings[F] {
        def received(peer: RawPeer[F], frame: Frame): F[Unit] =
          connection(peer).flatMap(_.state.update(_.received(frame)))

        def knownFields(peer: RawPeer[F], octetFields: Vector[(String, String)]): F[Unit] =
          connection(peer).flatMap(_.state.get).flatMap { state =>
            val size = fieldSectionSize(octetFields)
            if (size <= state.fieldBytes) Async[F].unit
            else
              Async[F].raiseError[Unit](
                new WireObservationLimit(
                  s"framing fault field section needs $size octets; advertised MAX_HEADER_LIST_SIZE ${state.fieldBytes} permits rejection or discard; framing scenario unexercised"
                )
              )
          }

        def sending(peer: RawPeer[F], frames: Vector[Frame], requireFieldAcceptance: Boolean)
            : F[Unit] =
          connection(peer).flatMap { current =>
            def section(block: ByteVector, complete: Boolean): F[Unit] =
              current.state.get.flatMap { state =>
                val decoded =
                  if (complete) current.decoder.decode(block).attempt
                  else
                    Async[F].pure(
                      Left(new IllegalArgumentException("incomplete field section")): Either[
                        Throwable,
                        Vector[(String, String)],
                      ]
                    )
                decoded.flatMap {
                  case Right(fields) =>
                    // Decoder strings represent wire octets, including every byte of UTF-8 text.
                    val size = fieldSectionSize(fields)
                    if (!requireFieldAcceptance || size <= state.fieldBytes) Async[F].unit
                    else
                      Async[F].raiseError[Unit](
                        new WireObservationLimit(
                          s"field section needs $size octets; advertised MAX_HEADER_LIST_SIZE ${state.fieldBytes} permits rejection or discard; acceptance scenario unexercised"
                        )
                      )
                  case Left(_) if requireFieldAcceptance && state.fieldBytes != Long.MaxValue =>
                    Async[F].raiseError[Unit](
                      new WireObservationLimit(
                        "finite MAX_HEADER_LIST_SIZE with an intentionally malformed or separately fragmented HPACK section: acceptance prerequisite cannot be established"
                      )
                    )
                  case Left(_) =>
                    Async[F].unit // Preserve the original deliberately malformed stimulus.
                }
              }
            def loop(index: Int): F[Unit] =
              if (index == frames.size) Async[F].unit
              else {
                val frame = frames(index)
                val startsSection = frame.frameType == Headers || frame.frameType == PushPromise
                val fieldSection: F[Int] =
                  if (startsSection) {
                    Either.catchNonFatal(unpad(frame)) match {
                      case Left(_) => section(ByteVector.empty, complete = false).as(1)
                      case Right(payload) =>
                        val first =
                          if (frame.frameType == PushPromise) payload.drop(4)
                          else if (frame.hasFlag(0x20)) payload.drop(5)
                          else payload
                        val rest =
                          if (frame.hasFlag(EndHeaders)) Vector.empty
                          else
                            frames
                              .drop(index + 1)
                              .takeWhile(f =>
                                f.frameType == Continuation && f.streamId == frame.streamId
                              )
                        val throughEnd = rest.indexWhere(_.hasFlag(EndHeaders)) match {
                          case -1 => rest
                          case last => rest.take(last + 1)
                        }
                        val block = throughEnd.foldLeft(first)(_ ++ _.payload)
                        val complete = frame.hasFlag(EndHeaders) || throughEnd.lastOption
                          .exists(_.hasFlag(EndHeaders))
                        (if (complete)
                           section(block, complete = true) *> current.fieldBlock.set(None)
                         else
                           current.state.get.flatMap { state =>
                             if (requireFieldAcceptance && state.fieldBytes != Long.MaxValue)
                               section(block, complete = false)
                             else current.fieldBlock.set(Some(frame.streamId -> block))
                           }).as(1 + throughEnd.size)
                    }
                  } else if (frame.frameType == Continuation)
                    current.fieldBlock.get
                      .flatMap {
                        case Some((stream, prefix)) if stream == frame.streamId =>
                          val block = prefix ++ frame.payload
                          if (frame.hasFlag(EndHeaders))
                            section(block, complete = true) *> current.fieldBlock.set(None)
                          else current.fieldBlock.set(Some(stream -> block))
                        case _ => Async[F].unit // An intentionally invalid standalone CONTINUATION.
                      }
                      .as(1)
                  else Async[F].pure(1)
                current.state.update(_.sending(frame, initiatesRequests)) *>
                  fieldSection.flatMap { consumed =>
                    current.state.update(state =>
                      frames
                        .slice(index + 1, index + consumed)
                        .foldLeft(state)((next, continuation) =>
                          next.sending(continuation, initiatesRequests)
                        )
                    ) *>
                      loop(index + consumed)
                  }
              }
            loop(0)
          }
      })
    }

  /** Compression-negative blocks need decompression even when the peer discards fields (9113 4.3).
    * Only their fault block opts out of field acceptance; positive warmups retain the default.
    */
  def writeFrames[F[_]: Async](
      peer: RawPeer[F],
      frames: Vector[Frame],
      requireFieldAcceptance: Boolean = true,
  )(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Unit] =
    peerSettings.sending(peer, frames, requireFieldAcceptance) *> frames.traverse_(frame =>
      peer.write(encode(frame))
    )

  /** A framing fault with one independently known logical field section. The witness contains
    * wire octet strings, as supplied to literalFields, not UTF-8 text. Positive warmups must be
    * written separately on the ordinary checked path. Admission and decoder tracking remain active.
    */
  def writeFramingFault[F[_]: Async](
      peer: RawPeer[F],
      frames: Vector[Frame],
      octetFields: Vector[(String, String)],
  )(implicit peerSettings: Http2PeerSettings[F]): F[Unit] =
    peerSettings.knownFields(peer, octetFields) *>
      writeFrames(peer, frames, requireFieldAcceptance = false)(Async[F], peerSettings)

  /** Inspect complete frame prefixes without rejecting intentionally invalid lengths or prefaces. */
  def write[F[_]: Async](peer: RawPeer[F], bytes: ByteVector)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Unit] = {
    val input = if (bytes.startsWith(clientPreface)) bytes.drop(clientPreface.size) else bytes
    def prefix(offset: Long, result: Vector[Frame]): Vector[Frame] =
      if (input.size - offset < 9L) result
      else {
        val size = ((input(offset) & 0xff) << 16) | ((input(offset + 1) & 0xff) << 8) | (input(
          offset + 2
        ) & 0xff)
        val end = offset + 9L + size.toLong
        if (end > input.size) result
        else
          decodeAll(input.slice(offset, end))
            .fold(_ => result, frames => prefix(end, result ++ frames))
      }
    peerSettings.sending(peer, prefix(0L, Vector.empty)) *> peer.write(bytes)
  }
}

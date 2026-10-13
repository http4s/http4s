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
import cats.syntax.all._
import scodec.bits.ByteVector

/** Explicit HTTP/2 frame construction and observation, independent of target frame codecs. */
object Http2Wire {
  val Data = 0
  val Headers = 1
  val Priority = 2
  val RstStream = 3
  val Settings = 4
  val PushPromise = 5
  val Ping = 6
  val GoAway = 7
  val WindowUpdate = 8
  val Continuation = 9

  val EndStream = 1
  val Ack = 1
  val EndHeaders = 4
  val Padded = 8

  val clientPreface: ByteVector = Http1Wire.ascii("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n")

  final case class Frame(frameType: Int, flags: Int, streamId: Int, payload: ByteVector) {
    def hasFlag(flag: Int): Boolean = (flags & flag) != 0
  }

  /** A conservative prerequisite for a finite stimulus, counting the entire DATA payload
    * (including padding) on its busiest stream. WINDOW_UPDATE grants are not used: a smaller
    * initial window reports an observation limit instead of sending incompatible traffic.
    * Rechecking every setting also covers reductions after some of this budget was sent.
    * Callers keep the total connection budget within 65535 bytes, or manage credit explicitly.
    */
  final case class DataBudget(bytes: Long) {
    require(bytes >= 0L && bytes <= 65535L, "invalid finite DATA stimulus budget")

    private[wire] def check(frame: Frame): Unit =
      if (
        frame.frameType == Settings && !frame.hasFlag(Ack) &&
        frame.streamId == 0 && frame.payload.size % 6L == 0L
      )
        frame.payload.grouped(6).foreach { entry =>
          val key = ((entry(0) & 0xff) << 8) | (entry(1) & 0xff)
          if (key == 4 && readUint32(entry.drop(2)) < bytes)
            throw new WireObservationLimit(
              s"DATA stimulus requires an initial stream window of at least $bytes bytes"
            )
        }
  }

  object DataBudget {
    implicit val empty: DataBudget = DataBudget(0L)
  }

  /** Literal-only generic stimuli select zero capacity, honoring every advertised reduction.
    * Dedicated HPACK cases construct their own blocks to retain their intentional table state.
    */
  def literalFields(values: Vector[(String, String)]): ByteVector =
    ByteVector(0x20) ++ HpackWire.encodeLiteral(values)

  /** A length override deliberately permits malformed frames in negative test stimuli. */
  def encode(frame: Frame, declaredLength: Option[Int] = None): ByteVector = {
    check(frame.frameType >= 0 && frame.frameType <= 255, "invalid frame type")
    check(frame.flags >= 0 && frame.flags <= 255, "invalid frame flags")
    check(frame.streamId >= 0, "invalid stream identifier")
    check(declaredLength.isDefined || frame.payload.size <= 0xffffffL, "frame payload too large")
    val size = declaredLength.getOrElse(frame.payload.size.toInt)
    check(size >= 0 && size <= 0xffffff, "invalid frame length")
    ByteVector(
      (size >>> 16).toByte,
      (size >>> 8).toByte,
      size.toByte,
      frame.frameType.toByte,
      frame.flags.toByte,
    ) ++ uint32(frame.streamId.toLong) ++ frame.payload
  }

  def uint32(value: Long): ByteVector = {
    check(value >= 0L && value <= 0xffffffffL, "invalid unsigned 32-bit integer")
    ByteVector((value >>> 24).toByte, (value >>> 16).toByte, (value >>> 8).toByte, value.toByte)
  }

  def readUint32(value: ByteVector): Long = {
    check(value.size == 4L, "expected a 32-bit integer")
    value.toArray.foldLeft(0L)((acc, b) => (acc << 8) | (b & 0xff).toLong)
  }

  /** Parses an already captured sequence, rejecting any truncated final frame. */
  def decodeAll(bytes: ByteVector): Either[Throwable, Vector[Frame]] = Either.catchNonFatal {
    val frames = Vector.newBuilder[Frame]
    var cursor = 0L
    while (cursor < bytes.size) {
      received(bytes.size - cursor >= 9L, "truncated HTTP/2 frame header")
      val header = bytes.slice(cursor, cursor + 9L)
      val size = ((header(0) & 0xff) << 16) | ((header(1) & 0xff) << 8) | (header(2) & 0xff)
      received(size.toLong <= bytes.size - cursor - 9L, "truncated HTTP/2 frame payload")
      frames += Frame(
        header(3) & 0xff,
        header(4) & 0xff,
        (readUint32(header.drop(5)) & 0x7fffffffL).toInt,
        bytes.slice(cursor + 9L, cursor + 9L + size.toLong),
      )
      cursor += 9L + size.toLong
    }
    frames.result()
  }

  def settings(values: Vector[(Int, Long)] = Vector.empty, ack: Boolean = false): Frame =
    Frame(
      Settings,
      if (ack) Ack else 0,
      0,
      values.foldLeft(ByteVector.empty) { case (bytes, (key, value)) =>
        check(key >= 0 && key <= 65535, "invalid settings identifier")
        bytes ++ ByteVector((key >>> 8).toByte, key.toByte) ++ uint32(value)
      },
    )

  def headers(
      streamId: Int,
      values: Vector[(String, String)],
      endStream: Boolean = true,
  ): Frame =
    Frame(
      Headers,
      EndHeaders | (if (endStream) EndStream else 0),
      streamId,
      literalFields(values),
    )

  def data(streamId: Int, bytes: ByteVector, endStream: Boolean = true): Frame =
    Frame(Data, if (endStream) EndStream else 0, streamId, bytes)

  def windowUpdate(streamId: Int, increment: Int): Frame = {
    check(increment > 0, "non-positive window increment")
    Frame(WindowUpdate, 0, streamId, uint32(increment.toLong))
  }

  def readFrame[F[_]: Async](
      peer: RawPeer[F],
      maxFrameBytes: Int = 0xffffff,
  )(implicit peerSettings: Http2PeerSettings[F]): F[Frame] =
    validateReadLimit[F](maxFrameBytes) *>
      WireDecodingFailure
        .read(peer.readExact(9))
        .flatMap(readFramePayload(peer, _, maxFrameBytes))
        .flatTap(peerSettings.received(peer, _))

  /** Accepts EOF or a reset only before the first byte of the next frame. */
  def readFrameOrClose[F[_]: Async](
      peer: RawPeer[F],
      maxFrameBytes: Int = 0xffffff,
  )(implicit peerSettings: Http2PeerSettings[F]): F[Option[Frame]] =
    (validateReadLimit[F](maxFrameBytes) *> peer.readExact(1))
      .map(_.some)
      .handleErrorWith { error =>
        RawPeer.raiseAdapterOrTimeout[F](error) *> (error match {
          case eof: IllegalArgumentException
              if eof.getMessage == "unexpected EOF: needed 1 bytes, received 0" =>
            none[ByteVector].pure[F]
          case reset if RuntimeInfo.isConnectionReset(reset) => none[ByteVector].pure[F]
          case other => Async[F].raiseError(other)
        })
      }
      .flatMap {
        case Some(first) =>
          WireDecodingFailure.read(peer.readExact(8)).flatMap { rest =>
            readFramePayload(peer, first ++ rest, maxFrameBytes)
              .flatTap(peerSettings.received(peer, _))
              .map(_.some)
          }
        case None => none[Frame].pure[F]
      }

  private def validateReadLimit[F[_]: Async](maxFrameBytes: Int): F[Unit] =
    if (maxFrameBytes >= 0) Async[F].unit
    else Async[F].raiseError(new IllegalArgumentException("negative frame read limit"))

  private def readFramePayload[F[_]: Async](
      peer: RawPeer[F],
      header: ByteVector,
      maxFrameBytes: Int,
  ): F[Frame] = {
    val size = ((header(0) & 0xff) << 16) | ((header(1) & 0xff) << 8) | (header(2) & 0xff)
    for {
      _ <-
        if (size <= maxFrameBytes) Async[F].unit
        else fail[F, Unit](s"frame length $size exceeds limit $maxFrameBytes")
      payload <- WireDecodingFailure.read(peer.readExact(size))
    } yield Frame(
      header(3) & 0xff,
      header(4) & 0xff,
      (readUint32(header.drop(5)) & 0x7fffffffL).toInt,
      payload,
    )
  }

  def writeFrame[F[_]: Async](peer: RawPeer[F], frame: Frame)(implicit
      peerSettings: Http2PeerSettings[F]
  ): F[Unit] =
    peerSettings.sending(peer, Vector(frame)) *> Async[F].delay(encode(frame)).flatMap(peer.write)

  /** Sends the client preface and acknowledges the server's initial SETTINGS. */
  def clientHandshake[F[_]: Async](
      peer: RawPeer[F]
  )(implicit peerSettings: Http2PeerSettings[F], dataBudget: DataBudget): F[Unit] =
    peer.write(clientPreface ++ encode(settings())) *> receiveInitialSettings(
      peer,
      fromServer = true,
    ) *>
      writeFrame(peer, settings(ack = true))

  /** Receives the complete client preface before sending server SETTINGS and their acknowledgment. */
  def serverHandshake[F[_]: Async](
      peer: RawPeer[F]
  )(implicit peerSettings: Http2PeerSettings[F], dataBudget: DataBudget): F[Unit] =
    WireDecodingFailure.read(peer.readExact(clientPreface.size.toInt)).flatMap { preface =>
      if (preface != clientPreface)
        Async[F].raiseError(new ProtocolFailure("invalid HTTP/2 client preface"))
      else
        receiveInitialSettings(peer, fromServer = false) *> peer.write(
          encode(settings()) ++ encode(settings(ack = true))
        )
    }

  private def receiveInitialSettings[F[_]: Async](
      peer: RawPeer[F],
      fromServer: Boolean,
  )(implicit peerSettings: Http2PeerSettings[F], dataBudget: DataBudget): F[Unit] =
    readFrame(peer).flatMap { frame =>
      if (frame.frameType != Settings || frame.hasFlag(Ack))
        Async[F].raiseError(new ProtocolFailure("initial HTTP/2 frame must be non-ACK SETTINGS"))
      else Async[F].delay(validateSettings(frame, fromServer))
    }

  /** Skips connection control and extension frames, acknowledging SETTINGS and PING.
    * Unexpected stream traffic is an error: silently consuming it could conceal a target defect.
    */
  def readUntil[F[_]: Async](
      peer: RawPeer[F],
      fromServer: Boolean,
  )(
      accept: Frame => Boolean
  )(implicit peerSettings: Http2PeerSettings[F], dataBudget: DataBudget): F[Frame] = {
    def loop(remaining: Int): F[Frame] =
      if (remaining == 0) limit("too many HTTP/2 control frames without the expected frame")
      else
        readFrame(peer).flatMap { frame =>
          if (accept(frame)) frame.pure[F]
          else {
            val handle: F[Unit] = frame.frameType match {
              case Settings =>
                Async[F].delay(validateSettings(frame, fromServer)) *>
                  (if (frame.hasFlag(Ack)) Async[F].unit
                   else writeFrame(peer, settings(ack = true)))
              case Ping =>
                if (frame.streamId != 0 || frame.payload.size != 8)
                  fail("invalid PING frame")
                else if (frame.hasFlag(Ack)) Async[F].unit
                else writeFrame(peer, frame.copy(flags = Ack))
              case WindowUpdate =>
                Async[F].delay {
                  received(frame.payload.size == 4, "invalid WINDOW_UPDATE length")
                  received(
                    (readUint32(frame.payload) & 0x7fffffffL) != 0L,
                    "zero WINDOW_UPDATE increment",
                  )
                }
              case Priority =>
                Async[F].delay {
                  received(frame.streamId != 0 && frame.payload.size == 5, "invalid PRIORITY frame")
                }
              case kind if kind > Continuation => Async[F].unit
              case _ =>
                fail(s"unexpected HTTP/2 frame type ${frame.frameType} on stream ${frame.streamId}")
            }
            handle *> loop(remaining - 1)
          }
        }
    loop(1024)
  }

  def readHeaders[F[_]: Async](
      peer: RawPeer[F],
      first: Frame,
      decoder: HpackWire.Decoder[F],
  )(implicit peerSettings: Http2PeerSettings[F]): F[Vector[(String, String)]] = {
    def continue(block: ByteVector, frame: Frame, remaining: Int): F[Vector[(String, String)]] =
      if (block.size > 1024 * 1024 || remaining == 0) limit("HTTP/2 header block exceeds limit")
      else if (frame.hasFlag(EndHeaders))
        decoder.decode(block).handleErrorWith {
          case error: IllegalArgumentException =>
            Async[F].raiseError(new WireDecodingFailure(error.getMessage))
          case error => Async[F].raiseError(error)
        }
      else
        readFrame(peer).flatMap { next =>
          if (next.frameType != Continuation || next.streamId != first.streamId)
            fail("expected an uninterrupted CONTINUATION on the same stream")
          else continue(block ++ next.payload, next, remaining - 1)
        }

    Async[F]
      .delay {
        received(first.frameType == Headers && first.streamId != 0, "expected stream HEADERS")
        val withoutPadding = unpad(first)
        if (first.hasFlag(0x20)) {
          received(withoutPadding.size >= 5, "truncated HEADERS priority fields")
          withoutPadding.drop(5)
        } else withoutPadding
      }
      .flatMap(continue(_, first, 1024))
  }

  /** Removes the padding field and padding bytes from a DATA or HEADERS frame. */
  def unpad(frame: Frame): ByteVector =
    if (!frame.hasFlag(Padded)) frame.payload
    else {
      received(frame.payload.nonEmpty, "missing HTTP/2 pad length")
      val padding = frame.payload(0) & 0xff
      received(padding.toLong < frame.payload.size, "HTTP/2 padding exceeds payload")
      frame.payload.drop(1).dropRight(padding.toLong)
    }

  private def validateSettings(frame: Frame, fromServer: Boolean)(implicit
      dataBudget: DataBudget
  ): Unit = {
    Http2RejectionChecks.validate(frame, fromServer).fold(throw _, _ => ())
    dataBudget.check(frame)
  }

  private def check(condition: Boolean, message: String): Unit =
    if (!condition) throw new IllegalArgumentException(message)

  private def received(condition: Boolean, message: String): Unit =
    if (!condition) throw new WireDecodingFailure(message)

  private def limit[F[_]: Async, A](message: String): F[A] =
    Async[F].raiseError(new WireObservationLimit(message))

  private def fail[F[_]: Async, A](message: String): F[A] =
    Async[F].raiseError(new WireDecodingFailure(message))
}

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
      check(bytes.size - cursor >= 9L, "truncated HTTP/2 frame header")
      val header = bytes.slice(cursor, cursor + 9L)
      val size = ((header(0) & 0xff) << 16) | ((header(1) & 0xff) << 8) | (header(2) & 0xff)
      check(size.toLong <= bytes.size - cursor - 9L, "truncated HTTP/2 frame payload")
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
      HpackWire.encodeLiteral(values),
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
  ): F[Frame] =
    for {
      header <- peer.readExact(9)
      size = ((header(0) & 0xff) << 16) | ((header(1) & 0xff) << 8) | (header(2) & 0xff)
      _ <-
        if (maxFrameBytes >= 0 && size <= maxFrameBytes) Async[F].unit
        else fail[F, Unit](s"frame length $size exceeds limit $maxFrameBytes")
      payload <- peer.readExact(size)
    } yield Frame(
      header(3) & 0xff,
      header(4) & 0xff,
      (readUint32(header.drop(5)) & 0x7fffffffL).toInt,
      payload,
    )

  def writeFrame[F[_]: Async](peer: RawPeer[F], frame: Frame): F[Unit] =
    Async[F].delay(encode(frame)).flatMap(peer.write)

  /** Sends the client preface and acknowledges the server's initial SETTINGS. */
  def clientHandshake[F[_]: Async](peer: RawPeer[F]): F[Unit] =
    peer.write(clientPreface ++ encode(settings())) *> receiveInitialSettings(peer) *>
      writeFrame(peer, settings(ack = true))

  /** Receives the complete client preface before sending server SETTINGS and their acknowledgment. */
  def serverHandshake[F[_]: Async](peer: RawPeer[F]): F[Unit] =
    peer.readExact(clientPreface.size.toInt).flatMap { preface =>
      if (preface != clientPreface)
        Async[F].raiseError(new ProtocolFailure("invalid HTTP/2 client preface"))
      else
        receiveInitialSettings(peer) *> peer.write(
          encode(settings()) ++ encode(settings(ack = true))
        )
    }

  private def receiveInitialSettings[F[_]: Async](peer: RawPeer[F]): F[Unit] =
    readFrame(peer).flatMap { frame =>
      if (frame.frameType != Settings || frame.hasFlag(Ack))
        Async[F].raiseError(new ProtocolFailure("initial HTTP/2 frame must be non-ACK SETTINGS"))
      else Async[F].delay(validateSettings(frame))
    }

  /** Skips connection control and extension frames, acknowledging SETTINGS and PING.
    * Unexpected stream traffic is an error: silently consuming it could conceal a target defect.
    */
  def readUntil[F[_]: Async](peer: RawPeer[F])(accept: Frame => Boolean): F[Frame] = {
    def loop(remaining: Int): F[Frame] =
      if (remaining == 0) fail("too many HTTP/2 control frames without the expected frame")
      else
        readFrame(peer).flatMap { frame =>
          if (accept(frame)) frame.pure[F]
          else {
            val handle: F[Unit] = frame.frameType match {
              case Settings =>
                Async[F].delay(validateSettings(frame)) *>
                  (if (frame.hasFlag(Ack)) Async[F].unit
                   else writeFrame(peer, settings(ack = true)))
              case Ping =>
                if (frame.streamId != 0 || frame.payload.size != 8)
                  fail("invalid PING frame")
                else if (frame.hasFlag(Ack)) Async[F].unit
                else writeFrame(peer, frame.copy(flags = Ack))
              case WindowUpdate =>
                Async[F].delay {
                  check(frame.payload.size == 4, "invalid WINDOW_UPDATE length")
                  check(
                    (readUint32(frame.payload) & 0x7fffffffL) != 0L,
                    "zero WINDOW_UPDATE increment",
                  )
                }
              case Priority =>
                Async[F].delay {
                  check(frame.streamId != 0 && frame.payload.size == 5, "invalid PRIORITY frame")
                  check(
                    (readUint32(frame.payload.take(4)) & 0x7fffffffL) != frame.streamId.toLong,
                    "self-dependent PRIORITY frame",
                  )
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
  ): F[Vector[(String, String)]] = {
    def continue(block: ByteVector, frame: Frame): F[Vector[(String, String)]] =
      if (block.size > 1024 * 1024) fail("HTTP/2 header block exceeds limit")
      else if (frame.hasFlag(EndHeaders)) decoder.decode(block)
      else
        readFrame(peer).flatMap { next =>
          if (next.frameType != Continuation || next.streamId != first.streamId)
            fail("expected an uninterrupted CONTINUATION on the same stream")
          else continue(block ++ next.payload, next)
        }

    Async[F]
      .delay {
        check(first.frameType == Headers && first.streamId != 0, "expected stream HEADERS")
        val withoutPadding = unpad(first)
        if (first.hasFlag(0x20)) {
          check(withoutPadding.size >= 5, "truncated HEADERS priority fields")
          check(
            (readUint32(withoutPadding.take(4)) & 0x7fffffffL) != first.streamId.toLong,
            "self-dependent HEADERS priority",
          )
          withoutPadding.drop(5)
        } else withoutPadding
      }
      .flatMap(continue(_, first))
  }

  /** Removes the padding field and padding bytes from a DATA or HEADERS frame. */
  def unpad(frame: Frame): ByteVector =
    if (!frame.hasFlag(Padded)) frame.payload
    else {
      check(frame.payload.nonEmpty, "missing HTTP/2 pad length")
      val padding = frame.payload(0) & 0xff
      check(padding.toLong < frame.payload.size, "HTTP/2 padding exceeds payload")
      frame.payload.drop(1).dropRight(padding.toLong)
    }

  private def validateSettings(frame: Frame): Unit = {
    def valid(condition: Boolean, message: String): Unit =
      if (!condition) throw new ProtocolFailure(message)
    valid(frame.streamId == 0, "SETTINGS on a nonzero stream")
    valid(frame.payload.size % 6 == 0, "invalid SETTINGS length")
    valid(!frame.hasFlag(Ack) || frame.payload.isEmpty, "SETTINGS ACK with a payload")
    var i = 0L
    while (i < frame.payload.size) {
      val key = ((frame.payload(i) & 0xff) << 8) | (frame.payload(i + 1) & 0xff)
      val value = readUint32(frame.payload.slice(i + 2, i + 6))
      key match {
        case 2 => valid(value <= 1L, "invalid SETTINGS_ENABLE_PUSH")
        case 4 => valid(value <= 0x7fffffffL, "invalid SETTINGS_INITIAL_WINDOW_SIZE")
        case 5 => valid(value >= 16384L && value <= 0xffffffL, "invalid SETTINGS_MAX_FRAME_SIZE")
        case _ => ()
      }
      i += 6
    }
  }

  private def check(condition: Boolean, message: String): Unit =
    if (!condition) throw new IllegalArgumentException(message)

  private def fail[F[_]: Async, A](message: String): F[A] =
    Async[F].raiseError(new IllegalArgumentException(message))
}

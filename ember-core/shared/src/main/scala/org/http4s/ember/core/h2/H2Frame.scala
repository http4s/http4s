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

package org.http4s.ember.core.h2

import cats.syntax.all._
import scodec.bits._

import scala.annotation.nowarn
import scala.annotation.switch

private[ember] sealed abstract class H2Frame {
  def toRaw: H2Frame.RawFrame
}

@nowarn("msg=implicit numeric widening")
private[ember] object H2Frame {
  /*
  All frames begin with a fixed 9-octet header followed by a variable-
  length payload.

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============================================================+
    |                   Frame Payload (0...)                      ...
    +---------------------------------------------------------------+

  Chris:
  To Unpack this each from should consist of
  Length 3 bytes
  Type 1 Byte
  Flags 1 Byte
  1 bit that must be sent 0x0, and must be ignored
  31 bit unsigned Integer
  FramePayload that consists of Length

   */

  final case class RawFrame(
      length: Int, // 3 bytes is within int range -- 16,777,216 16 mb max frame, bigger isn't necessarily better
      `type`: Byte,
      flags: Byte,
      identifier: Int, // 31 bit Integer
      payload: ByteVector,
  )

  object RawFrame {

    def fromByteVector(bv: ByteVector): Option[(RawFrame, ByteVector)] =
      if (bv.length >= 9) {
        val length = (bv(2) & 0xff) | ((bv(1) & 0xff) << 8) | ((bv(0) & 0xff) << 16)
        if (bv.length >= 9 + length) {
          val `type` = bv(3)
          val flags = bv(4)
          val s0 = bv(5) & 0xff
          val s1 = (bv(6) & 0xff) << 16
          val s2 = (bv(7) & 0xff) << 8
          val s3 = (bv(8) & 0xff) << 0
          val modS0 = (s0 & ~(1 << 7)) << 24
          val identifier = modS0 | s1 | s2 | s3
          // val identifier =  (bv(8) & 0xFF) | ((bv(7) & 0xFF) << 8) | ((bv(6) & 0xFF) << 16) | ((bv(5) & 0xFF) << 24)
          (
            RawFrame(
              length,
              `type`,
              flags,
              identifier,
              bv.drop(9).take(length),
            ),
            bv.drop(9 + length),
          ).some
        } else None
      } else None

    // Network Byte Order is Big Endian, so Highest Identifier is First
    def toByteVector(raw: RawFrame): ByteVector = {
      // 3
      val zero = ((raw.length >> 16) & 0xff).toByte
      val one = ((raw.length >> 8) & 0xff).toByte
      val two = ((raw.length >> 0) & 0xff).toByte

      // 2
      val t = raw.`type`
      val f = raw.flags

      // 4
      val iZero = ((raw.identifier >> 24) & 0xff).toByte
      val iOne = ((raw.identifier >> 16) & 0xff).toByte
      val iTwo = ((raw.identifier >> 8) & 0xff).toByte
      val iThree = ((raw.identifier) & 0xff).toByte

      ByteVector.view(
        Array(
          zero,
          one,
          two,
          t,
          f,
          iZero,
          iOne,
          iTwo,
          iThree,
        )
      ) ++ raw.payload
    }
  }

  def fromRaw(rawFrame: RawFrame): Either[H2Error, H2Frame] =
    (rawFrame.`type`: @switch) match {
      case Data.`type` => Data.fromRaw(rawFrame)
      case Headers.`type` => Headers.fromRaw(rawFrame)
      case Priority.`type` => Priority.fromRaw(rawFrame)
      case RstStream.`type` => RstStream.fromRaw(rawFrame)
      case Settings.`type` => Settings.fromRaw(rawFrame)
      case PushPromise.`type` => PushPromise.fromRaw(rawFrame)
      case Ping.`type` => Ping.fromRaw(rawFrame)
      case GoAway.`type` => GoAway.fromRaw(rawFrame)
      case WindowUpdate.`type` => WindowUpdate.fromRaw(rawFrame)
      case Continuation.`type` => Continuation.fromRaw(rawFrame)
      case _ => Unknown(rawFrame).asRight
    }

  def toByteVector(frame: H2Frame): ByteVector =
    RawFrame.toByteVector(frame.toRaw)

  final case class Unknown(raw: RawFrame) extends H2Frame {
    def toRaw: RawFrame = raw
  }

  /*
    +---------------+
    |Pad Length? (8)|
    +---------------+-----------------------------------------------+
    |                            Data (*)                         ...
    +---------------------------------------------------------------+
    |                           Padding (*)                       ...
    +---------------------------------------------------------------+
   */
  final case class Data(
      identifier: Int,
      data: ByteVector,
      pad: Option[ByteVector],
      endStream: Boolean,
  ) extends H2Frame {
    override def toString: String =
      s"Data(identifier=$identifier, data=$data, pad=$pad, endStream=$endStream)"

    def toRaw: RawFrame = Data.toRaw(this)
  }
  object Data {
    final val `type` = 0x0
    // 2 flags
    // EndStream = Bit 0 indicates this is the last frame this will send
    // Padded = Bit 3 indicates
    def fromRaw(rawFrame: RawFrame): Either[H2Error, Data] =
      if (rawFrame.`type` === `type`) {
        val endStream = (rawFrame.flags & (0x01 << 0)) != 0
        val padded = (rawFrame.flags & (0x01 << 3)) != 0
        if (padded) {
          val padLength = rawFrame.payload(0)
          val baseSize = rawFrame.payload.size.toInt - 1
          val dataSize = baseSize - padLength
          val data = rawFrame.payload.drop(1).take(dataSize)
          val pad = rawFrame.payload.drop(1).drop(dataSize)
          if (pad.length == padLength && data.size == dataSize) {
            Data(rawFrame.identifier, data, Some(pad), endStream).asRight
          } else Either.left(H2Error.ProtocolError)
        } else {
          Data(rawFrame.identifier, rawFrame.payload, None, endStream).asRight
        }
      } else Either.left(H2Error.ProtocolError)

    def toRaw(data: Data): RawFrame = {
      val payload = data.pad
        .map(p =>
          ByteVector.fromByte(p.length.toByte) ++
            data.data ++
            p
        )
        .getOrElse(data.data)
      val flags: Byte = {
        val init: Int = 0x0
        val endStreamBitSet: Int = if (data.endStream) init | (1 << 0) else init
        val paddedSet: Int = if (data.pad.isDefined) endStreamBitSet | (1 << 3) else endStreamBitSet
        paddedSet.toByte
      }
      RawFrame(
        payload.size.toInt,
        `type`,
        flags,
        data.identifier,
        payload,
      )
    }
  }

  /*
    +---------------+
    |Pad Length? (8)|
    +-+-------------+-----------------------------------------------+
    |E|                 Stream Dependency? (31)                     |
    +-+-------------+-----------------------------------------------+
    |  Weight? (8)  |
    +-+-------------+-----------------------------------------------+
    |                   Header Block Fragment (*)                 ...
    +---------------------------------------------------------------+
    |                           Padding (*)                       ...
    +---------------------------------------------------------------+
   */
  final case class Headers(
      identifier: Int,
      dependency: Option[Headers.StreamDependency],
      endStream: Boolean, // No Body Follows
      endHeaders: Boolean, // Whether or not to Expect Continuation Frame (if false, continuation must directly follow)
      headerBlock: ByteVector,
      padding: Option[ByteVector],
  ) extends H2Frame {
    override def toString: String =
      s"Headers(identifier=$identifier, dependency=$dependency, endStream=$endStream, endHeaders=$endHeaders, headerBlock=$headerBlock, padding=$padding)"
    def toRaw: RawFrame = Headers.toRaw(this)
  }
  object Headers {
    final val `type` = 0x1

    final case class StreamDependency(exclusive: Boolean, dependency: Int, weight: Byte)

    def fromRaw(rawFrame: RawFrame): Either[H2Error, Headers] =
      rawFrame.`type` match {
        case `type` =>
          val endStream = (rawFrame.flags & (0x01 << 0)) != 0
          val endHeaders = (rawFrame.flags & (0x01 << 2)) != 0
          val padded = (rawFrame.flags & (0x01 << 3)) != 0
          val priority = (rawFrame.flags & (0x01 << 5)) != 0

          (priority, padded) match {
            case (false, false) =>
              Headers(
                rawFrame.identifier,
                None,
                endStream,
                endHeaders,
                rawFrame.payload,
                None,
              ).asRight
            case (true, true) =>
              // This hurts. And is SO inefficient
              val bv = rawFrame.payload
              val padLength = bv.get(0)
              val pad = bv.takeRight(padLength)
              val rest = bv.dropRight(padLength).drop(1)
              val s0 = rest.get(0)
              val s1 = rest.get(1)
              val s2 = rest.get(2)
              val s3 = rest.get(3)
              val weight = rest.get(4)
              val mod0 = s0 & ~(1 << 7)
              val dependsOnStream = (mod0 << 24) + (s1 << 16) + (s2 << 8) + (s3 << 0)
              val exclusive = (s0 & (0x01 << 7)) != 0
              val payload = rest.drop(5)
              Headers(
                rawFrame.identifier,
                Some(StreamDependency(exclusive, dependsOnStream, weight)),
                endStream,
                endHeaders,
                payload,
                Some(pad),
              ).asRight
            case (true, false) =>
              val rest = rawFrame.payload
              val s0 = rest.get(0)
              val s1 = rest.get(1)
              val s2 = rest.get(2)
              val s3 = rest.get(3)
              val weight = rest.get(4)
              val mod0 = s0 & ~(1 << 7)
              val dependsOnStream = (mod0 << 24) + (s1 << 16) + (s2 << 8) + (s3 << 0)
              val exclusive = (s0 & (0x01 << 7)) != 0
              val payload = rest.drop(5)

              Headers(
                rawFrame.identifier,
                Some(StreamDependency(exclusive, dependsOnStream, weight)),
                endStream,
                endHeaders,
                payload,
                None,
              ).asRight

            case (false, true) =>
              val bv = rawFrame.payload
              val padLength = bv.get(0)
              val pad = bv.takeRight(padLength)
              val payload = bv.dropRight(padLength).drop(1)
              Headers(
                rawFrame.identifier,
                None,
                endStream,
                endHeaders,
                payload,
                Some(pad),
              ).asRight
          }
        case _ => Either.left(H2Error.InternalError)
      }
    def toRaw(headers: Headers): RawFrame = {
      val flags = {
        var init = 0
        if (headers.endStream) init = init | (1 << 0)
        if (headers.endHeaders) init = init | (1 << 2)
        if (headers.padding.isDefined) init = init | (1 << 3)
        if (headers.dependency.isDefined) init = init | (1 << 5)
        init
      }.toByte

      val body = (headers.padding, headers.dependency) match {
        case (None, None) => headers.headerBlock
        case (Some(pad), None) =>
          ByteVector.fromByte(pad.length.toByte) ++ headers.headerBlock ++
            pad
        case (padO, Some(dependency)) =>
          val dep0 = ((dependency.dependency >> 24) & 0xff).toByte
          val dep1 = ((dependency.dependency >> 16) & 0xff).toByte
          val dep2 = ((dependency.dependency >> 8) & 0xff).toByte
          val dep3 = ((dependency.dependency >> 0) & 0xff).toByte
          val modDep0 = (if (dependency.exclusive) dep0 | (1 << 7) else dep0 & ~(1 << 7)).toByte
          val base = ByteVector.view(
            Array(modDep0, dep1, dep2, dep3, dependency.weight)
          ) ++ headers.headerBlock
          padO match {
            case None => base
            case Some(pad) =>
              ByteVector.fromByte(pad.length.toByte) ++ base ++
                pad
          }
      }

      RawFrame(
        body.size.toInt,
        `type`,
        flags,
        headers.identifier,
        body,
      )
    }
  }

  /*
    +-+-------------------------------------------------------------+
    |E|                  Stream Dependency (31)                     |
    +-+-------------+-----------------------------------------------+
    |   Weight (8)  |
    +-+-------------+
   */
  final case class Priority(
      identifier: Int,
      exclusive: Boolean,
      streamDependency: Int,
      weight: Byte,
  ) extends H2Frame {
    def toRaw: RawFrame = Priority.toRaw(this)
  }
  object Priority {
    final val `type` = 0x2
    def fromRaw(raw: RawFrame): Either[H2Error, Priority] =
      if (raw.`type` == `type`) {
        if (raw.length === 5) {
          val s0 = raw.payload(0)
          val s1 = raw.payload(1)
          val s2 = raw.payload(2)
          val s3 = raw.payload(3)
          val mod0 = s0 & ~(1 << 7)
          val exclusive = (s0 & (0x01 << 7)) != 0
          val dependency = (mod0 << 24) + (s1 << 16) + (s2 << 8) + (s3 << 0)
          val weight = raw.payload(4)

          Priority(raw.identifier, exclusive, dependency, weight).asRight
        } else H2Error.FrameSizeError.asLeft
      } else Either.left(H2Error.InternalError)

    def toRaw(priority: Priority): RawFrame = {
      val payload = {
        val dep0 = ((priority.streamDependency >> 24) & 0xff).toByte
        val dep1 = ((priority.streamDependency >> 16) & 0xff).toByte
        val dep2 = ((priority.streamDependency >> 8) & 0xff).toByte
        val dep3 = ((priority.streamDependency >> 0) & 0xff).toByte
        val modDep0 = (if (priority.exclusive) dep0 | (1 << 7) else dep0 & ~(1 << 7)).toByte
        ByteVector.view(Array(modDep0, dep1, dep2, dep3, priority.weight))
      }
      RawFrame(payload.size.toInt, `type`, 0, priority.identifier, payload)
    }
  }

  /*
    +---------------------------------------------------------------+
    |                        Error Code (32)                        |
    +---------------------------------------------------------------+
   */
  final case class RstStream(
      identifier: Int,
      value: Integer,
  ) extends H2Frame {
    override def toString: String =
      s"RstStream(identifier=$identifier, value=${H2Error.fromInt(value).getOrElse(value)})"
    def toRaw: RawFrame = RstStream.toRaw(this)
  }
  object RstStream {
    final val `type` = 0x3

    def toRaw(rst: RstStream): RawFrame =
      RawFrame(4, `type`, 0, rst.identifier, ByteVector.fromInt(rst.value.toInt))

    def fromRaw(raw: RawFrame): Either[H2Error, RstStream] =
      if (raw.`type` == `type`) {
        if (raw.length == 4) {
          RstStream(raw.identifier, raw.payload.toInt(false, ByteOrdering.BigEndian)).asRight
        } else H2Error.FrameSizeError.asLeft
      } else Either.left(H2Error.InternalError)
  }

  /*
    Payload must be a multiple of 6 octets.
    n*
    +-------------------------------+
    |       Identifier (16)         |
    +-------------------------------+-------------------------------+
    |                        Value (32)                             |
    +---------------------------------------------------------------+
   */
  final case class Settings(
      identifier: Int,
      ack: Boolean,
      list: List[Settings.Setting],
  ) extends H2Frame {
    override def toString: String =
      if (identifier == 0 && ack && list.isEmpty) "Settings.Ack"
      else if (identifier == 0 && !ack) s"Settings(${list.map(_.toString).intercalate(", ")})"
      else s"Settings(identifier=$identifier, ack=$ack, list=$list)"
    def toRaw: RawFrame = Settings.toRaw(this)
  }
  object Settings {
    final val `type` = 0x4
    val Ack: Settings = Settings(0x0, ack = true, Nil)

    def updateSettings(
        settings: Settings,
        connectSettings: ConnectionSettings,
    ): ConnectionSettings =
      settings.list.foldLeft(connectSettings) {
        case (base, setting: SettingsHeaderTableSize) =>
          base.copy(tableSize = setting)
        case (base, setting: SettingsEnablePush) =>
          base.copy(enablePush = setting)
        case (base, setting: SettingsMaxConcurrentStreams) =>
          base.copy(maxConcurrentStreams = setting)
        case (base, setting: SettingsInitialWindowSize) =>
          base.copy(initialWindowSize = setting)
        case (base, setting: SettingsMaxFrameSize) =>
          base.copy(maxFrameSize = setting)
        case (base, setting: SettingsMaxHeaderListSize) =>
          base.copy(maxHeaderListSize = setting.some)
        case (base, _) => base
      }

    final case class ConnectionSettings(
        tableSize: SettingsHeaderTableSize,
        enablePush: SettingsEnablePush,
        maxConcurrentStreams: SettingsMaxConcurrentStreams,
        initialWindowSize: SettingsInitialWindowSize,
        maxFrameSize: SettingsMaxFrameSize,
        maxHeaderListSize: Option[SettingsMaxHeaderListSize],
    )
    object ConnectionSettings {

      def toSettings(connectionSettings: ConnectionSettings): Settings = {
        val tableSize =
          if (connectionSettings.tableSize != default.tableSize) connectionSettings.tableSize :: Nil
          else Nil
        val enablePush =
          if (connectionSettings.enablePush != default.enablePush)
            connectionSettings.enablePush :: Nil
          else Nil
        val maxConcurrentStreams =
          if (connectionSettings.maxConcurrentStreams != default.maxConcurrentStreams)
            connectionSettings.maxConcurrentStreams :: Nil
          else Nil
        val initialWindowSize =
          if (connectionSettings.initialWindowSize != default.initialWindowSize)
            connectionSettings.initialWindowSize :: Nil
          else Nil
        val maxFrameSize =
          if (connectionSettings.maxFrameSize != default.maxFrameSize)
            connectionSettings.maxFrameSize :: Nil
          else Nil
        val maxHeaderListSize =
          if (connectionSettings.maxHeaderListSize != default.maxHeaderListSize) {
            connectionSettings.maxHeaderListSize match {
              case Some(s) => s :: Nil
              case None => Nil
            }
          } else Nil
        Settings(
          0,
          ack = false,
          tableSize.widen ::: enablePush.widen ::: maxConcurrentStreams.widen ::: initialWindowSize.widen ::: maxFrameSize.widen ::: maxHeaderListSize.widen,
        )

      }

      val default: ConnectionSettings = ConnectionSettings(
        tableSize = SettingsHeaderTableSize(4096),
        enablePush = SettingsEnablePush(isEnabled = true),
        maxConcurrentStreams = SettingsMaxConcurrentStreams(1024),
        initialWindowSize = SettingsInitialWindowSize(65535),
        maxFrameSize = SettingsMaxFrameSize(16384),
        maxHeaderListSize = None,
      )
    }

    // Effect?
    def fromRaw(raw: RawFrame): Either[H2Error, Settings] =
      if (raw.`type` == `type`) {
        val ack = (raw.flags & (0x01 << 0)) != 0
        if (ack && raw.payload.nonEmpty) H2Error.FrameSizeError.asLeft
        else if (ack) Settings(raw.identifier, ack, List.empty).asRight
        else fromPayload(raw.payload, raw.identifier, ack)
      } else Either.left(H2Error.InternalError)
    def fromPayload(payload: ByteVector, identifier: Int, ack: Boolean): Either[H2Error, Settings] =
      if (payload.size % 6 != 0) H2Error.FrameSizeError.asLeft
      else {
        val settings = for {
          i <- 0 to (payload.size.toInt - 5) by 6
        } yield {
          val s = (payload(i) << 8) + (payload(i + 1) << 0)
          val v0 = (payload(i + 2) & 0xff) << 24
          val v1 = (payload(i + 3) & 0xff) << 16
          val v2 = (payload(i + 4) & 0xff) << 8
          val v3 = (payload(i + 5) & 0xff) << 0
          val value = v0 | v1 | v2 | v3
          Setting(s.toShort, value)
        }
        settings.toList.sequence.map(s => Settings(identifier, ack, s.toList))
      }

    def toRaw(settings: Settings): RawFrame = {
      val payload = settings.list.foldRight(ByteVector.empty) { case (next, bv) =>
        val s0 = ((next.identifier >> 8) & 0xff).toByte
        val s1 = ((next.identifier >> 0) & 0xff).toByte

        val v0 = ((next.value >> 24) & 0xff).toByte
        val v1 = ((next.value >> 16) & 0xff).toByte
        val v2 = ((next.value >> 8) & 0xff).toByte
        val v3 = ((next.value >> 0) & 0xff).toByte
        ByteVector.view(Array(s0, s1, v0, v1, v2, v3)) ++ bv
      }
      val flag: Byte = (if (settings.ack) 0 | (1 << 0) else 0).toByte
      RawFrame(payload.size.toInt, `type`, flag, settings.identifier, payload)
    }

    sealed abstract class Setting(val identifier: Short, val value: Integer)
    object Setting {
      def apply(identifier: Short, value: Integer): Either[H2Error, Setting] = identifier match {
        case 0x1 => SettingsHeaderTableSize(value).asRight
        case 0x2 =>
          value.toInt match {
            case 1 => SettingsEnablePush(isEnabled = true).asRight
            case 0 => SettingsEnablePush(isEnabled = false).asRight
            case _ => H2Error.ProtocolError.asLeft
          }
        case 0x3 => SettingsMaxConcurrentStreams(value).asRight
        case 0x4 => SettingsInitialWindowSize.fromInt(value)
        case 0x5 => SettingsMaxFrameSize.fromInt(value)
        case 0x6 => SettingsMaxHeaderListSize(value).asRight
        case _ => Unknown(identifier, value).asRight
      }
    }
    // The initial value is 4,096 octets
    final case class SettingsHeaderTableSize(size: Integer) extends Setting(0x1, size)
    // Default true
    final case class SettingsEnablePush(isEnabled: Boolean)
        extends Setting(0x2, if (isEnabled) 1 else 0)
    // Unbounded
    final case class SettingsMaxConcurrentStreams(maxConcurrency: Integer)
        extends Setting(0x3, maxConcurrency)
    // The initial value is 2^16-1 (65,535) octets.
    final case class SettingsInitialWindowSize(windowSize: Integer) extends Setting(0x4, windowSize)
    object SettingsInitialWindowSize {
      val MAX: SettingsInitialWindowSize = SettingsInitialWindowSize(Int.MaxValue - 1)
      val MIN: SettingsInitialWindowSize = SettingsInitialWindowSize(65536 - 1)
      def fromInt(windowSize: Int): Either[H2Error, SettingsInitialWindowSize] =
        if (
          windowSize <= MAX.windowSize && windowSize >= 0
        ) // MIN.windowSize) // This appears valid but would be awful
          SettingsInitialWindowSize(windowSize).asRight
        else {
          // println(s"Found $windowSize, $MAX $MIN")
          H2Error.FlowControlError.asLeft
        }
    }
    // The initial value is 2^14 (16,384) octets
    // 2^14 (16,384) and 2^24-1
    //  (16,777,215) octets, inclusive.
    final case class SettingsMaxFrameSize(frameSize: Int) extends Setting(0x5, frameSize)
    object SettingsMaxFrameSize {
      val MAX: SettingsMaxFrameSize = SettingsMaxFrameSize(16777215)
      val MIN: SettingsMaxFrameSize = SettingsMaxFrameSize(16384)
      def fromInt(frameSize: Int): Either[H2Error, SettingsMaxFrameSize] =
        if (frameSize <= MAX.frameSize && frameSize >= MIN.frameSize)
          SettingsMaxFrameSize(frameSize).asRight
        else H2Error.ProtocolError.asLeft
    }
    // The value is based on the
    // uncompressed size of header fields, including the length of the
    // name and value in octets plus an overhead of 32 octets for each
    // header field.
    final case class SettingsMaxHeaderListSize(listSize: Int) extends Setting(0x6, listSize)
    // DO NOT PROCESS
    // An endpoint that receives a SETTINGS frame with any unknown or
    // unsupported identifier MUST ignore that setting.
    final case class Unknown(override val identifier: Short, override val value: Integer)
        extends Setting(identifier, value)
  }

  /*
    +---------------+
    |Pad Length? (8)|
    +-+-------------+-----------------------------------------------+
    |R|                  Promised Stream ID (31)                    |
    +-+-----------------------------+-------------------------------+
    |                   Header Block Fragment (*)                 ...
    +---------------------------------------------------------------+
    |                           Padding (*)                       ...
    +---------------------------------------------------------------+
   */
  final case class PushPromise(
      identifier: Int,
      endHeaders: Boolean,
      promisedStreamId: Int,
      headerBlock: ByteVector,
      padding: Option[ByteVector],
  ) extends H2Frame {
    def toRaw: RawFrame = PushPromise.toRaw(this)
  }
  object PushPromise {
    final val `type` = 0x5
    def toRaw(push: PushPromise): RawFrame = {
      val flag = {
        var base = 0
        if (push.endHeaders) base = base | (1 << 2)
        if (push.padding.isDefined) base = base | (1 << 3)
        base
      }.toByte
      val payload = {
        val base: ByteVector = {
          val s0 = (push.promisedStreamId >> 24) & 0xff
          val s1: Byte = ((push.promisedStreamId >> 16) & 0xff).toByte
          val s2: Byte = ((push.promisedStreamId >> 8) & 0xff).toByte
          val s3: Byte = ((push.promisedStreamId >> 0) & 0xff).toByte
          val modS0: Byte = (s0 & ~(1 << 7)).toByte

          ByteVector.view(Array(modS0, s1, s2, s3)) ++ push.headerBlock
        }
        push.padding.fold(base)(padding =>
          ByteVector.fromByte(padding.length.toByte) ++ base ++ padding
        )
      }
      RawFrame(payload.size.toInt, `type`, flag, push.identifier, payload)
    }

    def fromRaw(raw: RawFrame): Either[H2Error, PushPromise] =
      if (raw.`type` == `type`) {
        val endHeaders = (raw.flags & (0x01 << 2)) != 0
        val padded = (raw.flags & (0x01 << 3)) != 0

        if (padded) {

          val padLength = raw.payload(0)
          val s0 = raw.payload(1) & 0xff
          val s1 = (raw.payload(2) & 0xff) << 16
          val s2 = (raw.payload(3) & 0xff) << 8
          val s3 = (raw.payload(4) & 0xff) << 0
          val modS0 = (s0 & ~(1 << 7)) << 24
          val s = modS0 | s1 | s2 | s3

          PushPromise(
            raw.identifier,
            endHeaders,
            s,
            raw.payload.drop(5).dropRight(padLength),
            raw.payload.takeRight(padLength).some,
          ).asRight
        } else {
          val s0 = raw.payload(0) & 0xff
          val s1 = (raw.payload(1) & 0xff) << 16
          val s2 = (raw.payload(2) & 0xff) << 8
          val s3 = (raw.payload(3) & 0xff) << 0
          val modS0 = (s0 & ~(1 << 7)) << 24
          val s = modS0 | s1 | s2 | s3

          PushPromise(raw.identifier, endHeaders, s, raw.payload.drop(4), None).asRight

        }

      } else H2Error.InternalError.asLeft
  }

  /*
    +---------------------------------------------------------------+
    |                                                               |
    |                      Opaque Data (64)                         |
    |                                                               |
    +---------------------------------------------------------------+
   */
  final case class Ping(identifier: Int, ack: Boolean, data: ByteVector) extends H2Frame {
    override def toString: String =
      if (identifier == 0 && ack) "Ping.Ack"
      else if (identifier == 0 && !ack) "Ping"
      else s"Ping(identifier=$identifier, ack=$ack, data=$data)"
    def toRaw: RawFrame = Ping.toRaw(this)
  } // Always exactly 8 bytes
  object Ping {
    final val `type` = 0x6
    private[this] val empty: ByteVector = ByteVector.view(Array[Byte](0, 0, 0, 0, 0, 0, 0, 0))

    val default: Ping = Ping(0, ack = false, empty)
    val ack: Ping = Ping(0, ack = true, empty)

    def toRaw(ping: Ping): RawFrame = {
      val flag: Byte = (if (ping.ack) 0 | (1 << 0) else 0).toByte
      val payload = ping.data

      RawFrame(8, `type`, flag, ping.identifier, payload)
    }

    def fromRaw(raw: RawFrame): Either[H2Error, Ping] =
      if (raw.`type` == `type`) {
        val ack = (raw.flags & (0x01 << 0)) != 0
        if (raw.length == 8) Either.right(Ping(raw.identifier, ack, raw.payload))
        else H2Error.FrameSizeError.asLeft
      } else H2Error.InternalError.asLeft

  }

  /*
    +-+-------------------------------------------------------------+
    |R|                  Last-Stream-ID (31)                        |
    +-+-------------------------------------------------------------+
    |                      Error Code (32)                          |
    +---------------------------------------------------------------+
    |                  Additional Debug Data (*)                    |
    +---------------------------------------------------------------+
   */
  final case class GoAway(
      identifier: Int,
      lastStreamId: Int,
      errorCode: Integer,
      additionalDebugData: Option[ByteVector],
  ) extends H2Frame {
    override def toString: String =
      s"GoAway(identifier=$identifier, lastStreamId=$lastStreamId, errorCode=${H2Error.fromInt(errorCode).getOrElse(errorCode)}, additionalDebugData=$additionalDebugData)"
    def toRaw: RawFrame = GoAway.toRaw(this)
  }
  object GoAway {
    final val `type` = 0x7

    def toRaw(goAway: GoAway): RawFrame = {
      val payload = {
        val s0 = (goAway.lastStreamId >> 24) & 0xff
        val s1: Byte = ((goAway.lastStreamId >> 16) & 0xff).toByte
        val s2: Byte = ((goAway.lastStreamId >> 8) & 0xff).toByte
        val s3: Byte = ((goAway.lastStreamId >> 0) & 0xff).toByte
        val modS0: Byte = (s0 & ~(1 << 7)).toByte

        val e0: Byte = ((goAway.errorCode >> 24) & 0xff).toByte
        val e1: Byte = ((goAway.errorCode >> 16) & 0xff).toByte
        val e2: Byte = ((goAway.errorCode >> 8) & 0xff).toByte
        val e3: Byte = ((goAway.errorCode >> 0) & 0xff).toByte

        ByteVector.view(Array(modS0, s1, s2, s3, e0, e1, e2, e3)) ++ goAway.additionalDebugData
          .getOrElse(
            ByteVector.empty
          )
      }

      RawFrame(payload.length.toInt, `type`, 0, goAway.identifier, payload)
    }

    def fromRaw(raw: RawFrame): Either[H2Error, GoAway] =
      if (raw.`type` == `type`) {
        val s0 = raw.payload(0) & 0xff
        val s1 = (raw.payload(1) & 0xff) << 16
        val s2 = (raw.payload(2) & 0xff) << 8
        val s3 = (raw.payload(3) & 0xff) << 0
        val modS0 = (s0 & ~(1 << 7)) << 24
        val s = modS0 | s1 | s2 | s3

        val e0 = (raw.payload(4) & 0xff) << 24
        val e1 = (raw.payload(5) & 0xff) << 16
        val e2 = (raw.payload(6) & 0xff) << 8
        val e3 = (raw.payload(7) & 0xff) << 0
        val e = e0 | e1 | e2 | e3

        val rest = {
          val end = raw.payload.drop(8)
          if (end.isEmpty) None else Some(end)
        }
        GoAway(raw.identifier, s, e, rest).asRight
      } else H2Error.InternalError.asLeft

  }

  /*
    +-+-------------------------------------------------------------+
    |R|              Window Size Increment (31)                     |
    +-+-------------------------------------------------------------+
   */
  final case class WindowUpdate(identifier: Int, windowSizeIncrement: Int) extends H2Frame {
    def toRaw: RawFrame = WindowUpdate.toRaw(this)
  }
  object WindowUpdate {
    final val `type` = 0x8

    def toRaw(windowUpdate: WindowUpdate): RawFrame = {
      val payload = {
        val s0 = (windowUpdate.windowSizeIncrement >> 24) & 0xff
        val s1: Byte = ((windowUpdate.windowSizeIncrement >> 16) & 0xff).toByte
        val s2: Byte = ((windowUpdate.windowSizeIncrement >> 8) & 0xff).toByte
        val s3: Byte = ((windowUpdate.windowSizeIncrement >> 0) & 0xff).toByte
        val modS0: Byte = (s0 & ~(1 << 7)).toByte
        ByteVector.view(Array(modS0, s1, s2, s3))
      }

      RawFrame(payload.length.toInt, `type`, 0, windowUpdate.identifier, payload)
    }

    def fromRaw(raw: RawFrame): Either[H2Error, WindowUpdate] =
      if (raw.`type` == `type`) {
        if (raw.length == 4) {
          val s0 = raw.payload(0) & 0xff
          val s1 = (raw.payload(1) & 0xff) << 16
          val s2 = (raw.payload(2) & 0xff) << 8
          val s3 = (raw.payload(3) & 0xff) << 0
          val modS0 = (s0 & ~(1 << 7)) << 24
          val s = modS0 | s1 | s2 | s3
          WindowUpdate(raw.identifier, s).asRight
        } else H2Error.FrameSizeError.asLeft
      } else H2Error.ProtocolError.asLeft
  }

  /*
    +---------------------------------------------------------------+
    |                   Header Block Fragment (*)                 ...
    +---------------------------------------------------------------+
   */
  final case class Continuation(
      identifier: Int,
      endHeaders: Boolean,
      headerBlockFragment: ByteVector,
  ) extends H2Frame {
    override def toString: String =
      s"Continuation(identifier=$identifier, endHeader=$endHeaders, headerBlockFragment=$headerBlockFragment)"
    def toRaw: RawFrame = Continuation.toRaw(this)
  }
  object Continuation {
    final val `type` = 0x9
    def toRaw(cont: Continuation): RawFrame = {
      val flag: Byte = (if (cont.endHeaders) 0 | (1 << 2) else 0).toByte
      RawFrame(
        cont.headerBlockFragment.size.toInt,
        `type`,
        flag,
        cont.identifier,
        cont.headerBlockFragment,
      )
    }
    def fromRaw(raw: RawFrame): Either[H2Error, Continuation] =
      if (raw.`type` == `type`) {
        val endHeaders = (raw.flags & (0x01 << 2)) != 0
        Continuation(raw.identifier, endHeaders, raw.payload).asRight
      } else H2Error.InternalError.asLeft
  }

}

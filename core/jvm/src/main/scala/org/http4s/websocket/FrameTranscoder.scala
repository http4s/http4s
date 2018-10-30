package org.http4s.websocket

import java.nio.ByteBuffer
import scodec.bits.ByteVector

private[http4s] object FrameTranscoder {
  final class TranscodeError(val message: String) extends Exception(message)

  private def decodeBinary(in: ByteBuffer, mask: Array[Byte]) = {
    val data = new Array[Byte](in.remaining)
    in.get(data)
    if (mask != null) { // We can use the charset decode
      for (i <- 0 until data.length) {
        data(i) = (data(i) ^ mask(i & 0x3)).toByte // i mod 4 is the same as i & 0x3 but slower
      }
    }
    data
  }

  private def lengthOffset(in: ByteBuffer) = {
    val len = in.get(1) & LENGTH
    if (len < 126) 2
    else if (len == 126) 4
    else if (len == 127) 10
    else throw new FrameTranscoder.TranscodeError("Length error!")
  }

  private def getMask(in: ByteBuffer): Array[Byte] = {
    val m = new Array[Byte](4)
    in.mark
    in.position(lengthOffset(in))
    in.get(m)
    in.reset
    m
  }

  private def bodyLength(in: ByteBuffer) = {
    val len = in.get(1) & LENGTH
    if (len < 126) len
    else if (len == 126) (in.get(2) << 8 & 0xff00) | (in.get(3) & 0xff)
    else if (len == 127) {
      val l = in.getLong(2)
      if (l > Integer.MAX_VALUE) throw new FrameTranscoder.TranscodeError("Frame is too long")
      else l.toInt
    } else throw new FrameTranscoder.TranscodeError("Length error")
  }

  private def getMsgLength(in: ByteBuffer) = {
    var totalLen = 2
    if ((in.get(1) & MASK) != 0) totalLen += 4

    val len = in.get(1) & LENGTH

    if (len == 126) totalLen += 2
    if (len == 127) totalLen += 8

    if (in.remaining < totalLen) {
      -1
    } else {
      totalLen += bodyLength(in)

      if (in.remaining < totalLen) -1
      else totalLen
    }
  }
}

class FrameTranscoder(val isClient: Boolean) {
  def frameToBuffer(in: WebSocketFrame): Array[ByteBuffer] = {
    var size = 2

    if (isClient) size += 4 // for the mask

    if (in.length < 126) { /* NOOP */ } else if (in.length <= 0xffff) size += 2
    else size += 8

    val buff = ByteBuffer.allocate(if (isClient) size + in.length else size)

    val opcode = in.opcode

    if (in.length > 125 && (opcode == PING || opcode == PONG || opcode == CLOSE))
      throw new FrameTranscoder.TranscodeError("Invalid PING frame: frame too long: " + in.length)

    // First byte. Finished, reserved, and OP CODE
    val b1 = if (in.last) opcode | FINISHED else opcode

    buff.put(b1.byteValue)

    // Second byte. Mask bit and length
    var b2 = 0x0

    if (isClient) b2 = MASK

    if (in.length < 126) b2 |= in.length
    else if (in.length <= 0xffff) b2 |= 126
    else b2 |= 127

    buff.put(b2.byteValue)

    // Put the length if we have an extended length packet
    if (in.length > 125 && in.length <= 0xffff) {
      buff.put((in.length >>> 8 & 0xff).toByte).put((in.length & 0xff).toByte)
    } else if (in.length > 0xffff) buff.putLong(in.length.toLong)

    // If we are a client, we need to mask the data, else just wrap it in a buffer and done
    if (isClient && in.length > 0) { // need to mask outgoing bytes
      val mask = (Math.random * Integer.MAX_VALUE).toInt
      val maskBits = Array(
        ((mask >>> 24) & 0xff).toByte,
        ((mask >>> 16) & 0xff).toByte,
        ((mask >>> 8) & 0xff).toByte,
        ((mask >>> 0) & 0xff).toByte)

      buff.put(maskBits)

      val data = in.data

      for (i <- 0 until in.length.toInt) {
        buff.put((data(i.toLong) ^ maskBits(i & 0x3)).toByte) // i & 0x3 is the same as i % 4 but faster
      }
      buff.flip
      Array[ByteBuffer](buff)
    } else {
      buff.flip
      Array[ByteBuffer](buff, in.data.toByteBuffer)
    }
  }

  /** Method that decodes ByteBuffers to objects. None reflects not enough data to decode a message
    * Any unused data in the ByteBuffer will be recycled and available for the next read
    *
    * @param in ByteBuffer of immediately available data
    * @return optional message if enough data was available
    */
  def bufferToFrame(in: ByteBuffer): WebSocketFrame =
    if (in.remaining < 2 || FrameTranscoder.getMsgLength(in) < 0) {
      null
    } else {
      val opcode = in.get(0) & OP_CODE
      val finished = (in.get(0) & FINISHED) != 0
      val masked = (in.get(1) & MASK) != 0

      if (masked && isClient)
        throw new FrameTranscoder.TranscodeError("Client received a masked message")

      var bodyOffset = FrameTranscoder.lengthOffset(in)

      val m = if (masked) {
        bodyOffset += 4
        FrameTranscoder.getMask(in)
      } else {
        null
      }

      val oldLim = in.limit()
      val bodylen = FrameTranscoder.bodyLength(in)

      in.position(bodyOffset)
      in.limit(in.position() + bodylen)

      val slice = in.slice
      in.position(in.limit)
      in.limit(oldLim)

      makeFrame(opcode, ByteVector.view(FrameTranscoder.decodeBinary(slice, m)), finished)
    }
}

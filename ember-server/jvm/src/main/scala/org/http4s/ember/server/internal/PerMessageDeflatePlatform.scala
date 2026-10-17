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

package org.http4s.ember.server.internal

import fs2.io.net.ProtocolException
import org.http4s.websocket.FrameTranscoder.TranscodeError
import scodec.bits.ByteVector

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

private[internal] trait PerMessageDeflatePlatform {
  private[internal] val codecFactory: Option[() => PerMessageDeflate.Codec] =
    Some(() => new PerMessageDeflatePlatform.ZipCodec)
}

private[internal] object PerMessageDeflatePlatform {
  private val MaxFinalBlocks = 8
  private val BufferSize = 8192
  private val WindowSize = 32768
  private val Tail = PerMessageDeflate.Tail.toArray

  private final class ZipCodec extends PerMessageDeflate.Codec {
    private[this] var deflater: Deflater = null
    private[this] var deflateBuffer: Array[Byte] = null
    private[this] var inflater: Inflater = null
    private[this] var inflateBuffer: Array[Byte] = null
    private[this] var window: Array[Byte] = null
    private[this] var windowEnd = 0
    private[this] var windowFull = false
    private[this] var streamEnded = false
    private[this] var finalBlocks = 0

    def deflate(data: ByteVector): ByteVector = {
      if (deflater == null) {
        deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflateBuffer = new Array[Byte](BufferSize)
      }
      deflater.setInput(data.toArray)
      val out = new ByteArrayOutputStream()
      var n = deflateBuffer.length
      while (n == deflateBuffer.length) {
        n = deflater.deflate(deflateBuffer, 0, deflateBuffer.length, Deflater.SYNC_FLUSH)
        out.write(deflateBuffer, 0, n)
      }
      ByteVector.view(out.toByteArray)
    }

    def endDeflater(): Unit =
      if (deflater != null) {
        deflater.end()
        deflater = null
        deflateBuffer = null
      }

    def inflate(data: ByteVector, last: Boolean, maxSize: Int): ByteVector = {
      if (inflater == null) {
        inflater = new Inflater(true)
        inflateBuffer = new Array[Byte](BufferSize)
        window = new Array[Byte](WindowSize)
      }
      val out = new ByteArrayOutputStream()
      try {
        feed(data.toArray, out, maxSize)
        if (last) {
          if (!streamEnded) feed(Tail, out, maxSize)
          finalBlocks = 0
        }
      } catch {
        case e: DataFormatException =>
          throw new ProtocolException(s"Invalid compressed data: ${e.getMessage}")
      }
      ByteVector.view(out.toByteArray)
    }

    def resetInflater(): Unit =
      if (inflater != null) {
        inflater.reset()
        windowEnd = 0
        windowFull = false
        streamEnded = false
      }

    def close(): Unit = {
      endDeflater()
      if (inflater != null) inflater.end()
    }

    private def feed(input: Array[Byte], out: ByteArrayOutputStream, maxSize: Int): Unit = {
      if (input.nonEmpty) streamEnded = false
      inflater.setInput(input)
      var more = true
      while (more) {
        val n = inflater.inflate(inflateBuffer)
        out.write(inflateBuffer, 0, n)
        if (out.size() > maxSize)
          throw TranscodeError.maxFrameSizeExceeded("Decompressed message is too big")
        remember(n)
        if (inflater.finished()) {
          finalBlocks += 1
          if (finalBlocks > MaxFinalBlocks)
            throw new ProtocolException("Too many final DEFLATE blocks in one message")
          val remaining = inflater.getRemaining()
          restart()
          streamEnded = remaining == 0
          if (!streamEnded) inflater.setInput(input, input.length - remaining, remaining)
        }
        more = n == inflateBuffer.length || !inflater.needsInput()
      }
    }

    private def remember(length: Int): Unit = {
      val first = math.min(length, WindowSize - windowEnd)
      System.arraycopy(inflateBuffer, 0, window, windowEnd, first)
      System.arraycopy(inflateBuffer, first, window, 0, length - first)
      if (windowEnd + length >= WindowSize) windowFull = true
      windowEnd = (windowEnd + length) % WindowSize
    }

    private def restart(): Unit = {
      inflater.reset()
      if (windowFull) {
        val dictionary = new Array[Byte](WindowSize)
        System.arraycopy(window, windowEnd, dictionary, 0, WindowSize - windowEnd)
        System.arraycopy(window, 0, dictionary, WindowSize - windowEnd, windowEnd)
        inflater.setDictionary(dictionary)
      } else if (windowEnd > 0) inflater.setDictionary(window, 0, windowEnd)
    }
  }
}

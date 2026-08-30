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
  private[internal] val isSupported: Boolean = true

  private[internal] def createCodec(): PerMessageDeflate.Codec =
    new PerMessageDeflatePlatform.ZipCodec
}

private[internal] object PerMessageDeflatePlatform {
  private val WindowSize = 32768

  private final class ZipCodec extends PerMessageDeflate.Codec {
    private[this] val deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
    private[this] val inflater = new Inflater(true)
    private[this] val deflateBuffer = new Array[Byte](8192)
    private[this] val inflateBuffer = new Array[Byte](8192)
    private[this] val window = new Array[Byte](WindowSize)
    private[this] var windowEnd = 0
    private[this] var windowFull = false

    def deflate(data: ByteVector): ByteVector = {
      deflater.setInput(data.toArray)
      val out = new ByteArrayOutputStream()
      var n = deflateBuffer.length
      while (n == deflateBuffer.length) {
        n = deflater.deflate(deflateBuffer, 0, deflateBuffer.length, Deflater.SYNC_FLUSH)
        out.write(deflateBuffer, 0, n)
      }
      ByteVector.view(out.toByteArray)
    }

    def resetDeflater(): Unit = deflater.reset()

    def inflate(data: ByteVector, maxSize: Int): ByteVector = {
      val input = data.toArray
      inflater.setInput(input)
      val out = new ByteArrayOutputStream()
      var more = true
      try
        while (more) {
          val n = inflater.inflate(inflateBuffer)
          if (n > 0) {
            out.write(inflateBuffer, 0, n)
            if (out.size() > maxSize)
              throw TranscodeError.maxFrameSizeExceeded(
                "Decompressed message exceeds the size limit"
              )
            remember(inflateBuffer, n)
          } else if (inflater.needsDictionary())
            throw new ProtocolException("Compressed data requires a preset dictionary")
          if (inflater.finished()) {
            val remaining = inflater.getRemaining()
            restart()
            if (remaining > 0) inflater.setInput(input, input.length - remaining, remaining)
          }
          more = n == inflateBuffer.length || !inflater.needsInput()
        }
      catch {
        case e: DataFormatException =>
          throw new ProtocolException(s"Invalid compressed data: ${e.getMessage}")
      }
      ByteVector.view(out.toByteArray)
    }

    def resetInflater(): Unit = {
      inflater.reset()
      windowEnd = 0
      windowFull = false
    }

    private def remember(bytes: Array[Byte], length: Int): Unit = {
      val first = math.min(length, WindowSize - windowEnd)
      System.arraycopy(bytes, 0, window, windowEnd, first)
      System.arraycopy(bytes, first, window, 0, length - first)
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

    def close(): Unit = {
      deflater.end()
      inflater.end()
    }
  }
}

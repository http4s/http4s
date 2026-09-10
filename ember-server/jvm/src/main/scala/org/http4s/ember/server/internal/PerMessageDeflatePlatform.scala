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
  private final class ZipCodec extends PerMessageDeflate.Codec {
    private[this] var deflater: Deflater = null
    private[this] var inflater: Inflater = null
    private[this] val deflateBuffer = new Array[Byte](8192)
    private[this] val inflateBuffer = new Array[Byte](8192)

    def deflate(data: ByteVector): ByteVector = {
      if (deflater == null) deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
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
      }

    def inflate(data: ByteVector, maxSize: Int): ByteVector = {
      if (inflater == null) inflater = new Inflater(true)
      val input = data.toArray
      inflater.setInput(input)
      val out = new ByteArrayOutputStream()
      var more = true
      try
        while (more) {
          val n = inflater.inflate(inflateBuffer)
          out.write(inflateBuffer, 0, n)
          if (out.size() > maxSize)
            throw TranscodeError.maxFrameSizeExceeded("Decompressed message is too big")
          if (inflater.finished()) {
            val remaining = inflater.getRemaining()
            inflater.reset()
            inflater.setInput(input, input.length - remaining, remaining)
          }
          more = n == inflateBuffer.length || !inflater.needsInput()
        }
      catch {
        case e: DataFormatException =>
          throw new ProtocolException(s"Invalid compressed data: ${e.getMessage}")
      }
      ByteVector.view(out.toByteArray)
    }

    def resetInflater(): Unit = if (inflater != null) inflater.reset()

    def close(): Unit = {
      endDeflater()
      if (inflater != null) inflater.end()
    }
  }
}

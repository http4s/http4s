/*
 * Copyright 2014 http4s.org
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

package org.http4s.server.middleware

import fs2.compression.DeflateParams
import scodec.bits.ByteVector

import java.util.Arrays
import java.util.zip.CRC32
import java.util.zip.Deflater

private[middleware] object GZipPlatform {
  private[this] val HeaderSize = 10
  private[this] val TrailerSize = 8
  private[this] val InitialMinSize = 256

  /** Compresses an in-memory body into a complete gzip member (RFC 1952) in one pass. */
  def gzipStrict(bytes: ByteVector, level: DeflateParams.Level): Option[ByteVector] = {
    val input = bytes.toArrayUnsafe
    val deflater = new Deflater(deflaterLevel(level), true)
    try {
      deflater.setInput(input)
      deflater.finish()
      // Text bodies usually compress to well under a quarter of their size;
      // the array grows if the output needs more room.
      val initial = math.max(InitialMinSize, input.length >>> 2)
      var out = new Array[Byte](HeaderSize + initial + TrailerSize)
      out(0) = 0x1f.toByte
      out(1) = 0x8b.toByte
      out(2) = Deflater.DEFLATED.toByte
      out(9) = 0xff.toByte // OS: unknown; FLG, MTIME and XFL stay 0
      var pos = HeaderSize
      while (!deflater.finished()) {
        if (pos == out.length - TrailerSize)
          out = Arrays.copyOf(out, out.length + (out.length >>> 1))
        pos += deflater.deflate(out, pos, out.length - TrailerSize - pos)
      }
      val crc = new CRC32()
      crc.update(input, 0, input.length)
      writeIntLE(out, pos, crc.getValue.toInt)
      writeIntLE(out, pos + 4, input.length)
      Some(ByteVector.view(out, 0, pos + TrailerSize))
    } finally deflater.end()
  }

  private[this] def writeIntLE(out: Array[Byte], offset: Int, value: Int): Unit = {
    out(offset) = value.toByte
    out(offset + 1) = (value >>> 8).toByte
    out(offset + 2) = (value >>> 16).toByte
    out(offset + 3) = (value >>> 24).toByte
  }

  private[this] def deflaterLevel(level: DeflateParams.Level): Int = {
    import DeflateParams.Level._
    level match {
      case DEFAULT => Deflater.DEFAULT_COMPRESSION
      case NO_COMPRESSION | ZERO => 0
      case BEST_SPEED | ONE => 1
      case TWO => 2
      case THREE => 3
      case FOUR => 4
      case FIVE => 5
      case SIX => 6
      case SEVEN => 7
      case EIGHT => 8
      case BEST_COMPRESSION | NINE => 9
    }
  }
}

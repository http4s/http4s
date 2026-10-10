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

import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all._
import scodec.bits.ByteVector

/** An independent HPACK oracle (RFC 7541), deliberately using a literal-only encoder. */
object HpackWire {
  private final case class State(entries: Vector[(String, String)], capacity: Int)

  final class Decoder[F[_]] private[HpackWire] (
      state: Ref[F, State],
      maxTableSize: Int,
      maxHeaderBytes: Int,
  )(implicit F: Sync[F]) {

    /** Decodes one complete header block, retaining its dynamic table for subsequent blocks. */
    def decode(bytes: ByteVector): F[Vector[(String, String)]] =
      state
        .modify { current =>
          decodeBlock(bytes, current, maxTableSize, maxHeaderBytes) match {
            case Right((next, headers)) => (next, Right(headers))
            case Left(error) => (current, Left(error))
          }
        }
        .flatMap(F.fromEither)
  }

  def decoder[F[_]: Sync](
      maxTableSize: Int = 4096,
      maxHeaderBytes: Int = 1024 * 1024,
  ): F[Decoder[F]] =
    if (maxTableSize < 0 || maxHeaderBytes < 0)
      Sync[F].raiseError(new IllegalArgumentException("negative HPACK limit"))
    else
      Ref
        .of[F, State](State(Vector.empty, maxTableSize))
        .map(new Decoder[F](_, maxTableSize, maxHeaderBytes))

  /** Encodes non-indexed, non-Huffman literals. Strings represent octets, not UTF-8 text. */
  def encodeLiteral(headers: Vector[(String, String)]): ByteVector =
    headers.foldLeft(ByteVector.empty) { case (bytes, (name, value)) =>
      bytes ++ ByteVector(0) ++ encodeString(name) ++ encodeString(value)
    }

  def encodeInteger(value: Int, prefixBits: Int, firstBits: Int = 0): ByteVector = {
    check(value >= 0 && prefixBits >= 1 && prefixBits <= 8, "invalid HPACK integer")
    val mask = (1 << prefixBits) - 1
    check(
      firstBits >= 0 && firstBits <= 255 && (firstBits & mask) == 0,
      "HPACK integer flags overlap its prefix",
    )
    if (value < mask) ByteVector((firstBits | value).toByte)
    else {
      @annotation.tailrec
      def loop(remaining: Int, bytes: ByteVector): ByteVector =
        if (remaining < 128) bytes ++ ByteVector(remaining.toByte)
        else loop(remaining >>> 7, bytes ++ ByteVector(((remaining & 0x7f) | 0x80).toByte))
      loop(value - mask, ByteVector((firstBits | mask).toByte))
    }
  }

  private def encodeString(value: String): ByteVector = {
    check(value.forall(_ <= 255), "HPACK strings must contain only octets")
    encodeInteger(value.length, 7) ++ ByteVector.view(value.iterator.map(_.toByte).toArray)
  }

  private def check(condition: Boolean, message: String): Unit =
    if (!condition) throw new IllegalArgumentException(message)

  private def entrySize(entry: (String, String)): Long =
    32L + entry._1.length.toLong + entry._2.length.toLong

  private def evict(entries: Vector[(String, String)], capacity: Int): Vector[(String, String)] = {
    @annotation.tailrec
    def loop(remaining: Vector[(String, String)], size: Long): Vector[(String, String)] =
      if (size <= capacity.toLong) remaining
      else loop(remaining.dropRight(1), size - entrySize(remaining.last))
    loop(entries, entries.foldLeft(0L)((n, e) => n + entrySize(e)))
  }

  private def decodeBlock(
      input: ByteVector,
      initial: State,
      maxTableSize: Int,
      maxHeaderBytes: Int,
  ): Either[Throwable, (State, Vector[(String, String)])] = Either.catchNonFatal {
    // All mutation is confined to a single pure decoding call; only a successful result is committed.
    check(input.size <= maxHeaderBytes.toLong + 1024L, "encoded HPACK block exceeds limit")
    var cursor = 0L
    var state = initial
    var seenHeader = false
    var headerBytes = 0L
    val headers = Vector.newBuilder[(String, String)]

    def octet(): Int = {
      check(cursor < input.size, "truncated HPACK field")
      val byte = input(cursor) & 0xff
      cursor += 1L
      byte
    }

    def integer(prefix: Int): Int = {
      val mask = (1 << prefix) - 1
      var value = (octet() & mask).toLong
      if (value == mask.toLong) {
        var shift = 0
        var more = true
        while (more) {
          check(shift <= 28, "HPACK integer overflow")
          val byte = octet()
          value += (byte & 0x7f).toLong << shift
          check(value <= Int.MaxValue.toLong, "HPACK integer overflow")
          more = (byte & 0x80) != 0
          shift += 7
        }
      }
      value.toInt
    }

    def string(limit: Int): String = {
      check(cursor < input.size, "truncated HPACK string")
      val huffman = (input(cursor) & 0x80) != 0
      val length = integer(7)
      check(length.toLong <= input.size - cursor, "truncated HPACK string")
      val encoded = input.slice(cursor, cursor + length.toLong)
      cursor += length.toLong
      val decoded =
        if (huffman) decodeHuffman(encoded, limit)
        else {
          check(length <= limit, "decoded HPACK string exceeds limit")
          encoded
        }
      decoded.toArray.iterator.map(b => (b & 0xff).toChar).mkString
    }

    def indexed(index: Int): (String, String) = {
      check(index > 0 && index <= staticTable.size + state.entries.size, "invalid HPACK index")
      if (index <= staticTable.size) staticTable(index - 1)
      else state.entries(index - staticTable.size - 1)
    }

    while (cursor < input.size) {
      val first = input(cursor) & 0xff
      if ((first & 0xe0) == 0x20) {
        check(!seenHeader, "HPACK table-size update after a header field")
        val capacity = integer(5)
        check(capacity <= maxTableSize, "HPACK table-size update exceeds advertised maximum")
        state = State(evict(state.entries, capacity), capacity)
      } else {
        seenHeader = true
        val entry =
          if ((first & 0x80) != 0) indexed(integer(7))
          else {
            val incremental = (first & 0x40) != 0
            val index = integer(if (incremental) 6 else 4)
            val available = math.max(0L, maxHeaderBytes.toLong - headerBytes - 32L).toInt
            val name = if (index == 0) string(available) else indexed(index)._1
            check(name.length <= available, "decoded HPACK header list exceeds limit")
            val value = string(available - name.length)
            val decoded = name -> value
            if (incremental)
              state = state.copy(entries = evict(decoded +: state.entries, state.capacity))
            decoded
          }
        headerBytes += entrySize(entry)
        check(headerBytes <= maxHeaderBytes.toLong, "decoded HPACK header list exceeds limit")
        headers += entry
      }
    }
    (state, headers.result())
  }

  private[wire] def decodeHuffman(input: ByteVector, maxBytes: Int): ByteVector = {
    check(maxBytes >= 0, "negative Huffman limit")
    val output = Vector.newBuilder[Byte]
    var outputSize = 0
    var code = 0
    var length = 0
    var i = 0L
    while (i < input.size) {
      val byte = input(i) & 0xff
      var bit = 7
      while (bit >= 0) {
        code = (code << 1) | ((byte >>> bit) & 1)
        length += 1
        huffmanSymbols.get(length -> code) match {
          case Some(symbol) =>
            check(symbol != 256, "HPACK Huffman EOS symbol in string")
            check(outputSize < maxBytes, "decoded HPACK Huffman string exceeds limit")
            output += symbol.toByte
            outputSize += 1
            code = 0
            length = 0
          case None => check(length < 30, "invalid HPACK Huffman code")
        }
        bit -= 1
      }
      i += 1L
    }
    check(length <= 7 && code == (1 << length) - 1, "invalid HPACK Huffman padding")
    ByteVector.view(output.result().toArray)
  }

  /*
   * The following two data tables are transcribed from RFC 7541, Appendices A and B:
   * https://www.rfc-editor.org/rfc/rfc7541#appendix-A
   * https://www.rfc-editor.org/rfc/rfc7541#appendix-B
   * Copyright (c) 2015 IETF Trust and the persons identified as authors of the code.
   * All rights reserved.
   *
   * Redistribution and use in source and binary forms, with or without modification, are
   * permitted provided that the following conditions are met:
   *
   * 1. Redistributions of source code must retain the above copyright notice, this list of
   *    conditions and the following disclaimer.
   * 2. Redistributions in binary form must reproduce the above copyright notice, this list
   *    of conditions and the following disclaimer in the documentation and/or other materials
   *    provided with the distribution.
   *
   * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
   * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
   * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
   * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
   * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
   * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
   * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
   * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
   */
  private val staticTable: Vector[(String, String)] = Vector(
    ":authority" -> "",
    ":method" -> "GET",
    ":method" -> "POST",
    ":path" -> "/",
    ":path" -> "/index.html",
    ":scheme" -> "http",
    ":scheme" -> "https",
    ":status" -> "200",
    ":status" -> "204",
    ":status" -> "206",
    ":status" -> "304",
    ":status" -> "400",
    ":status" -> "404",
    ":status" -> "500",
    "accept-charset" -> "",
    "accept-encoding" -> "gzip, deflate",
    "accept-language" -> "",
    "accept-ranges" -> "",
    "accept" -> "",
    "access-control-allow-origin" -> "",
    "age" -> "",
    "allow" -> "",
    "authorization" -> "",
    "cache-control" -> "",
    "content-disposition" -> "",
    "content-encoding" -> "",
    "content-language" -> "",
    "content-length" -> "",
    "content-location" -> "",
    "content-range" -> "",
    "content-type" -> "",
    "cookie" -> "",
    "date" -> "",
    "etag" -> "",
    "expect" -> "",
    "expires" -> "",
    "from" -> "",
    "host" -> "",
    "if-match" -> "",
    "if-modified-since" -> "",
    "if-none-match" -> "",
    "if-range" -> "",
    "if-unmodified-since" -> "",
    "last-modified" -> "",
    "link" -> "",
    "location" -> "",
    "max-forwards" -> "",
    "proxy-authenticate" -> "",
    "proxy-authorization" -> "",
    "range" -> "",
    "referer" -> "",
    "refresh" -> "",
    "retry-after" -> "",
    "server" -> "",
    "set-cookie" -> "",
    "strict-transport-security" -> "",
    "transfer-encoding" -> "",
    "user-agent" -> "",
    "vary" -> "",
    "via" -> "",
    "www-authenticate" -> "",
  )

  private val huffmanCodes: Vector[(Int, Int)] = Vector(
    0x1ff8 -> 13,
    0x7fffd8 -> 23,
    0xfffffe2 -> 28,
    0xfffffe3 -> 28,
    0xfffffe4 -> 28,
    0xfffffe5 -> 28,
    0xfffffe6 -> 28,
    0xfffffe7 -> 28,
    0xfffffe8 -> 28,
    0xffffea -> 24,
    0x3ffffffc -> 30,
    0xfffffe9 -> 28,
    0xfffffea -> 28,
    0x3ffffffd -> 30,
    0xfffffeb -> 28,
    0xfffffec -> 28,
    0xfffffed -> 28,
    0xfffffee -> 28,
    0xfffffef -> 28,
    0xffffff0 -> 28,
    0xffffff1 -> 28,
    0xffffff2 -> 28,
    0x3ffffffe -> 30,
    0xffffff3 -> 28,
    0xffffff4 -> 28,
    0xffffff5 -> 28,
    0xffffff6 -> 28,
    0xffffff7 -> 28,
    0xffffff8 -> 28,
    0xffffff9 -> 28,
    0xffffffa -> 28,
    0xffffffb -> 28,
    0x14 -> 6,
    0x3f8 -> 10,
    0x3f9 -> 10,
    0xffa -> 12,
    0x1ff9 -> 13,
    0x15 -> 6,
    0xf8 -> 8,
    0x7fa -> 11,
    0x3fa -> 10,
    0x3fb -> 10,
    0xf9 -> 8,
    0x7fb -> 11,
    0xfa -> 8,
    0x16 -> 6,
    0x17 -> 6,
    0x18 -> 6,
    0x0 -> 5,
    0x1 -> 5,
    0x2 -> 5,
    0x19 -> 6,
    0x1a -> 6,
    0x1b -> 6,
    0x1c -> 6,
    0x1d -> 6,
    0x1e -> 6,
    0x1f -> 6,
    0x5c -> 7,
    0xfb -> 8,
    0x7ffc -> 15,
    0x20 -> 6,
    0xffb -> 12,
    0x3fc -> 10,
    0x1ffa -> 13,
    0x21 -> 6,
    0x5d -> 7,
    0x5e -> 7,
    0x5f -> 7,
    0x60 -> 7,
    0x61 -> 7,
    0x62 -> 7,
    0x63 -> 7,
    0x64 -> 7,
    0x65 -> 7,
    0x66 -> 7,
    0x67 -> 7,
    0x68 -> 7,
    0x69 -> 7,
    0x6a -> 7,
    0x6b -> 7,
    0x6c -> 7,
    0x6d -> 7,
    0x6e -> 7,
    0x6f -> 7,
    0x70 -> 7,
    0x71 -> 7,
    0x72 -> 7,
    0xfc -> 8,
    0x73 -> 7,
    0xfd -> 8,
    0x1ffb -> 13,
    0x7fff0 -> 19,
    0x1ffc -> 13,
    0x3ffc -> 14,
    0x22 -> 6,
    0x7ffd -> 15,
    0x3 -> 5,
    0x23 -> 6,
    0x4 -> 5,
    0x24 -> 6,
    0x5 -> 5,
    0x25 -> 6,
    0x26 -> 6,
    0x27 -> 6,
    0x6 -> 5,
    0x74 -> 7,
    0x75 -> 7,
    0x28 -> 6,
    0x29 -> 6,
    0x2a -> 6,
    0x7 -> 5,
    0x2b -> 6,
    0x76 -> 7,
    0x2c -> 6,
    0x8 -> 5,
    0x9 -> 5,
    0x2d -> 6,
    0x77 -> 7,
    0x78 -> 7,
    0x79 -> 7,
    0x7a -> 7,
    0x7b -> 7,
    0x7ffe -> 15,
    0x7fc -> 11,
    0x3ffd -> 14,
    0x1ffd -> 13,
    0xffffffc -> 28,
    0xfffe6 -> 20,
    0x3fffd2 -> 22,
    0xfffe7 -> 20,
    0xfffe8 -> 20,
    0x3fffd3 -> 22,
    0x3fffd4 -> 22,
    0x3fffd5 -> 22,
    0x7fffd9 -> 23,
    0x3fffd6 -> 22,
    0x7fffda -> 23,
    0x7fffdb -> 23,
    0x7fffdc -> 23,
    0x7fffdd -> 23,
    0x7fffde -> 23,
    0xffffeb -> 24,
    0x7fffdf -> 23,
    0xffffec -> 24,
    0xffffed -> 24,
    0x3fffd7 -> 22,
    0x7fffe0 -> 23,
    0xffffee -> 24,
    0x7fffe1 -> 23,
    0x7fffe2 -> 23,
    0x7fffe3 -> 23,
    0x7fffe4 -> 23,
    0x1fffdc -> 21,
    0x3fffd8 -> 22,
    0x7fffe5 -> 23,
    0x3fffd9 -> 22,
    0x7fffe6 -> 23,
    0x7fffe7 -> 23,
    0xffffef -> 24,
    0x3fffda -> 22,
    0x1fffdd -> 21,
    0xfffe9 -> 20,
    0x3fffdb -> 22,
    0x3fffdc -> 22,
    0x7fffe8 -> 23,
    0x7fffe9 -> 23,
    0x1fffde -> 21,
    0x7fffea -> 23,
    0x3fffdd -> 22,
    0x3fffde -> 22,
    0xfffff0 -> 24,
    0x1fffdf -> 21,
    0x3fffdf -> 22,
    0x7fffeb -> 23,
    0x7fffec -> 23,
    0x1fffe0 -> 21,
    0x1fffe1 -> 21,
    0x3fffe0 -> 22,
    0x1fffe2 -> 21,
    0x7fffed -> 23,
    0x3fffe1 -> 22,
    0x7fffee -> 23,
    0x7fffef -> 23,
    0xfffea -> 20,
    0x3fffe2 -> 22,
    0x3fffe3 -> 22,
    0x3fffe4 -> 22,
    0x7ffff0 -> 23,
    0x3fffe5 -> 22,
    0x3fffe6 -> 22,
    0x7ffff1 -> 23,
    0x3ffffe0 -> 26,
    0x3ffffe1 -> 26,
    0xfffeb -> 20,
    0x7fff1 -> 19,
    0x3fffe7 -> 22,
    0x7ffff2 -> 23,
    0x3fffe8 -> 22,
    0x1ffffec -> 25,
    0x3ffffe2 -> 26,
    0x3ffffe3 -> 26,
    0x3ffffe4 -> 26,
    0x7ffffde -> 27,
    0x7ffffdf -> 27,
    0x3ffffe5 -> 26,
    0xfffff1 -> 24,
    0x1ffffed -> 25,
    0x7fff2 -> 19,
    0x1fffe3 -> 21,
    0x3ffffe6 -> 26,
    0x7ffffe0 -> 27,
    0x7ffffe1 -> 27,
    0x3ffffe7 -> 26,
    0x7ffffe2 -> 27,
    0xfffff2 -> 24,
    0x1fffe4 -> 21,
    0x1fffe5 -> 21,
    0x3ffffe8 -> 26,
    0x3ffffe9 -> 26,
    0xffffffd -> 28,
    0x7ffffe3 -> 27,
    0x7ffffe4 -> 27,
    0x7ffffe5 -> 27,
    0xfffec -> 20,
    0xfffff3 -> 24,
    0xfffed -> 20,
    0x1fffe6 -> 21,
    0x3fffe9 -> 22,
    0x1fffe7 -> 21,
    0x1fffe8 -> 21,
    0x7ffff3 -> 23,
    0x3fffea -> 22,
    0x3fffeb -> 22,
    0x1ffffee -> 25,
    0x1ffffef -> 25,
    0xfffff4 -> 24,
    0xfffff5 -> 24,
    0x3ffffea -> 26,
    0x7ffff4 -> 23,
    0x3ffffeb -> 26,
    0x7ffffe6 -> 27,
    0x3ffffec -> 26,
    0x3ffffed -> 26,
    0x7ffffe7 -> 27,
    0x7ffffe8 -> 27,
    0x7ffffe9 -> 27,
    0x7ffffea -> 27,
    0x7ffffeb -> 27,
    0xffffffe -> 28,
    0x7ffffec -> 27,
    0x7ffffed -> 27,
    0x7ffffee -> 27,
    0x7ffffef -> 27,
    0x7fffff0 -> 27,
    0x3ffffee -> 26,
    0x3fffffff -> 30,
  )

  private lazy val huffmanSymbols: Map[(Int, Int), Int] =
    huffmanCodes.zipWithIndex.map { case ((code, length), symbol) =>
      (length -> code) -> symbol
    }.toMap
}

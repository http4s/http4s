/*
 * Copyright 2013 http4s.org
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

package org.http4s

import cats.syntax.all._
import fs2._
import fs2.text.utf8Decode
import org.http4s.internal.decode
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop.propBoolean

import java.nio.charset.CharacterCodingException
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import java.nio.charset.UnmappableCharacterException
import java.nio.charset.{Charset => JCharset}

class DecodeSpec extends Http4sSuite {
  {
    test("decode should be consistent with utf8Decode") {
      forAll { (s: String, chunkSize: Int) =>
        (chunkSize > 0) ==> {
          val source = Stream
            .emits {
              s.getBytes(StandardCharsets.UTF_8)
                .grouped(chunkSize)
                .map(_.toArray)
                .map(Chunk.bytes)
                .toSeq
            }
            .flatMap(Stream.chunk)
          val utf8Decoded = utf8Decode(source).toList.combineAll
          val decoded = source.through(decode[Fallible](Charset.`UTF-8`)).compile.string
          decoded == Right(utf8Decoded)
        }
      }
    }

    test("decode should be consistent with String constructor over aggregated output") {
      forAll { (cs: Charset, s: String, chunkSize: Int) =>
        // x-COMPOUND_TEXT fails with a read only buffer.
        (chunkSize > 0 && cs.nioCharset.canEncode && cs.nioCharset.name != "x-COMPOUND_TEXT") ==> {
          val source: Stream[Pure, Byte] = Stream
            .emits {
              s.getBytes(cs.nioCharset)
                .grouped(chunkSize)
                .map(Chunk.bytes)
                .toSeq
            }
            .flatMap(Stream.chunk)
          val expected = new String(source.toVector.toArray, cs.nioCharset)
          !expected.contains("\ufffd") ==> {
            // \ufffd means we generated a String unrepresentable by the charset
            val decoded = source.through(decode[Fallible](cs)).compile.string
            decoded == Right(expected)
          }
        }
      }
    }

    test("decode should decode an empty chunk") {
      forAll { (cs: Charset) =>
        val source: Stream[Pure, Byte] = Stream.chunk[Pure, Byte](Chunk.empty[Byte])
        val expected = new String(source.toVector.toArray, cs.nioCharset)
        !expected.contains("\ufffd") ==> {
          // \ufffd means we generated a String unrepresentable by the charset
          val decoded = source.through(decode[Fallible](cs)).compile.string
          decoded == Right(expected)
        }
      }
    }

    test("decode should drop Byte Order Mark") {
      val source = Stream(0xef.toByte, 0xbb.toByte, 0xbf.toByte)
      val decoded = source.through(decode[Fallible](Charset.`UTF-8`)).compile.string
      decoded == Right("")
    }

    test("decode should handle malformed input") {
      // Not a valid first byte in UTF-8
      val source = Stream(0x80.toByte)
      val decoded = source.through(decode[Fallible](Charset.`UTF-8`)).compile.string
      val Left(_: MalformedInputException) = decoded
    }

    test("decode should handle incomplete input") {
      // Only the first byte of a two-byte UTF-8 sequence
      val source = Stream(0xc2.toByte)
      val decoded = source.through(decode[Fallible](Charset.`UTF-8`)).compile.string
      val Left(_: MalformedInputException) = decoded
    }

    test("decode should handle unmappable character") {
      // https://stackoverflow.com/a/22902806
      val source = Stream(0x80.toByte, 0x81.toByte)
      val decoded =
        source.through(decode[Fallible](Charset(JCharset.forName("IBM1098")))).compile.string
      val Left(_: UnmappableCharacterException) = decoded
    }

    test("decode should handle overflows") {
      // Found by scalachek
      val source = Stream(-36.toByte)
      val decoded =
        source.through(decode[Fallible](Charset(JCharset.forName("x-ISCII91")))).compile.string
      assertEquals(decoded, Right("ी"))
    }

    test("decode should not crash in IllegalStateException") {
      // Found by scalachek
      val source = Stream(-1.toByte)
      val decoded =
        source.through(decode[Fallible](Charset(JCharset.forName("x-IBM943")))).compile.string
      val Left(_: MalformedInputException) = decoded
    }

    test("decode should either succeed or raise a CharacterCodingException") {
      forAll { (bs: Array[Byte], cs: Charset) =>
        val source = Stream.emits(bs)
        val decoded = source.through(decode[Fallible](cs)).compile.drain
        decoded match {
          case Left(_: CharacterCodingException) => true
          case Right(_) => true
          case _ => false
        }
      }
    }
  }
}

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
import java.nio.charset.{Charset => JCharset}
import java.nio.charset.{
  CharacterCodingException,
  MalformedInputException,
  UnmappableCharacterException
}
import fs2._
import fs2.text.utf8Decode
import org.http4s.internal.decode
import java.nio.charset.StandardCharsets

class DecodeSpec extends Http4sSpec {
  "decode" should {
    "be consistent with utf8Decode" in prop { (s: String, chunkSize: Int) =>
      (chunkSize > 0) ==> {
        val source = Stream
          .emits {
            s.getBytes(StandardCharsets.UTF_8)
              .grouped(chunkSize)
              .map(_.toArray)
              .map(Chunk.array[Byte])
              .toSeq
          }
          .flatMap(Stream.chunk[Pure, Byte])
        val utf8Decoded = utf8Decode(source).toList.combineAll
        val decoded = source.through(decode[Fallible](Charset.`UTF-8`)).compile.string
        decoded must_== Right(utf8Decoded)
      }
    }

    "be consistent with String constructor over aggregated output" in prop {
      (cs: Charset, s: String, chunkSize: Int) =>
        // x-COMPOUND_TEXT fails with a read only buffer.
        (chunkSize > 0 && cs.nioCharset.canEncode && cs.nioCharset.name != "x-COMPOUND_TEXT") ==> {
          val source: Stream[Pure, Byte] = Stream
            .emits {
              s.getBytes(cs.nioCharset)
                .grouped(chunkSize)
                .map(Chunk.array[Byte])
                .toSeq
            }
            .flatMap(Stream.chunk[Pure, Byte])
          val expected = new String(source.toVector.toArray, cs.nioCharset)
          !expected.contains("\ufffd") ==> {
            // \ufffd means we generated a String unrepresentable by the charset
            val decoded = source.through(decode[Fallible](cs)).compile.string
            decoded must_== Right(expected)
          }
        }
    }

    "decode an empty chunk" in prop { (cs: Charset) =>
      val source: Stream[Pure, Byte] = Stream.chunk[Pure, Byte](Chunk.empty[Byte])
      val expected = new String(source.toVector.toArray, cs.nioCharset)
      !expected.contains("\ufffd") ==> {
        // \ufffd means we generated a String unrepresentable by the charset
        val decoded = source.through(decode[Fallible](cs)).compile.string
        decoded must_== Right(expected)
      }
    }

    "drop Byte Order Mark" in {
      val source = Stream(0xef.toByte, 0xbb.toByte, 0xbf.toByte)
      val decoded = source.through(decode[Fallible](Charset.`UTF-8`)).compile.string
      decoded must beRight("")
    }

    "handle malformed input" in {
      // Not a valid first byte in UTF-8
      val source = Stream(0x80.toByte)
      val decoded = source.through(decode[Fallible](Charset.`UTF-8`)).compile.string
      decoded must beLeft(beAnInstanceOf[MalformedInputException])
    }

    "handle incomplete input" in {
      // Only the first byte of a two-byte UTF-8 sequence
      val source = Stream(0xc2.toByte)
      val decoded = source.through(decode[Fallible](Charset.`UTF-8`)).compile.string
      decoded must beLeft(beAnInstanceOf[MalformedInputException])
    }

    "handle unmappable character" in {
      // https://stackoverflow.com/a/22902806
      val source = Stream(0x80.toByte, 0x81.toByte)
      val decoded =
        source.through(decode[Fallible](Charset(JCharset.forName("IBM1098")))).compile.string
      decoded must beLeft(beAnInstanceOf[UnmappableCharacterException])
    }

    "handle overflows" in {
      // Found by scalachek
      val source = Stream(-36.toByte)
      val decoded =
        source.through(decode[Fallible](Charset(JCharset.forName("x-ISCII91")))).compile.string
      decoded must_== Right("ी")
    }

    "not crash in IllegalStateException" in {
      // Found by scalachek
      val source = Stream(-1.toByte)
      val decoded =
        source.through(decode[Fallible](Charset(JCharset.forName("x-IBM943")))).compile.string
      decoded must beLeft(beAnInstanceOf[MalformedInputException])
    }

    "either succeed or raise a CharacterCodingException" in prop { (bs: Array[Byte], cs: Charset) =>
      val source = Stream.emits(bs)
      val decoded = source.through(decode[Fallible](cs)).compile.drain
      decoded must beLike {
        case Left(_: CharacterCodingException) => ok
        case Right(_) if true => ok
      }
    }
  }
}

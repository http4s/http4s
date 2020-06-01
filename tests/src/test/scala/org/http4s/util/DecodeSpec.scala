/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package util

import cats.implicits._
import java.nio.charset.{Charset => JCharset}
import java.nio.charset.{
  CharacterCodingException,
  MalformedInputException,
  UnmappableCharacterException
}
import fs2._
import fs2.text.utf8Decode
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
              .map(Chunk.bytes)
              .toSeq
          }
          .flatMap(Stream.chunk)
        val utf8Decoded = utf8Decode(source).toList.combineAll
        val decoded = decode(Charset.`UTF-8`)(source).toList.combineAll
        decoded must_== utf8Decoded
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
                .map(Chunk.bytes)
                .toSeq
            }
            .flatMap(Stream.chunk)
          val expected = new String(source.toVector.toArray, cs.nioCharset)
          !expected.contains("\ufffd") ==> {
            // \ufffd means we generated a String unrepresentable by the charset
            val decoded = decode(cs)(source).toList.combineAll
            decoded must_== expected
          }
        }
    }

    "decode an empty chunk" in prop { (cs: Charset) =>
      val source: Stream[Pure, Byte] = Stream.chunk[Pure, Byte](Chunk.empty[Byte])
      val expected = new String(source.toVector.toArray, cs.nioCharset)
      !expected.contains("\ufffd") ==> {
        // \ufffd means we generated a String unrepresentable by the charset
        val decoded = decode(cs)(source).toList.combineAll
        decoded must_== expected
      }
    }

    "drop Byte Order Mark" in {
      val source = Stream.emits(Seq(0xef.toByte, 0xbb.toByte, 0xbf.toByte))
      val decoded = decode(Charset.`UTF-8`)(source).toList.combineAll
      decoded must_== ""
    }

    "handle malformed input" in {
      // Not a valid first byte in UTF-8
      val source: Stream[Fallible, Byte] = Stream.emits(Seq(0x80.toByte))
      val decoded = decode(Charset.`UTF-8`)(source).compile.string
      decoded must beLeft(beAnInstanceOf[MalformedInputException])
    }

    "handle incomplete input" in {
      // Only the first byte of a two-byte UTF-8 sequence
      val source: Stream[Fallible, Byte] = Stream.emits(Seq(0xc2.toByte))
      val decoded = decode(Charset.`UTF-8`)(source).compile.string // incorrect encoding provided
      decoded must beLeft(beAnInstanceOf[MalformedInputException])
    }

    "handle unmappable character" in {
      // https://stackoverflow.com/a/22902806
      val source: Stream[Fallible, Byte] = Stream.emits(Seq(0x80.toByte, 0x81.toByte))
      val decoded = decode(Charset(JCharset.forName("IBM1098")))(source).compile.string
      decoded must beLeft(beAnInstanceOf[UnmappableCharacterException])
    }

    "either succeed or raise a CharacterCodingException" in prop { (bs: Array[Byte], cs: Charset) =>
      val decoded = decode(cs)(Stream.emits[Fallible, Byte](bs)).compile.drain
      decoded must beLike {
        case Left(_: CharacterCodingException) => ok
        case Right(_) if true => ok
      }
    }
  }
}

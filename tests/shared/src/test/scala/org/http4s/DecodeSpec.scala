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
import fs2.text.decodeWithCharset
import fs2.text.utf8
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop.propBoolean

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import java.nio.charset.UnmappableCharacterException
import java.nio.charset.{Charset => JCharset}
import scala.util.Try

class DecodeSpec extends Http4sSuite {
  override def scalaCheckInitialSeed = "UuhLRvSjo_SAS13rYR-NHxeNurZ-dZNzGgpxCYA_i6G="
  test("decode should be consistent with utf8.decode") {
    forAll { (s: String, chunkSize: Int) =>
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
        val utf8Decoded = utf8.decode(source).toList.combineAll
        val decoded =
          source.through(decodeWithCharset[Fallible](StandardCharsets.UTF_8)).compile.string
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
              .map(Chunk.array[Byte])
              .toSeq
          }
          .flatMap(Stream.chunk[Pure, Byte])
        val expected = trimBOM(new String(source.toVector.toArray, cs.nioCharset))
        !expected.contains("\ufffd") ==> {
          // \ufffd means we generated a String unrepresentable by the charset
          val decoded = source.through(decodeWithCharset[Fallible](cs.nioCharset)).compile.string
          decoded == Right(expected)
        }
      }
    }
  }

  test("decode should be consistent with String constructor with BOM") {
    val cs = Charset(StandardCharsets.UTF_8)
    val s = "\uFEFF" // EF BB BF
    val chunkSize = 1
    val source: Stream[Pure, Byte] = Stream
      .emits {
        s.getBytes(cs.nioCharset)
          .grouped(chunkSize)
          .map(Chunk.array[Byte])
          .toSeq
      }
      .flatMap(Stream.chunk[Pure, Byte])
    val expected = trimBOM(new String(source.toVector.toArray, cs.nioCharset))
    val decoded = source.through(decodeWithCharset[Fallible](cs.nioCharset)).compile.string
    assertEquals(decoded, Right(expected))
  }

  private def trimBOM(str: String): String =
    if (str.nonEmpty && str.head == '\ufeff') str.tail else str

  test("decode should decode an empty chunk") {
    forAll { (cs: Charset) =>
      val source: Stream[Pure, Byte] = Stream.chunk[Pure, Byte](Chunk.empty[Byte])
      val expected = new String(source.toVector.toArray, cs.nioCharset)
      !expected.contains("\ufffd") ==> {
        // \ufffd means we generated a String unrepresentable by the charset
        val decoded = source.through(decodeWithCharset[Fallible](cs.nioCharset)).compile.string
        decoded == Right(expected)
      }
    }
  }

  test("decode should drop Byte Order Mark") {
    val source = Stream(0xef.toByte, 0xbb.toByte, 0xbf.toByte)
    val decoded = source.through(decodeWithCharset[Fallible](StandardCharsets.UTF_8)).compile.string
    decoded == Right("")
  }

  test("decode should handle unmappable character") {
    assume(Platform.isJvm, "IBM1098 charset is unavailable on JS/Native")

    // https://stackoverflow.com/a/22902806
    val source = Stream(0x80.toByte, 0x81.toByte)
    val decoded =
      source.through(decodeWithCharset[Fallible](JCharset.forName("IBM1098"))).compile.string
    val Left(_: UnmappableCharacterException) = decoded
  }

  test("decode should handle overflows") {
    assume(Platform.isJvm, "x-ISCII91 charset is unavailable on JS/Native")

    // Found by scalachek
    val source = Stream(-36.toByte)
    val decoded =
      source.through(decodeWithCharset[Fallible](JCharset.forName("x-ISCII91"))).compile.string
    assertEquals(decoded, Right("ी"))
  }

  test("decode should not crash in IllegalStateException") {
    assume(Platform.isJvm, "x-ISCII91 charset is unavailable on JS/Native")

    // Found by scalachek
    val source = Stream(-1.toByte)
    val decoded =
      source.through(decodeWithCharset[Fallible](JCharset.forName("x-IBM943"))).compile.string
    val Left(_: MalformedInputException) = decoded
  }

  test("decode stream result should be consistent with nio's decode on full stream") {
    forAll { (bs: Array[Byte], cs: Charset) =>
      (cs != Charset.`UTF-8`) ==> {
        val referenceDecoder = cs.nioCharset
          .newDecoder()
          // setting these to be consistent with our decoder's behavior
          // note that java.nio.charset.Charset.decode and fs2's utf8.decode
          // will replace character instead of raising exception
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)

        val referenceResult = Try(referenceDecoder.decode(ByteBuffer.wrap(bs)).toString).toEither
        val source = Stream.emits(bs)
        val decoded = source.through(decodeWithCharset[Fallible](cs.nioCharset)).compile.foldMonoid
        // Ignoring the actual exception type
        assertEquals(decoded.toOption, referenceResult.toOption)
      }
    }
  }
}

package org.http4s
package util

import cats.implicits._
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

  }
}

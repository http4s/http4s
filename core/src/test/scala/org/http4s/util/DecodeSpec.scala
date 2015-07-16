package org.http4s
package util

import java.nio.charset.StandardCharsets

import org.specs2.ScalaCheck

import scalaz.stream.Process._
import scalaz.stream.text.utf8Decode
import scodec.bits._

import org.http4s.util.byteVector._

class DecodeSpec extends Http4sSpec with ScalaCheck {
  "decode" should {
    "be consistent with utf8Decode" in prop { (s: String, chunkSize: Int) =>
      (chunkSize > 0) ==> {
        val source = emitAll {
          s.getBytes(StandardCharsets.UTF_8)
            .grouped(chunkSize)
            .map(ByteVector.view)
            .toSeq
        }.toSource
        val utf8Decoded = (source |> utf8Decode).runLog.run
        val decoded = (source |> decode(Charset.`UTF-8`)).runLog.run
        decoded must_== utf8Decoded
      }
    }

    "be consistent with String constructor over aggregated output" in prop { (cs: Charset, s: String, chunkSize: Int) =>
      // x-COMPOUND_TEXT fails with a read only buffer.
      (chunkSize > 0 && cs.nioCharset.canEncode && cs.nioCharset.name != "x-COMPOUND_TEXT") ==> {
        val source = emitAll {
          s.getBytes(cs.nioCharset)
            .grouped(chunkSize)
            .map(ByteVector.view)
            .toSeq
        }.toSource
        val expected = source.foldMonoid
          .runLastOr(ByteVector.empty)
          .map(bs => new String(bs.toArray, cs.nioCharset))
          .run
        !expected.contains("\ufffd") ==> {
          // \ufffd means we generated a String unrepresentable by the charset
          val decoded = (source |> decode(cs)).foldMonoid.runLastOr("").run
          decoded must_== expected
        }
      }
    }
  }
}

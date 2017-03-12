package org.http4s
package util

import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.StandardCharsets

import fs2._
import fs2.text.utf8Decode
import org.scalacheck.Gen

class DecodeSpec extends Http4sSpec {
  /*
  0x8140 to 0xa0fe	Reserved for user-defined characters 造字
  0xa140 to 0xa3bf	"Graphical characters" 圖形碼
  0xa3c0 to 0xa3fe	Reserved, not for user-defined characters
  0xa440 to 0xc67e	Frequently used characters 常用字
  */
//  val big5CharGen: Gen[Char] = ???
  // Gen.infiniteStream(Gen.alphaChar).map(_.take(Int.MaxValue))

  "decode" should {
    "be consistent with utf8Decode" in prop { (s: String, chunkSize: Int) =>
      (chunkSize > 0) ==> {
        val source = Stream.emits {
          s.getBytes(StandardCharsets.UTF_8)
            .grouped(chunkSize)
            .map(_.toArray)
            .map(Chunk.bytes)
            .toSeq
        }.flatMap(Stream.chunk)
        val utf8Decoded = utf8Decode(source).toList.combineAll
        val decoded = decode(Charset.`UTF-8`)(source).toList.combineAll
        decoded must_== utf8Decoded
      }
    }

    "be consistent with String constructor over aggregated output" in prop { (cs: Charset, s: String, chunkSize: Int) =>
      // x-COMPOUND_TEXT fails with a read only buffer.
      (chunkSize > 0 && cs.nioCharset.canEncode && cs.nioCharset.name != "x-COMPOUND_TEXT") ==> {
        val source: Stream[Pure, Byte] = Stream.emits {
          s.getBytes(cs.nioCharset)
            .grouped(chunkSize)
            .map(Chunk.bytes)
            .toSeq
        }.flatMap(Stream.chunk).pure
        val expected = new String(source.toVector.toArray, cs.nioCharset)
        !expected.contains("\ufffd") ==> {
          // \ufffd means we generated a String unrepresentable by the charset
          val decoded = decode(cs)(source).toList.combineAll
          decoded must_== expected
        }
      }
    }


    "handle simple stuff" in {
      val inputString = "Hello"
      val charset = Charset.`UTF-8`.nioCharset
      val (input1, input2) = inputString.splitAt(2)
      println(s"inputs are '$input1', '$input2'")
      val inputBytes = inputString.getBytes(charset)
      val decoder = charset.newDecoder

      val cb1out = decoder.decode(ByteBuffer.wrap(input1.getBytes(charset)))
      val cb2out = decoder.decode(ByteBuffer.wrap(input2.getBytes(charset)))

      cb1out.toString must_== input1
      cb2out.toString must_== input2
    }

    "handle chunked bytes" in { //prop { (cs: Charset, s: String/*, chunkSize: Int*/) =>
      /*(
        cs.nioCharset.canEncode && cs.nioCharset.name != "x-COMPOUND_TEXT" //&&
        //chunkSize >= 1 && chunkSize <= (math.ceil(cs.nioCharset.newDecoder().maxCharsPerByte().toDouble) + 1)
      ) ==> {*/
//      prop { (chunkSize: Int) =>
        //val inputString = "Hello"
        //val cs = Charset.`UTF-16`
        /*
        val nioCharset = cs.nioCharset
        val inputBytes = s.getBytes(nioCharset)
        val source = Stream.emits(inputBytes)
        val decoded = decode(cs, chunkSize)(source).toList.combineAll
        decoded must_== new String(inputBytes, nioCharset)
        */
      val cs = Charset.fromNioCharset(java.nio.charset.Charset.forName("UTF-32"))
      val s = "the quick brown fox jumped over the lazy dog"
        println(s"charset $cs, max chars ${cs.nioCharset.newDecoder().maxCharsPerByte()}, avg chars ${cs.nioCharset.newDecoder().averageCharsPerByte()}")
      val strBytes = s.getBytes(cs.nioCharset)
      println(s"str bytes: ${strBytes.mkString(",")}")
      val source: Stream[Pure, Byte] = Stream.chunk(Chunk.bytes(strBytes)).pure
        val expected = new String(source.toVector.toArray, cs.nioCharset)
        !expected.contains("\ufffd") ==> {
          // \ufffd means we generated a String unrepresentable by the cs
          val decoded = decode(cs)(source).toList.combineAll
          decoded must_== expected
        }
      //}.setGen(Gen.chooseNum[Int](1, cs.nioCharset.newDecoder().maxCharsPerByte().toInt + 1))
      //}
    }.set(minTestsOk = 1)//.pretty1(charset => s"$charset (with max chars ${charset.nioCharset.newDecoder().maxCharsPerByte()})")
      //.set(maxDiscardRatio = 10)
      //.setGen1(Gen.const(Charset.fromString("UTF-32").right.get))
      //.setGen2("Hello")

    /*
    "handle" in prop { (chars: scala.Stream[Char], chunkSize: Int) =>
      val cs = Charset.fromString("Big5")
      val nioCharset = cs.nioCharset
      val source: Stream[Pure, Byte] = Stream.emits {
        nioCharset.encode(CharBuffer.wrap(chars.toArray)).array()
        //new String(chars.toArray).getBytes(cs.nioCharset)
          .grouped(chunkSize)
          .map(Chunk.bytes)
          .toSeq
      }.flatMap(Stream.chunk).pure
      val expected = new String(source.toVector.toArray, nioCharset)
      !expected.contains("\ufffd") ==> {
        // \ufffd means we generated a String unrepresentable by the charset
        val decoded = decode(cs)(source).toList.combineAll
        println(s"$cs $chunkSize results for: $decoded")
        decoded must_== expected
      }
    }.setGen1(big5CharGen).setGen2(Gen.posNum[Int])
    */
  }
}

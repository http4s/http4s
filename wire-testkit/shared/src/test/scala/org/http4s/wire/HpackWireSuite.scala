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

import cats.effect.IO
import cats.syntax.all._
import munit.CatsEffectSuite
import scodec.bits.ByteVector

class HpackWireSuite extends CatsEffectSuite {
  private def hex(value: String): ByteVector = ByteVector.fromValidHex(value)

  test("RFC 7541 C.1 integer encodings") {
    assertEquals(HpackWire.encodeInteger(10, 5), hex("0a"))
    assertEquals(HpackWire.encodeInteger(1337, 5), hex("1f9a0a"))
    assertEquals(HpackWire.encodeInteger(42, 8), hex("2a"))
  }

  private val request1 = Vector(
    ":method" -> "GET",
    ":scheme" -> "http",
    ":path" -> "/",
    ":authority" -> "www.example.com",
  )
  private val request2 = request1 :+ ("cache-control" -> "no-cache")
  private val request3 = Vector(
    ":method" -> "GET",
    ":scheme" -> "https",
    ":path" -> "/index.html",
    ":authority" -> "www.example.com",
    "custom-key" -> "custom-value",
  )

  private val response1 = Vector(
    ":status" -> "302",
    "cache-control" -> "private",
    "date" -> "Mon, 21 Oct 2013 20:13:21 GMT",
    "location" -> "https://www.example.com",
  )
  private val response2 = response1.updated(0, ":status" -> "307")
  private val response3 = Vector(
    ":status" -> "200",
    "cache-control" -> "private",
    "date" -> "Mon, 21 Oct 2013 20:13:22 GMT",
    "location" -> "https://www.example.com",
    "content-encoding" -> "gzip",
    "set-cookie" -> "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1",
  )

  // RFC 7541 Appendix C gives independent wire fixtures and expected header lists.
  private val requestPlain = Vector(
    "828684410f7777772e6578616d706c652e636f6d",
    "828684be58086e6f2d6361636865",
    "828785bf400a637573746f6d2d6b65790c637573746f6d2d76616c7565",
  )
  private val requestHuffman = Vector(
    "828684418cf1e3c2e5f23a6ba0ab90f4ff",
    "828684be5886a8eb10649cbf",
    "828785bf408825a849e95ba97d7f8925a849e95bb8e8b4bf",
  )
  private val responsePlain = Vector(
    "4803333032580770726976617465611d4d6f6e2c203231204f637420323031332032303a31333a323120474d54" +
      "6e1768747470733a2f2f7777772e6578616d706c652e636f6d",
    "4803333037c1c0bf",
    "88c1611d4d6f6e2c203231204f637420323031332032303a31333a323220474d54c05a04677a6970" +
      "7738666f6f3d4153444a4b48514b425a584f5157454f50495541585157454f49553b206d61782d6167653d" +
      "333630303b2076657273696f6e3d31",
  )
  private val responseHuffman = Vector(
    "488264025885aec3771a4b6196d07abe941054d444a8200595040b8166e082a62d1bff6e919d29ad171863" +
      "c78f0b97c8e9ae82ae43d3",
    "4883640effc1c0bf",
    "88c16196d07abe941054d444a8200595040b8166e084a62d1bffc05a839bd9ab77ad94e7821dd7f2e6c7" +
      "b335dfdfcd5b3960d5af27087f3672c1ab270fb5291f9587316065c003ed4ee5b1063d5007",
  )

  Vector("C.3" -> requestPlain, "C.4" -> requestHuffman).foreach { case (name, blocks) =>
    test(s"RFC 7541 $name stateful request sequence") {
      HpackWire.decoder[IO]().flatMap { decoder =>
        blocks
          .traverse(b => decoder.decode(hex(b)))
          .map(actual => assertEquals(actual, Vector(request1, request2, request3)))
      }
    }
  }

  Vector("C.5" -> responsePlain, "C.6" -> responseHuffman).foreach { case (name, blocks) =>
    test(s"RFC 7541 $name response sequence with table eviction") {
      HpackWire.decoder[IO](maxTableSize = 256).flatMap { decoder =>
        blocks
          .traverse(b => decoder.decode(hex(b)))
          .map(actual => assertEquals(actual, Vector(response1, response2, response3)))
      }
    }
  }

  test("literal encoder emits independently specified bytes and preserves duplicates") {
    val fields = Vector("a" -> "b", "a" -> "c")
    assertEquals(HpackWire.encodeLiteral(fields), hex("00016101620001610163"))
    HpackWire
      .decoder[IO]()
      .flatMap(_.decode(hex("00016101620001610163")))
      .map(actual => assertEquals(actual, fields))
  }

  test("non-indexed and never-indexed fields do not enter the dynamic table") {
    HpackWire.decoder[IO]().flatMap { decoder =>
      decoder.decode(hex("00016101621001610163")) *>
        decoder.decode(hex("be")).attempt.map(result => assert(result.isLeft))
    }
  }

  test("table reduction evicts entries even if capacity is restored in the same block") {
    HpackWire.decoder[IO]().flatMap { decoder =>
      decoder.decode(hex("4001610162")) *>
        decoder.decode(hex("20") ++ HpackWire.encodeInteger(4096, 5, 0x20)) *>
        decoder.decode(hex("be")).attempt.map(result => assert(result.isLeft))
    }
  }

  test("an entry larger than the table empties the table without rejecting the literal") {
    HpackWire.decoder[IO](maxTableSize = 34).flatMap { decoder =>
      for {
        _ <- decoder.decode(hex("4001610162"))
        fields <- decoder.decode(hex("400261610162"))
        indexed <- decoder.decode(hex("be")).attempt
      } yield {
        assertEquals(fields, Vector("aa" -> "b"))
        assert(indexed.isLeft)
      }
    }
  }

  Vector(
    "zero indexed entry" -> "80",
    "unknown dynamic entry" -> "be",
    "integer overflow" -> "ffffffffffff01",
    "truncated integer" -> "ff",
    "truncated literal" -> "000361",
    "table update after field" -> "8220",
    "table update above limit" -> "3fe21f",
    "Huffman EOS" -> "0084ffffffff00",
    "overlong Huffman padding" -> "0081ff00",
    "non-EOS Huffman padding" -> "00810000",
  ).foreach { case (name, bytes) =>
    test(s"rejects $name") {
      HpackWire
        .decoder[IO]()
        .flatMap(_.decode(hex(bytes)).attempt)
        .map(result => assert(result.isLeft))
    }
  }

  test("valid short Huffman padding and string bound") {
    assertEquals(HpackWire.decodeHuffman(hex("07"), 1), Http1Wire.ascii("0"))
    intercept[IllegalArgumentException](HpackWire.decodeHuffman(hex("07"), 0))
    assertEquals(HpackWire.decodeHuffman(ByteVector.empty, 0), ByteVector.empty)
  }

  test("header-list limit includes indexed fields") {
    HpackWire
      .decoder[IO](maxHeaderBytes = 41)
      .flatMap(_.decode(hex("8282")).attempt)
      .map(result => assert(result.isLeft))
  }
}

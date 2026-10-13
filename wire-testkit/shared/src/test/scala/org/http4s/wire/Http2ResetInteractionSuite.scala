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
import munit.CatsEffectSuite
import scodec.bits.ByteVector

class Http2ResetInteractionSuite extends CatsEffectSuite {
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  private val resetInteractionCases = Vector(
    "h2.server.interaction-reset-other-upload-stream",
    "h2.server.interaction-reset-retains-compression-context",
  )

  private def crossingResetFrames(kind: String, stream: Int): Vector[Http2Wire.Frame] = {
    val insertion = ByteVector.fromValidHex("4006782d72616365046b657074")
    val block = HpackWire.encodeLiteral(Vector(":status" -> "413")) ++ insertion
    kind match {
      case "none" => Vector.empty
      case "window" => Vector(Http2Wire.windowUpdate(stream, 1))
      case "priority" =>
        Vector(Http2Wire.Frame(Http2Wire.Priority, 0, stream, Http2Wire.uint32(0) ++ ByteVector(0)))
      case "reset" =>
        Vector(Http2Wire.Frame(Http2Wire.RstStream, 0, stream, Http2Wire.uint32(8)))
      case "headers" =>
        Vector(
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders | Http2Wire.EndStream,
            stream,
            block,
          )
        )
      case "continuation" =>
        Vector(
          Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndStream, stream, block.take(4)),
          Http2Wire.Frame(Http2Wire.Continuation, Http2Wire.EndHeaders, stream, block.drop(4)),
        )
      case "padded-data" =>
        Vector(
          Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndHeaders, stream, block),
          Http2Wire.Frame(
            Http2Wire.Data,
            Http2Wire.Padded | Http2Wire.EndStream,
            stream,
            ByteVector(2, 65, 0, 0),
          ),
        )
      case "trailers" =>
        Vector(
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders,
            stream,
            HpackWire.encodeLiteral(Vector(":status" -> "413")),
          ),
          Http2Wire.Frame(
            Http2Wire.Headers,
            Http2Wire.EndHeaders | Http2Wire.EndStream,
            stream,
            insertion,
          ),
        )
    }
  }

  private def resetInteraction(
      id: String,
      crossing: Vector[Http2Wire.Frame],
      timing: String = "early",
      indexedResponse: Boolean = false,
  ): IO[Unit] = ScriptedTarget.run(id) { (peer, publish) =>
    HpackWire.decoder[IO]().flatMap { decoder =>
      def emit: IO[Unit] =
        crossing.foldLeft(IO.unit)((send, frame) => send *> Http2Wire.writeFrame(peer, frame))
      def respond(path: String, body: ByteVector, fields: Vector[(String, String)]): IO[Unit] = {
        val block =
          if (indexedResponse) ByteVector(0x88.toByte, 0xbe.toByte)
          else HpackWire.encodeLiteral(Vector(":status" -> "200"))
        publish(
          RequestData(path = path, headers = fields.filterNot(_._1.startsWith(":")), body = body)
        ) *>
          Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(Http2Wire.Headers, Http2Wire.EndHeaders, 3, block),
          ) *>
          (if (timing == "after-head") emit else IO.unit) *>
          Http2Wire.writeFrame(peer, Http2Wire.data(3, body.take(3), endStream = false)) *>
          (if (timing == "after-prefix") emit else IO.unit) *>
          Http2Wire.writeFrame(peer, Http2Wire.data(3, body.drop(3), endStream = false)) *>
          (if (timing == "after-suffix") emit else IO.unit) *>
          Http2Wire.writeFrame(peer, Http2Wire.data(3, ByteVector.empty)) *>
          (if (timing == "after-end") emit else IO.unit) *>
          Http2Wire
            .readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Ping)
            .flatMap(ping => Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack)))
      }
      def loop(path: String, body: ByteVector, fields: Vector[(String, String)]): IO[Unit] =
        Http2Wire.readFrame(peer).flatMap { frame =>
          if (frame.frameType == Http2Wire.Headers)
            Http2Wire.readHeaders(peer, frame, decoder).flatMap { next =>
              if (frame.streamId == 1)
                (if (timing == "early") emit else IO.unit) *> loop(path, body, fields)
              else {
                val nextPath = next.collectFirst { case (":path", value) => value }.get
                if (frame.hasFlag(Http2Wire.EndStream)) respond(nextPath, body, next)
                else loop(nextPath, body, next)
              }
            }
          else if (frame.frameType == Http2Wire.Data && frame.streamId == 3) {
            val next = body ++ Http2Wire.unpad(frame)
            if (frame.hasFlag(Http2Wire.EndStream)) respond(path, next, fields)
            else loop(path, next, fields)
          } else loop(path, body, fields)
        }
      Http2Wire.serverHandshake(peer) *> loop("", ByteVector.empty, Vector.empty)
    }
  }

  for {
    id <- resetInteractionCases
    kind <- Vector(
      "none",
      "window",
      "priority",
      "reset",
      "headers",
      "continuation",
      "padded-data",
      "trailers",
    )
  } test(s"$id accepts queued $kind and preserves connection HPACK") {
    val indexed = Set("headers", "continuation", "padded-data", "trailers").contains(kind)
    resetInteraction(id, crossingResetFrames(kind, 1), indexedResponse = indexed)
  }

  for {
    id <- resetInteractionCases
    kind <- Vector("reset", "headers", "continuation", "padded-data", "trailers")
    timing <- Vector("after-head", "after-prefix", "after-suffix", "after-end")
  } test(s"$id admits $kind only before reset confirmation ($timing)") {
    val run = resetInteraction(id, crossingResetFrames(kind, 1), timing)
    if (
      id.endsWith("reset-other-upload-stream") && Set("after-head", "after-prefix").contains(timing)
    ) run
    else run.intercept[ProtocolFailure].void
  }

  for {
    id <- resetInteractionCases
    kind <- Vector("reset", "headers", "padded-data")
  } test(s"$id does not discard $kind on an unrelated stream") {
    resetInteraction(id, crossingResetFrames(kind, 5)).intercept[ProtocolFailure].void
  }

  for (id <- resetInteractionCases) {
    test(s"$id rejects crossing PUSH_PROMISE after initial disablement") {
      val promise = Http2Wire.Frame(
        Http2Wire.PushPromise,
        Http2Wire.EndHeaders,
        1,
        Http2Wire.uint32(2) ++ HpackWire.encodeLiteral(
          Vector(
            ":method" -> "GET",
            ":scheme" -> "http",
            ":authority" -> "wire.test",
            ":path" -> "/pushed",
          )
        ),
      )
      resetInteraction(id, Vector(promise))
        .intercept[ProtocolFailure]
        .map(error => assert(error.getMessage.contains("PUSH_PROMISE after push disablement")))
    }
    test(s"$id requires a valid shared HPACK entry after discarded headers") {
      resetInteraction(id, Vector.empty, indexedResponse = true).intercept[WireDecodingFailure].void
    }
    test(s"$id bounds discarded reset frames as incomplete observation") {
      resetInteraction(id, Vector.fill(1024)(crossingResetFrames("reset", 1).head))
        .intercept[WireObservationLimit]
        .void
    }
    for (count <- Vector(255, 256))
      test(s"$id counts discarded DATA padding in its finite observation budget ($count frames)") {
        val padded = Http2Wire.Frame(
          Http2Wire.Data,
          Http2Wire.Padded,
          1,
          ByteVector(255.toByte) ++ ByteVector.fill(255)(0),
        )
        // Exercise the volume boundary with a deterministic byte source. Socket scheduling
        // is exercised by the crossing controls above, independently of this 64-KiB budget.
        val upload = id.endsWith("reset-other-upload-stream")
        val body = if (upload) Http1Wire.ascii("following") else ByteVector.empty
        val response = Vector(
          Http2Wire.settings(),
          Http2Wire.settings(ack = true),
          Http2Wire.headers(1, Vector(":status" -> "413"), endStream = false),
        ) ++ Vector.fill(count)(padded) ++ Vector(
          Http2Wire.headers(3, Vector(":status" -> "200"), endStream = false),
          Http2Wire.data(3, body),
          Http2Wire
            .Frame(Http2Wire.Ping, Http2Wire.Ack, 0, ByteVector(41, 43, 47, 53, 59, 61, 67, 71)),
        )
        val run = ScriptedPeer.runServerBytes(
          id,
          response.foldLeft(ByteVector.empty)((bytes, frame) => bytes ++ Http2Wire.encode(frame)),
          IO.pure(None),
          IO.pure(
            RequestData(
              path = if (upload) "/two" else "/after",
              headers = Vector("x-state" -> "kept"),
              body = body,
            )
          ),
        )
        if (count == 255) run else run.intercept[WireObservationLimit].void
      }
    test(s"$id validates padding even on discarded DATA") {
      val padded = Http2Wire.Frame(Http2Wire.Data, Http2Wire.Padded, 1, ByteVector(2, 0))
      resetInteraction(id, Vector(padded)).intercept[WireDecodingFailure].void
    }
  }

}

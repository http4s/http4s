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

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import fs2.Stream
import fs2.text.utf8
import org.http4s.headers._
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class ServerSentEventSpec extends Http4sSuite {
  import ServerSentEvent._

  def toStream(s: String): Stream[IO, Byte] = {
    val scrubbed = s.dropWhile(_.isWhitespace)
    Stream.emit(scrubbed).through(utf8.encode)
  }

  test("decode should decode multi-line messages") {
    val stream = toStream("""
      |data: YHOO
      |data: +2
      |data: 10
      |""".stripMargin('|'))
    stream
      .through(ServerSentEvent.decoder)
      .compile
      .toVector
      .assertEquals(
        Vector(
          ServerSentEvent(data = "YHOO\n+2\n10".some)
        )
      )
  }

  test("a lone comment is a valid event") {
    val stream = toStream("""
      |: hello
      |""".stripMargin('|'))
    stream
      .through(ServerSentEvent.decoder)
      .compile
      .toVector
      .assertEquals(
        Vector(
          ServerSentEvent(comment = "hello".some)
        )
      )
  }

  test("decode should decode test stream") {
    val stream = toStream("""
      |: test stream
      |data: first event
      |id: 1
      |
      |data:second event
      |id
      |
      |data:  third event
      |""".stripMargin('|'))
    // test stream\n\ndata: first event\nid: 1\n\ndata:second event\nid\n\ndata:  third event\n")
    stream
      .through(ServerSentEvent.decoder)
      .compile
      .toVector
      .assertEquals(
        Vector(
          ServerSentEvent(
            data = "first event".some,
            id = Some(EventId("1")),
            comment = Some("test stream"),
          ),
          ServerSentEvent(data = "second event".some, id = Some(EventId.reset)),
          ServerSentEvent(data = " third event".some, id = None),
        )
      )
  }

  test("decode should fire empty events") {
    val stream = toStream("""
      |data
      |
      |data
      |data
      |
      |data:
      |""".stripMargin('|'))
    // test stream\n\ndata: first event\nid: 1\n\ndata:second event\nid\n\ndata:  third event\n")
    stream
      .through(ServerSentEvent.decoder)
      .compile
      .toVector
      .assertEquals(
        Vector(
          ServerSentEvent(data = "".some),
          ServerSentEvent(data = "\n".some),
          ServerSentEvent(data = "".some),
        )
      )
  }

  test("decode should ignore single space after colon") {
    val stream = toStream("""
      |data:test
      |
      |data: test
      |""".stripMargin('|'))
    // test stream\n\ndata: first event\nid: 1\n\ndata:second event\nid\n\ndata:  third event\n")
    stream
      .through(ServerSentEvent.decoder)
      .compile
      .toVector
      .assertEquals(
        Vector(
          ServerSentEvent(data = "test".some),
          ServerSentEvent(data = "test".some),
        )
      )
  }

  test("encode should be consistent with decode") {
    Prop.forAll { (sses: Vector[ServerSentEvent]) =>
      val roundTrip = Stream
        .emits(sses)
        .through(ServerSentEvent.encoder)
        .through(ServerSentEvent.decoder)
        .dropLast
        .toVector

      assertEquals(roundTrip, sses)
    }
  }

  test("encode should handle leading spaces") {
    // This is a pathological case uncovered by scalacheck
    val sse = ServerSentEvent(
      " a".some,
      Some(" b"),
      Some(EventId(" c")),
      Some(FiniteDuration(1, TimeUnit.MILLISECONDS)),
    )

    val roundTrip = Stream
      .emit(sse)
      .through(ServerSentEvent.encoder)
      .through(ServerSentEvent.decoder)
      .dropLast
      .compile
      .last

    assertEquals(roundTrip, Some(sse))
  }

  val eventStream: EventStream[IO] =
    Stream.range(0, 5).map(i => ServerSentEvent(data = i.toString.some))
  test("EntityEncoder[EventStream] should set Content-Type to text/event-stream") {
    assertEquals(
      Response[IO]().withEntity(eventStream).contentType,
      Some(`Content-Type`(MediaType.`text/event-stream`)),
    )
  }

  test("EntityEncoder[EventStream] should decode to original event stream") {
    for {
      r <- Response[IO]()
        .withEntity(eventStream)
        .body
        .through(ServerSentEvent.decoder)
        .dropLast
        .compile
        .toVector
      e <- eventStream.compile.toVector
    } yield assertEquals(r, e)
  }

}

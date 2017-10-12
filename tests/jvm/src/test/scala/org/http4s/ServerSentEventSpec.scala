package org.http4s

import cats.effect.IO
import fs2.Stream
import fs2.text.utf8Encode
import org.http4s.headers._

class ServerSentEventSpec extends Http4sSpec {
  import ServerSentEvent._

  def toStream(s: String): Stream[IO, Byte] =
    Stream.emit(s).through(utf8Encode)

  "decode" should {
    "decode multi-line messages" in {
      val stream = toStream("""
      |data: YHOO
      |data: +2
      |data: 10
      |""".stripMargin('|'))
      stream.through(ServerSentEvent.decoder).compile.toVector.unsafeRunSync must_== Vector(
        ServerSentEvent(data = "YHOO\n+2\n10")
      )
    }

    "decode test stream" in {
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
      //test stream\n\ndata: first event\nid: 1\n\ndata:second event\nid\n\ndata:  third event\n")
      stream.through(ServerSentEvent.decoder).compile.toVector.unsafeRunSync must_== Vector(
        ServerSentEvent(data = "first event", id = Some(EventId("1"))),
        ServerSentEvent(data = "second event", id = Some(EventId.reset)),
        ServerSentEvent(data = " third event", id = None)
      )
    }

    "fire empty events" in {
      val stream = toStream("""
      |data
      |
      |data
      |data
      |
      |data:
      |""".stripMargin('|'))
      //test stream\n\ndata: first event\nid: 1\n\ndata:second event\nid\n\ndata:  third event\n")
      stream.through(ServerSentEvent.decoder).compile.toVector.unsafeRunSync must_== Vector(
        ServerSentEvent(data = ""),
        ServerSentEvent(data = "\n"),
        ServerSentEvent(data = "")
      )
    }

    "ignore single space after colon" in {
      val stream = toStream("""
      |data:test
      |
      |data: test
      |""".stripMargin('|'))
      //test stream\n\ndata: first event\nid: 1\n\ndata:second event\nid\n\ndata:  third event\n")
      stream.through(ServerSentEvent.decoder).compile.toVector.unsafeRunSync must_== Vector(
        ServerSentEvent(data = "test"),
        ServerSentEvent(data = "test")
      )
    }
  }

  "encode" should {
    "be consistent with decode" in prop { sses: Vector[ServerSentEvent] =>
      val roundTrip = Stream
        .emits(sses)
        .covary[IO]
        .through(ServerSentEvent.encoder)
        .through(ServerSentEvent.decoder)
        .compile
        .toVector
        .unsafeRunSync
      roundTrip must_== sses
    }

    "handle leading spaces" in {
      // This is a pathological case uncovered by scalacheck
      val sse = ServerSentEvent(" a", Some(" b"), Some(EventId(" c")), Some(1L))
      Stream
        .emit(sse)
        .covary[IO]
        .through(ServerSentEvent.encoder)
        .through(ServerSentEvent.decoder)
        .compile
        .last
        .unsafeRunSync must beSome(sse)
    }
  }

  "EntityEncoder[ServerSentEvent]" should {
    val eventStream: Stream[IO, ServerSentEvent] =
      Stream.range(0, 5).map(i => ServerSentEvent(data = i.toString))
    "set Content-Type to text/event-stream" in {
      Response[IO]().withEntity(eventStream).contentType must beSome(
        `Content-Type`(MediaType.`text/event-stream`))
    }

    "decode to original event stream" in {
      val resp = Response[IO]().withEntity(eventStream)
      resp.body
        .through(ServerSentEvent.decoder)
        .compile
        .toVector
        .unsafeRunSync must_== eventStream.compile.toVector.unsafeRunSync
    }
  }
}

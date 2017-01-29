package org.http4s

import fs2.{Chunk, Stream, Task}
import org.http4s.headers._

class ServerSentEventSpec extends Http4sSpec {
  import ServerSentEvent._

  def toStream(s: String): Stream[Task, Byte] =
    Stream.emit[Task, String](s).through(fs2.text.utf8Encode)

  "decode" should {
    "decode multi-line messages" in {
      val stream = toStream("""
      |data: YHOO
      |data: +2
      |data: 10
      |""".stripMargin('|'))
      stream.through(ServerSentEvent.decoder).runLog.unsafeRun must_== Vector(
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
      stream.through(ServerSentEvent.decoder).runLog.unsafeRun must_== Vector(
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
      stream.through(ServerSentEvent.decoder).runLog.unsafeRun must_== Vector(
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
      stream.through(ServerSentEvent.decoder).runLog.unsafeRun must_== Vector(
        ServerSentEvent(data = "test"),
        ServerSentEvent(data = "test")
      )
    }
  }

  "encode" should {
    "be consistent with decode" in prop { sses: Vector[ServerSentEvent] =>
      val roundTrip = Stream.emits[Task, ServerSentEvent](sses)
        .through(ServerSentEvent.encoder)
        .through(ServerSentEvent.decoder)
        .runLog
        .unsafeRun
      roundTrip must_== sses
    }

    "handle leading spaces" in {
      // This is a pathological case uncovered by scalacheck
      val sse = ServerSentEvent(" a",Some(" b"),Some(EventId(" c")),Some(1L))
      Stream.emit[Task, ServerSentEvent](sse)
        .through(ServerSentEvent.encoder)
        .through(ServerSentEvent.decoder)
        .runLast
        .unsafeRun must beSome(sse)
    }
  }

  "EntityEncoder[ServerSentEvent]" should {
    val eventStream = Stream.range[Task](0, 5).map(i => ServerSentEvent(data = i.toString))
    "set Content-Type to text/event-stream" in {
      Response().withBody(eventStream).unsafeRun.contentType must beSome(`Content-Type`(MediaType.`text/event-stream`))
    }

    "decode to original event stream" in {
      val resp = Response().withBody(eventStream).unsafeRun
      resp.body.through(ServerSentEvent.decoder).runLog.unsafeRun must_== eventStream.runLog.unsafeRun
    }
  }
}

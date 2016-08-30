/* TODO fs2 port
package org.http4s

import org.http4s.headers._
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process.{emit, emitAll}
import scalaz.stream.text.utf8Encode
import scodec.bits.ByteVector

class ServerSentEventSpec extends Http4sSpec {
  import ServerSentEvent._

  def toStream(s: String): Process[Task, ByteVector] =
    emit(s).pipe(utf8Encode).toSource

  "decode" should {
    "decode multi-line messages" in {
      val stream = toStream("""
      |data: YHOO
      |data: +2
      |data: 10
      |""".stripMargin('|'))
      stream.pipe(ServerSentEvent.decoder).runLog.run must_== Vector(
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
      stream.pipe(ServerSentEvent.decoder).runLog.run must_== Vector(
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
      stream.pipe(ServerSentEvent.decoder).runLog.run must_== Vector(
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
      stream.pipe(ServerSentEvent.decoder).runLog.run must_== Vector(
        ServerSentEvent(data = "test"),
        ServerSentEvent(data = "test")
      )
    }
  }

  "encode" should {
    "be consistent with decode" in prop { sses: Vector[ServerSentEvent] =>
      val roundTrip = emitAll(sses).toSource
        .pipe(ServerSentEvent.encoder)
        .pipe(ServerSentEvent.decoder)
        .runLog
        .run
      roundTrip must_== sses
    }

    "handle leading spaces" in {
      // This is a pathological case uncovered by scalacheck
      val sse = ServerSentEvent(" a",Some(" b"),Some(EventId(" c")),Some(1L))
      emit(sse).toSource.pipe(ServerSentEvent.encoder).pipe(ServerSentEvent.decoder).runLast.run must beSome(sse)
    }
  }

  "EntityEncoder[ServerSentEvent]" should {
    val eventStream = Process.range(0, 5).map(i => ServerSentEvent(data = i.toString)).toSource
    "set Content-Type to text/event-stream" in {
      Response().withBody(eventStream).run.contentType must beSome(`Content-Type`(MediaType.`text/event-stream`))
    }

    "decode to original event stream" in {
      val resp = Response().withBody(eventStream).run
      resp.body.pipe(ServerSentEvent.decoder).runLog.run must_== eventStream.runLog.run
    }
  }
}
 */

package org.http4s

import org.specs2.mutable.Specification
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process.emit
import scalaz.stream.text.utf8Encode
import scodec.bits.ByteVector

class ServerSentEventSpec extends Specification {
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
        ServerSentEvent(data = "first event", lastEventId = "1"),
        ServerSentEvent(data = "second event"),
        ServerSentEvent(data = " third event")                
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
}

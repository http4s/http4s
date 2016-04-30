package org.http4s

import scala.util.Try
import scalaz.stream.Process._
import scalaz.stream.Process1
import scalaz.stream.text.utf8Decode

import scodec.bits.ByteVector

case class ServerSentEvent(
  data: String,
  eventType: String = "message",
  lastEventId: String = "",
  retry: Option[Long] = None
)

object ServerSentEvent {
  val empty = ServerSentEvent("")

  val decoder: Process1[ByteVector, ServerSentEvent] = {
    def splitLines(rest: String): Process1[String, String] =
      rest.split("""\r\n|\n|\r""", 2) match {
        case Array(head, tail) =>
          emit(head) ++ splitLines(tail)
        case Array(head) =>
          receive1Or[String, String](emit(rest)) { s => splitLines(rest + s) }
      }

    def go(dataBuffer: StringBuilder,
           eventTypeBuffer: String,
           lastEventId: String,
           retry: Option[Long]): Process1[String, ServerSentEvent] = {
      def dispatch =
        dataBuffer.toString match {
          case "" =>
            go(new StringBuilder, "", lastEventId, None)
          case s =>
            val data = if (s.endsWith("\n")) s.dropRight(1) else s
            val eventType = if (eventTypeBuffer == "") "message" else eventTypeBuffer
            val sse = ServerSentEvent(data, eventType, lastEventId, retry)
            emit(sse) ++ go(new StringBuilder, "", lastEventId, None)
        }

      def handleLine(field: String, value: String) =
        field match {
          case "event" =>
            go(dataBuffer, value, lastEventId, retry)
          case "data" =>
            go(dataBuffer.append(value).append("\n"), eventTypeBuffer, lastEventId, retry)
          case "id" =>
            go(dataBuffer, eventTypeBuffer, value, retry)
          case "retry" =>
            val newRetry = Try(value.toLong).toOption.orElse(retry)
            go(dataBuffer, eventTypeBuffer, lastEventId, newRetry)
          case field =>
            go(dataBuffer, eventTypeBuffer, lastEventId, retry)
        }

      await1[String].flatMap {
        case "" =>
          dispatch
        case s if s.startsWith(":") =>
          go(dataBuffer, eventTypeBuffer, lastEventId, retry)
        case s =>
          s.split(":\\s?", 2) match {
            case Array(field, value) =>
              handleLine(field, value)
            case Array(line) =>
              handleLine(line, "")
          }
      }
    }

    utf8Decode
      .pipe(splitLines(""))
      .pipe(suspend(go(new StringBuilder, "", "", None)))
  }
}

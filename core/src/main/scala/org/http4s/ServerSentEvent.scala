package org.http4s

import scala.util.Try
import scalaz.stream.Process._
import scalaz.stream.Process1
import scalaz.stream.text.{utf8Decode, utf8Encode}

import scodec.bits.ByteVector

import ServerSentEvent._
import util.{Renderable, Writer}

case class ServerSentEvent(
  data: String,
  eventType: Option[String] = None,
  id: EventId = NoEventId,
  retry: Option[Long] = None
) extends Renderable {
  def render(writer: Writer): writer.type = {
    writer << "data:" << data << "\n"
    eventType.foreach { writer << "event:" << _ << "\n" }
    id match {
      case NoEventId =>
      case SomeEventId(id) => writer << "id:" << id << "\n"
      case ResetEventId => writer << "id\n"
    }
    retry.foreach { writer << "retry:" << _ << "\n" }
    writer << "\n"
  }

  override def toString =
    s"ServerSentEvent($data,$eventType,$id,$retry)"
}

object ServerSentEvent {
  val empty = ServerSentEvent("")

  sealed trait EventId
  /** An explicit event ID. */
  case class SomeEventId(value: String) extends EventId
  /** An empty ID field, which resets the last event ID in an event source. */
  case object ResetEventId extends EventId
  /** A message that specifies no ID. */
  case object NoEventId extends EventId

  val decoder: Process1[ByteVector, ServerSentEvent] = {
    def splitLines(rest: String): Process1[String, String] =
      rest.split("""\r\n|\n|\r""", 2) match {
        case Array(head, tail) =>
          emit(head) ++ splitLines(tail)
        case Array(head) =>
          receive1Or[String, String](emit(rest)) { s => splitLines(rest + s) }
      }

    def go(dataBuffer: StringBuilder,
           eventType: Option[String],
           id: EventId,
           retry: Option[Long]): Process1[String, ServerSentEvent] = {
      def dispatch =
        dataBuffer.toString match {
          case "" =>
            go(new StringBuilder, None, NoEventId, None)
          case s =>
            val data = if (s.endsWith("\n")) s.dropRight(1) else s
            val sse = ServerSentEvent(data, eventType, id, retry)
            emit(sse) ++ go(new StringBuilder, None, NoEventId, None)
        }

      def handleLine(field: String, value: String) =
        field match {
          case "event" =>
            go(dataBuffer, Some(value), id, retry)
          case "data" =>
            go(dataBuffer.append(value).append("\n"), eventType, id, retry)
          case "id" =>
            val newId = value match {
              case "" => ResetEventId
              case s => SomeEventId(s)
            }
            go(dataBuffer, eventType, newId, retry)
          case "retry" =>
            val newRetry = Try(value.toLong).toOption.orElse(retry)
            go(dataBuffer, eventType, id, newRetry)
          case field =>
            go(dataBuffer, eventType, id, retry)
        }

      await1[String].flatMap {
        case "" =>
          dispatch
        case s if s.startsWith(":") =>
          go(dataBuffer, eventType, id, retry)
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
      .pipe(suspend(go(new StringBuilder, None, NoEventId, None)))
  }

  val encoder: Process1[ServerSentEvent, ByteVector] =
    await1[ServerSentEvent]
      .map(_.renderString)
      .repeat
      .pipe(utf8Encode)
}

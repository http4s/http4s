package org.http4s

import java.util.regex.Pattern

import scala.util.Try
import fs2.{Chunk, Handle, Pipe, Pull, Stream}
import fs2.text.{utf8Decode, utf8Encode}
import ServerSentEvent._
import util.{Renderable, Writer}

case class ServerSentEvent(
  data: String,
  eventType: Option[String] = None,
  id: Option[EventId] = None,
  retry: Option[Long] = None
) extends Renderable {
  def render(writer: Writer): writer.type = {
    writer << "data: " << data << "\n"
    eventType.foreach { writer << "event: " << _ << "\n" }
    id match {
      case None =>
      case Some(EventId.reset) => writer << "id\n"
      case Some(EventId(id)) => writer << "id: " << id << "\n"
    }
    retry.foreach { writer << "retry: " << _ << "\n" }
    writer << "\n"
  }

  override def toString =
    s"ServerSentEvent($data,$eventType,$id,$retry)"
}

object ServerSentEvent {
  val empty = ServerSentEvent("")

  case class EventId(value: String)

  object EventId {
    val reset: EventId = EventId("")
  }

  // Pattern instead of Regex, because Regex doesn't have split with limit. :(
  private val FieldSeparator =
    Pattern.compile(""": ?""")

  def decoder[F[_]]: Pipe[F, Byte, ServerSentEvent] =
    _.through(utf8Decode andThen fs2.text.lines).open.flatMap { h =>

    def go(dataBuffer: StringBuilder,
           eventType: Option[String],
           id: Option[EventId],
           retry: Option[Long],
           h: Handle[F, String]): Pull[F, ServerSentEvent, Nothing] = {

      def dispatch(h: Handle[F, String]): Pull[F, ServerSentEvent, Nothing] =
        dataBuffer.toString match {
          case "" =>
            // We just proved dataBuffer is empty, so we can reuse it
            go(dataBuffer, None, None, None, h)
          case s =>
            val data = if (s.endsWith("\n")) s.dropRight(1) else s
            val sse = ServerSentEvent(data, eventType, id, retry)
            Pull.output1(sse) >> go(new StringBuilder, None, None, None, h)
        }

      def handleLine(field: String, value: String, h: Handle[F, String]): Pull[F, ServerSentEvent, Nothing] =
        field match {
          case "event" =>
            go(dataBuffer, Some(value), id, retry, h)
          case "data" =>
            go(dataBuffer.append(value).append("\n"), eventType, id, retry, h)
          case "id" =>
            val newId = EventId(value)
            go(dataBuffer, eventType, Some(newId), retry, h)
          case "retry" =>
            val newRetry = Try(value.toLong).toOption.orElse(retry)
            go(dataBuffer, eventType, id, newRetry, h)
          case field =>
            go(dataBuffer, eventType, id, retry, h)
        }

      h.await1.flatMap {
        case ("", h) =>
          dispatch(h)
        case (s, h) if s.startsWith(":") =>
          go(dataBuffer, eventType, id, retry, h)
        case (s, h) =>
          FieldSeparator.split(s, 2) match {
            case Array(field, value) =>
              handleLine(field, value, h)
            case Array(line) =>
              handleLine(line, "", h)
          }
      }
    }

    go(new StringBuilder, None, None, None, h)
  }.close

  def encoder[F[_]]: Pipe[F, ServerSentEvent, Byte] =
    _.map(_.renderString).through(utf8Encode)

}

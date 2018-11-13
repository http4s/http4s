package org.http4s

import fs2._
import fs2.text.{utf8Decode, utf8Encode}
import java.util.regex.Pattern
import org.http4s.ServerSentEvent._
import org.http4s.util.{Renderable, Writer}
import scala.util.Try

final case class ServerSentEvent(
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

  override def toString: String =
    s"ServerSentEvent($data,$eventType,$id,$retry)"
}

object ServerSentEvent {
  val empty = ServerSentEvent("")

  final case class EventId(value: String)

  object EventId {
    val reset: EventId = EventId("")
  }

  // Pattern instead of Regex, because Regex doesn't have split with limit. :(
  private val FieldSeparator =
    Pattern.compile(""": ?""")

  def decoder[F[_]]: Pipe[F, Byte, ServerSentEvent] = {
    def go(
        dataBuffer: StringBuilder,
        eventType: Option[String],
        id: Option[EventId],
        retry: Option[Long],
        stream: Stream[F, String]): Pull[F, ServerSentEvent, Unit] = {

      //      def dispatch(h: Handle[F, String]): Pull[F, ServerSentEvent, Nothing] =
      def dispatch(stream: Stream[F, String]): Pull[F, ServerSentEvent, Unit] =
        dataBuffer.toString match {
          case "" =>
            // We just proved dataBuffer is empty, so we can reuse it
            go(dataBuffer, None, None, None, stream)
          case s =>
            val data = if (s.endsWith("\n")) s.dropRight(1) else s
            val sse = ServerSentEvent(data, eventType, id, retry)
            Pull.output1(sse) >> go(new StringBuilder, None, None, None, stream)
        }

      def handleLine(
          field: String,
          value: String,
          stream: Stream[F, String]): Pull[F, ServerSentEvent, Unit] =
        field match {
          case "event" =>
            go(dataBuffer, Some(value), id, retry, stream)
          case "data" =>
            go(dataBuffer.append(value).append("\n"), eventType, id, retry, stream)
          case "id" =>
            val newId = EventId(value)
            go(dataBuffer, eventType, Some(newId), retry, stream)
          case "retry" =>
            val newRetry = Try(value.toLong).toOption.orElse(retry)
            go(dataBuffer, eventType, id, newRetry, stream)
          case _ =>
            go(dataBuffer, eventType, id, retry, stream)
        }

      stream.pull.uncons1.flatMap {
        case None =>
          Pull.done
        case Some(("", stream)) =>
          dispatch(stream)
        case Some((s, stream)) if s.startsWith(":") =>
          go(dataBuffer, eventType, id, retry, stream)
        case Some((s, stream)) =>
          FieldSeparator.split(s, 2) match {
            case Array(field, value) =>
              handleLine(field, value, stream)
            case Array(line) =>
              handleLine(line, "", stream)
          }
      }
    }

    stream =>
      go(new StringBuilder, None, None, None, stream.through(utf8Decode.andThen(text.lines))).stream
  }

  def encoder[F[_]]: Pipe[F, ServerSentEvent, Byte] =
    _.map(_.renderString).through(utf8Encode)

}

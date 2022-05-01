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

import cats.data.Chain
import fs2._
import fs2.text.utf8
import org.http4s.ServerSentEvent._
import org.http4s.util.Renderable
import org.http4s.util.Writer

import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

final case class ServerSentEvent(
    data: Option[String] = None,
    eventType: Option[String] = None,
    id: Option[EventId] = None,
    retry: Option[FiniteDuration] = None,
    comment: Option[String] = None,
) extends Renderable {
  def render(writer: Writer): writer.type = {
    data.foreach(writer << "data: " << _ << "\n")
    comment.foreach(writer << ": " << _ << "\n")
    eventType.foreach(writer << "event: " << _ << "\n")
    id match {
      case None =>
      case Some(EventId.reset) => writer << "id\n"
      case Some(EventId(id)) => writer << "id: " << id << "\n"
    }
    retry.foreach(writer << "retry: " << _.toMillis << "\n")
    writer << "\n"
  }

  override def toString: String =
    s"ServerSentEvent($data,$eventType,$id,$retry)"
}

object ServerSentEvent {
  val empty: ServerSentEvent = ServerSentEvent()

  final case class EventId(value: String)

  object EventId {
    val reset: EventId = EventId("")
  }

  // Pattern instead of Regex, because Regex doesn't have split with limit. :(
  private val FieldSeparator =
    Pattern.compile(""": ?""")

  def decoder[F[_]]: Pipe[F, Byte, ServerSentEvent] = {
    case class LineBuffer(lines: Chain[String] = Chain.empty) {
      def append(line: String): LineBuffer =
        // val scrubbed = if (line.endsWith("\n")) line.dropRight(1) else line
        copy(lines = lines :+ line)
      def reify: Option[String] =
        if (lines.nonEmpty) {
          Some(lines.iterator.mkString("\n"))
        } else
          None
    }
    val emptyBuffer = LineBuffer()
    def go(
        dataBuffer: LineBuffer,
        eventType: Option[String],
        id: Option[EventId],
        retry: Option[Long],
        commentBuffer: LineBuffer,
        stream: Stream[F, String],
    ): Pull[F, ServerSentEvent, Unit] = {
      //      def dispatch(h: Handle[F, String]): Pull[F, ServerSentEvent, Nothing] =
      def dispatch(stream: Stream[F, String]): Pull[F, ServerSentEvent, Unit] = {
        val sse = ServerSentEvent(
          dataBuffer.reify,
          eventType,
          id,
          retry.map(FiniteDuration(_, TimeUnit.MILLISECONDS)),
          commentBuffer.reify,
        )
        Pull.output1(sse) >> go(emptyBuffer, None, None, None, emptyBuffer, stream)
      }

      def handleLine(
          field: String,
          value: String,
          stream: Stream[F, String],
      ): Pull[F, ServerSentEvent, Unit] =
        field match {
          case "" =>
            go(dataBuffer, eventType, id, retry, commentBuffer.append(value), stream)
          case "event" =>
            go(dataBuffer, Some(value), id, retry, commentBuffer, stream)
          case "data" =>
            go(dataBuffer.append(value), eventType, id, retry, commentBuffer, stream)
          case "id" =>
            val newId = EventId(value)
            go(dataBuffer, eventType, Some(newId), retry, commentBuffer, stream)
          case "retry" =>
            val newRetry = Try(value.toLong).toOption.orElse(retry)
            go(dataBuffer, eventType, id, newRetry, commentBuffer, stream)
          case _ =>
            go(dataBuffer, eventType, id, retry, commentBuffer, stream)
        }

      stream.pull.uncons1.flatMap {
        case None =>
          Pull.done
        case Some(("", stream)) =>
          dispatch(stream)
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
      go(
        emptyBuffer,
        None,
        None,
        None,
        emptyBuffer,
        stream.through(utf8.decode.andThen(text.lines)),
      ).stream
  }

  def encoder[F[_]]: Pipe[F, ServerSentEvent, Byte] =
    _.map(_.renderString).through(utf8.encode)
}

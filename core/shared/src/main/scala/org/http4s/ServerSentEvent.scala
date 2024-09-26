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

  def decoder[F[_]]: Pipe[F, Byte, ServerSentEvent] = {
    case class LineBuffer(lines: Chain[String] = Chain.empty) {
      def append(line: String): LineBuffer =
        copy(lines = lines :+ line)
      def reify: Option[String] =
        lines.sizeCompare(1) match {
          case c if c < 0 => None
          case 0 => lines.headOption
          case _ => Some(lines.iterator.mkString("\n"))
        }
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

      stream.pull.uncons1.flatMap {
        case None =>
          Pull.done
        case Some(("", stream)) =>
          dispatch(stream)
        case Some((s, stream)) =>
          if (s.startsWith("data:")) {
            go(dataBuffer.append(dropPrefix(s, 5)), eventType, id, retry, commentBuffer, stream)
          } else if (s.startsWith("id:")) {
            go(dataBuffer, eventType, Some(EventId(dropPrefix(s, 3))), retry, commentBuffer, stream)
          } else if (s.startsWith("event:")) {
            go(dataBuffer, Some(dropPrefix(s, 6)), id, retry, commentBuffer, stream)
          } else if (s.startsWith("retry:")) {
            val newRetry = Try(dropPrefix(s, 6).toLong).toOption.orElse(retry)
            go(dataBuffer, eventType, id, newRetry, commentBuffer, stream)
          } else if (s.startsWith(":")) {
            go(dataBuffer, eventType, id, retry, commentBuffer.append(dropPrefix(s, 1)), stream)
          } else
            s match {
              case "data" => go(dataBuffer.append(""), eventType, id, retry, commentBuffer, stream)
              case "id" =>
                go(dataBuffer, eventType, Some(EventId("")), retry, commentBuffer, stream)
              case "event" => go(dataBuffer, Some(""), id, retry, commentBuffer, stream)
              case _ => go(dataBuffer, eventType, id, retry, commentBuffer, stream)
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

  @inline private def dropPrefix(s: String, n: Int): String = {
    val l = s.length
    if (l > n && s(n) == ' ') {
      s.slice(n + 1, l)
    } else {
      s.slice(n, l)
    }
  }

  def encoder[F[_]]: Pipe[F, ServerSentEvent, Byte] =
    _.map(_.renderString).through(utf8.encode)
}

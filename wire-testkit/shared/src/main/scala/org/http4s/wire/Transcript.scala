/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all._
import scodec.bits.ByteVector

/** A bounded prefix of the exchanged bytes, for assertion diagnostics. */
final class Transcript[F[_]] private (
    state: Ref[F, Transcript.State],
    maxBytes: Int,
)(implicit F: Sync[F]) {
  def record(direction: String, bytes: ByteVector): F[Unit] =
    state.update { current =>
      if (bytes.isEmpty) current
      else {
        val available = if (current.events.size < 256) maxBytes - current.retained else 0
        val kept = ByteVector.view(bytes.take(available.toLong).toArray)
        Transcript.State(
          if (kept.isEmpty) current.events else current.events :+ (direction.take(16) -> kept),
          current.retained + kept.size.toInt,
          current.dropped + bytes.size - kept.size,
        )
      }
    }

  def render: F[String] = state.get.map { current =>
    val lines = current.events.map { case (direction, bytes) =>
      val printable = bytes.toArray.iterator.map { b =>
        val n = b & 0xff
        if (n >= 32 && n < 127) n.toChar else '.'
      }.mkString
      s"$direction ${bytes.toHex} |$printable|"
    }
    val omitted =
      if (current.dropped > 0) Vector(s"[${current.dropped} bytes omitted]") else Vector.empty
    (lines ++ omitted).mkString("\n")
  }
}

object Transcript {
  private final case class State(
      events: Vector[(String, ByteVector)],
      retained: Int,
      dropped: Long,
  )

  def create[F[_]: Sync](maxBytes: Int = 65536): F[Transcript[F]] =
    if (maxBytes < 0) Sync[F].raiseError(new IllegalArgumentException("negative transcript limit"))
    else Ref.of[F, State](State(Vector.empty, 0, 0L)).map(new Transcript[F](_, maxBytes))
}

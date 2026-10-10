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

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path

/** JSON evidence plus a deliberately simple, independently reconciled manifest. */
final class WireReport(target: WireTarget[IO], suite: String, comparison: Boolean) {
  private val mode = if (comparison) "comparison" else "normal"
  private val directory = Path(TckBuild.reportRoot) / TckBuild.scalaVersion /
    mode / RuntimeInfo.platform / suite

  private def write(name: String, contents: String): IO[Unit] =
    Stream
      .emit(contents)
      .through(fs2.text.utf8.encode)
      .through(Files[IO].writeAll(directory / name))
      .compile
      .drain

  def initialize(cases: Vector[WireCase]): IO[Unit] =
    Files[IO].createDirectories(directory) *>
      write("manifest.tsv", cases.map(c => s"${c.id}\t${target.name}").mkString("", "\n", "\n")) *>
      cases.traverse_ { c =>
        Files[IO].deleteIfExists(directory / s"${c.id}.outcome").void
      } *> Files[IO].deleteIfExists(directory / "complete").void

  def complete: IO[Unit] = write("complete", "suite finalizer completed\n")

  def record(
      c: WireCase,
      outcome: String,
      elapsedMillis: Long,
      detail: String,
      transcript: String,
  ): IO[Unit] = {
    val traceFile = s"${c.id}.wire.txt"
    val fields = Vector(
      "case" -> c.id,
      "outcome" -> outcome,
      "target" -> target.name,
      "targetVersion" -> target.version,
      "platform" -> RuntimeInfo.platform,
      "runtime" -> RuntimeInfo.runtime,
      "scalaVersion" -> TckBuild.scalaVersion,
      "catalogRevision" -> TckBuild.revision,
      "protocol" -> c.protocol.id,
      "role" -> c.role.id,
      "source" -> s"${c.location.path}:${c.location.line}",
      "settings" -> "plaintext; prior-knowledge h2; isolated loopback; case deadline 8s; trace cap 64KiB",
      "elapsedMillis" -> elapsedMillis.toString,
      "detail" -> detail,
      "transcript" -> (directory / traceFile).toString,
    ).map { case (key, value) => s"${WireReport.quote(key)}:${WireReport.quote(value)}" }
    val refs = c.references.map(r =>
      s"""{"url":${WireReport.quote(r.url)},"requirement":${WireReport.quote(
          r.requirement
        )},"strength":${WireReport.quote(r.strength)}}"""
    )
    write(traceFile, transcript) *>
      write(
        s"${c.id}.json",
        (fields :+ s""""references":[${refs.mkString(",")}]""")
          .mkString("{", ",", "}\n"),
      ) *>
      write(s"${c.id}.outcome", s"${c.id}\t${target.name}\t$outcome\n")
  }
}

object WireReport {
  private[wire] def quote(value: String): String = {
    val escaped = value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' => f"\\u${c.toInt}%04x"
      case c => c.toString
    }
    "\"" + escaped + "\""
  }
}

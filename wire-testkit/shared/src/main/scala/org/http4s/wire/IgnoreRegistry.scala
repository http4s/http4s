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

final case class IgnoredCase(
    id: String,
    platforms: Set[String],
    observed: String,
    revision: String,
    status: String,
    tracking: String,
)

/** The only source of ignores. Full comparison suites never consult this registry. */
object IgnoreRegistry {
  val http1: Vector[IgnoredCase] = Vector(
    IgnoredCase(
      id = "h1.server.missing-host",
      platforms = Set("jvm", "js", "native"),
      observed = "Missing Host reaches the application and returns 200 instead of required 400.",
      revision =
        "7ae5b1ea14c6df3f9b6b00ba5a74b2ff7adcde2d:94a49f23066fca48842cef98008d3d09439b013a3727b068dbeef9c07f68d50b",
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h1-server-missing-host",
    )
  )
  val http2: Vector[IgnoredCase] = Vector.empty
  val all: Vector[IgnoredCase] = http1 ++ http2

  def get(id: String, platform: String): Option[IgnoredCase] =
    all.find(e => e.id == id && e.platforms(platform))

  def validate(cases: Vector[WireCase]): Vector[String] = {
    val ids = cases.map(_.id)
    val duplicateCases = ids
      .groupBy(identity)
      .collect {
        case (id, xs) if xs.size > 1 =>
          s"duplicate case: $id"
      }
      .toVector
    val duplicateIgnores = all
      .groupBy(_.id)
      .collect {
        case (id, xs) if xs.size > 1 =>
          s"duplicate ignore: $id"
      }
      .toVector
    duplicateCases ++ duplicateIgnores ++ all.flatMap { e =>
      Vector(
        if (!ids.contains(e.id)) Some(s"stale ignore: ${e.id}") else None,
        if (e.platforms.isEmpty || !e.platforms.subsetOf(Set("jvm", "js", "native")))
          Some(s"invalid platforms: ${e.id}")
        else None,
        if (Vector(e.observed, e.revision, e.status, e.tracking).exists(_.trim.isEmpty))
          Some(s"incomplete ignore: ${e.id}")
        else None,
        if (!Set("provisional", "confirmed")(e.status))
          Some(s"invalid review status: ${e.id}")
        else None,
        if (!e.tracking.startsWith("docs/wire-tck/known-failures.md#"))
          Some(s"missing local tracking reference: ${e.id}")
        else None,
      ).flatten
    } ++ http1.filterNot(_.id.startsWith("h1.")).map(e => s"wrong registry section: ${e.id}") ++
      http2.filterNot(_.id.startsWith("h2.")).map(e => s"wrong registry section: ${e.id}") ++ cases
        .collect {
          case c if c.references.isEmpty || c.references.exists(_.requirement.trim.isEmpty) =>
            s"missing specification reference: ${c.id}"
        }
  }
}

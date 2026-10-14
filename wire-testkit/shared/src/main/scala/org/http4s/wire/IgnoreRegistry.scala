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
  private val validPlatforms = Set("jvm", "js", "native")

  private[wire] def parse(row: String): IgnoredCase =
    row.split("\t", -1) match {
      case Array(id, platforms, observed, revision, status, tracking) =>
        IgnoredCase(id, platforms.split(",", -1).toSet, observed, revision, status, tracking)
      case _ => throw new IllegalArgumentException(s"ignore row must have six columns: $row")
    }

  val all: Vector[IgnoredCase] = TckBuild.ignoreRowsBase64.toVector.map { encoded =>
    val row = scodec.bits.ByteVector
      .fromBase64Descriptive(encoded)
      .flatMap(_.decodeUtf8.left.map(_.getMessage))
      .fold(sys.error, identity)
    parse(row)
  }

  val http1: Vector[IgnoredCase] = all.filter(_.id.startsWith("h1."))
  val http2: Vector[IgnoredCase] = all.filter(_.id.startsWith("h2."))

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
        if (!e.id.startsWith("h1.") && !e.id.startsWith("h2."))
          Some(s"unknown ignore protocol: ${e.id}")
        else None,
        if (!ids.contains(e.id)) Some(s"stale ignore: ${e.id}") else None,
        if (e.platforms.isEmpty || !e.platforms.subsetOf(validPlatforms))
          Some(s"invalid platforms: ${e.id}")
        else None,
        if (Vector(e.observed, e.revision, e.status, e.tracking).exists(_.trim.isEmpty))
          Some(s"incomplete ignore: ${e.id}")
        else None,
        if (e.status != "provisional" && e.status != "confirmed")
          Some(s"invalid review status: ${e.id}")
        else None,
        if (!e.tracking.startsWith("docs/wire-tck/known-failures.md#"))
          Some(s"missing local tracking reference: ${e.id}")
        else None,
      ).flatten
    } ++ cases
      .collect {
        case c if c.references.isEmpty || c.references.exists(_.requirement.trim.isEmpty) =>
          s"missing specification reference: ${c.id}"
      }
  }
}

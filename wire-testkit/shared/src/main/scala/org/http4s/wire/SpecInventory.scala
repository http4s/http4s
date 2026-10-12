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

import scodec.bits.ByteVector

/** The requirements ledger is authored separately from executable discovery. */
object SpecInventory {
  final case class Entry(
      id: String,
      rfc: String,
      section: String,
      role: String,
      strength: String,
      requirement: String,
      disposition: String,
      cases: Vector[String],
      notes: String,
  )

  private def when[A](condition: Boolean)(value: => A): Option[A] =
    if (condition) Some(value) else None

  def parse(row: String): Either[String, Entry] =
    row.split("\t", -1).toVector match {
      case Vector(id, rfc, section, role, strength, requirement, disposition, cases, notes) =>
        Right(
          Entry(
            id,
            rfc,
            section,
            role,
            strength,
            requirement,
            disposition,
            cases.split(",").toVector.filter(_.nonEmpty),
            notes,
          )
        )
      case _ => Left(s"inventory row must have nine columns: $row")
    }

  def validate(
      rows: Seq[String],
      sections: Seq[String],
      cases: Vector[WireCase],
  ): Vector[String] = {
    val parsed = rows.map(parse)
    val entries = parsed.collect { case Right(value) => value }.toVector
    val classified = sections.map(_.split("\t", -1).toVector)
    val sectionKeys = classified.collect { case Vector(rfc, section, _, _, _) =>
      (rfc, section)
    }.toSet
    val ids = cases.map(_.id).toSet
    val mapped = entries.flatMap(_.cases).toSet
    parsed.collect { case Left(error) => error }.toVector ++
      entries
        .groupBy(_.id)
        .collect {
          case (id, values) if values.size > 1 =>
            s"duplicate inventory id: $id"
        }
        .toVector ++
      classified.collect {
        case row
            if row.size != 5 ||
              !Set("applicable", "dependency", "informative", "excluded")(row(3)) || row(
                4
              ).trim.isEmpty =>
          s"invalid section classification: ${row.mkString(" / ")}"
      }.toVector ++
      classified
        .groupBy(_.take(2))
        .collect {
          case (key, values) if values.size > 1 =>
            s"duplicate section classification: $key"
        }
        .toVector ++
      entries.flatMap { entry =>
        val requiredRoles = entry.role match {
          case "both" => Set("client", "server")
          case "client" | "server" => Set(entry.role)
          case _ => Set.empty[String]
        }
        val coveredRoles = cases.filter(c => entry.cases.contains(c.id)).map(_.role.id).toSet
        val missingRoles =
          if (entry.disposition == "covered") requiredRoles.diff(coveredRoles)
          else Set.empty[String]
        missingRoles.toVector.sorted.map(role =>
          s"coverage without $role role evidence: ${entry.id}"
        ) ++ Vector(
          when(entry.id.isEmpty || entry.requirement.trim.isEmpty)(
            s"empty obligation: ${entry.id}"
          ),
          when(!sectionKeys((entry.rfc, entry.section)))(s"unclassified RFC section: ${entry.id}"),
          when(!Set("client", "server", "both", "na")(entry.role))(
            s"invalid inventory role: ${entry.id}"
          ),
          when(
            !Set(
              "MUST",
              "MUST NOT",
              "MUSTNOT",
              "SHOULD",
              "SHOULD NOT",
              "MAY",
              "syntax",
              "informative",
            )(entry.strength)
          )(s"invalid requirement strength: ${entry.id}"),
          when(!Set("covered", "gap", "excluded")(entry.disposition))(
            s"invalid disposition: ${entry.id}"
          ),
          when(entry.disposition == "covered" && entry.cases.isEmpty)(
            s"coverage without cases: ${entry.id}"
          ),
          when(entry.disposition != "covered" && entry.notes.trim.isEmpty)(
            s"unexplained gap or exclusion: ${entry.id}"
          ),
        ).flatten ++ entry.cases
          .filterNot(ids)
          .map(id => s"inventory references missing case: ${entry.id}: $id")
      } ++
      ids.diff(mapped).toVector.sorted.map(id => s"case missing from independent inventory: $id") ++
      classified.collect {
        case Vector(rfc, section, _, kind, _)
            if Set("applicable", "dependency")(kind) && !entries.exists(e =>
              e.rfc == rfc &&
                (e.section == section || e.section.startsWith(section + "."))
            ) =>
          s"section has no obligations or explicit gap: RFC$rfc $section"
      }.toVector
  }

  private def decodeRows(rows: Seq[String]): Seq[String] =
    rows.map(row => ByteVector.fromValidBase64(row).decodeUtf8.fold(throw _, identity))

  lazy val problems: Vector[String] =
    validate(
      decodeRows(TckBuild.inventoryRowsBase64),
      decodeRows(TckBuild.sectionRowsBase64),
      Catalog.all,
    )
}

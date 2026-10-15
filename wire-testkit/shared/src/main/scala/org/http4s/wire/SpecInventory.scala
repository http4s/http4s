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

import SpecSections.Section

/** The requirements ledger is authored separately from executable discovery. */
object SpecInventory {
  private val sectionKinds = Set("applicable", "dependency", "informative", "excluded")
  private val roles = Set("client", "server", "both", "na")
  private val strengths =
    Set("MUST", "MUST NOT", "MUSTNOT", "SHOULD", "SHOULD NOT", "MAY", "syntax", "informative")
  final case class Entry(
      id: String,
      rfc: String,
      section: String,
      role: String,
      strength: String,
      requirement: String,
      disposition: String,
      cases: Vector[WireCase],
      notes: String,
  )

  private def when[A](condition: Boolean)(value: => A): Option[A] =
    if (condition) Some(value) else None

  val all: Vector[Entry] =
    DependenciesInventory.all ++
      GrammarDependenciesInventory.all ++
      H1FramingInventory.all ++
      H1GrammarInventory.all ++
      H1LifecycleInventory.all ++
      H1SyntaxInventory.all ++
      H2ControlInventory.all ++
      H2MessagesInventory.all ++
      H2StatesInventory.all ++
      HpackInventory.all ++
      InteractionsInventory.all ++
      SmokeInventory.all ++
      ScopeBoundariesInventory.all ++
      SectionGapsInventory.all

  def validate(
      entries: Vector[Entry],
      sections: Vector[Section],
      cases: Vector[WireCase],
  ): Vector[String] = {
    val sectionKeys = sections.map(s => (s.rfc, s.section)).toSet
    val rolesById = cases.groupBy(_.id).map { case (id, values) =>
      id -> values.iterator.map(_.role.id).toSet
    }
    val ids = rolesById.keySet
    val mapped = entries.flatMap(_.cases.map(_.id)).toSet
    entries
      .groupBy(_.id)
      .collect {
        case (id, values) if values.size > 1 =>
          s"duplicate inventory id: $id"
      }
      .toVector ++
      sections.collect {
        case section if !sectionKinds(section.classification) || section.reason.trim.isEmpty =>
          s"invalid section classification: $section"
      } ++
      sections
        .groupBy(s => (s.rfc, s.section))
        .collect {
          case (key, values) if values.size > 1 => s"duplicate section classification: $key"
        }
        .toVector ++
      entries.flatMap { entry =>
        val requiredRoles = entry.role match {
          case "both" => Set("client", "server")
          case "client" | "server" => Set(entry.role)
          case _ => Set.empty[String]
        }
        val missingRoles =
          if (entry.disposition == "covered") {
            val coveredRoles =
              entry.cases.iterator.flatMap(c => rolesById.getOrElse(c.id, Set.empty[String])).toSet
            requiredRoles.diff(coveredRoles)
          } else Set.empty[String]
        missingRoles.toVector.sorted.map(role =>
          s"coverage without $role role evidence: ${entry.id}"
        ) ++ Vector(
          when(entry.id.isEmpty || entry.requirement.trim.isEmpty)(
            s"empty obligation: ${entry.id}"
          ),
          when(!sectionKeys((entry.rfc, entry.section)))(s"unclassified RFC section: ${entry.id}"),
          when(!roles(entry.role))(
            s"invalid inventory role: ${entry.id}"
          ),
          when(!strengths(entry.strength))(s"invalid requirement strength: ${entry.id}"),
          when(
            entry.disposition != "covered" && entry.disposition != "gap" && entry.disposition != "excluded"
          )(
            s"invalid disposition: ${entry.id}"
          ),
          when(entry.disposition == "covered" && entry.cases.isEmpty)(
            s"coverage without cases: ${entry.id}"
          ),
          when(entry.disposition != "covered" && entry.notes.trim.isEmpty)(
            s"unexplained gap or exclusion: ${entry.id}"
          ),
        ).flatten ++ entry.cases
          .filterNot(cases.contains)
          .map(id => s"inventory references missing case: ${entry.id}: ${id.id}")
      } ++
      ids.diff(mapped).toVector.sorted.map(id => s"case missing from independent inventory: $id") ++
      sections.collect {
        case section
            if (section.classification == "applicable" || section.classification == "dependency") &&
              !entries.exists(e =>
                e.rfc == section.rfc &&
                  (e.section == section.section || e.section.startsWith(section.section + "."))
              ) =>
          s"section has no obligations or explicit gap: RFC${section.rfc} ${section.section}"
      }
  }

  lazy val problems: Vector[String] = validate(all, SpecSections.all, Catalog.all)
}

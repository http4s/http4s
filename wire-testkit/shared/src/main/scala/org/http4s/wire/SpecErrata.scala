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

/** RFC Editor snapshot from 2026-10-10. See docs/wire-tck/baseline.md for provenance. */
object SpecErrata {
  final case class Erratum(
      rfc: String,
      id: String,
      status: String,
      section: String,
      classification: String,
      url: String,
  )

  val all: Set[Erratum] = Set(
    Erratum(
      "7541",
      "4574",
      "Held for Document Update",
      "Appendix A.",
      "Not incorporated: Held for Document Update",
      "https://www.rfc-editor.org/errata/eid4574",
    ),
    Erratum(
      "7541",
      "5094",
      "Held for Document Update",
      "5.1",
      "Not incorporated: Held for Document Update",
      "https://www.rfc-editor.org/errata/eid5094",
    ),
    Erratum(
      "9110",
      "7105",
      "Verified",
      "B.1.",
      "Excluded: TLS certificate identity",
      "https://www.rfc-editor.org/errata/eid7105",
    ),
    Erratum(
      "9110",
      "7107",
      "Rejected",
      "6.5.1",
      "Not incorporated: Rejected",
      "https://www.rfc-editor.org/errata/eid7107",
    ),
    Erratum(
      "9110",
      "7109",
      "Verified",
      "15.4.9",
      "Excluded: redirect semantics",
      "https://www.rfc-editor.org/errata/eid7109",
    ),
    Erratum(
      "9110",
      "7138",
      "Verified",
      "12.5.1",
      "Excluded: media negotiation",
      "https://www.rfc-editor.org/errata/eid7138",
    ),
    Erratum(
      "9110",
      "7306",
      "Verified",
      "14.1.1",
      "Excluded: range semantics",
      "https://www.rfc-editor.org/errata/eid7306",
    ),
    Erratum(
      "9110",
      "7419",
      "Verified",
      "8.3.2",
      "Excluded: charset semantics",
      "https://www.rfc-editor.org/errata/eid7419",
    ),
    Erratum(
      "9110",
      "7530",
      "Rejected",
      "15.5.2.",
      "Not incorporated: Rejected",
      "https://www.rfc-editor.org/errata/eid7530",
    ),
    Erratum(
      "9110",
      "7599",
      "Rejected",
      "6.4.1",
      "Not incorporated: Rejected",
      "https://www.rfc-editor.org/errata/eid7599",
    ),
    Erratum(
      "9110",
      "7870",
      "Rejected",
      "8.6",
      "Not incorporated: Rejected",
      "https://www.rfc-editor.org/errata/eid7870",
    ),
    Erratum(
      "9110",
      "8138",
      "Rejected",
      "15.4",
      "Not incorporated: Rejected",
      "https://www.rfc-editor.org/errata/eid8138",
    ),
    Erratum(
      "9110",
      "8268",
      "Verified",
      "B.2",
      "Incorporated: editorial history relocated to RFC9112",
      "https://www.rfc-editor.org/errata/eid8268",
    ),
    Erratum(
      "9110",
      "8459",
      "Rejected",
      "15",
      "Not incorporated: Rejected",
      "https://www.rfc-editor.org/errata/eid8459",
    ),
    Erratum(
      "9110",
      "9162",
      "Reported",
      "5.2",
      "Not incorporated: Reported",
      "https://www.rfc-editor.org/errata/eid9162",
    ),
    Erratum(
      "9110",
      "9164",
      "Reported",
      "Appendix A",
      "Not incorporated: Reported",
      "https://www.rfc-editor.org/errata/eid9164",
    ),
    Erratum(
      "9112",
      "7214",
      "Rejected",
      "1.2",
      "Not incorporated: Rejected",
      "https://www.rfc-editor.org/errata/eid7214",
    ),
    Erratum(
      "9112",
      "7633",
      "Rejected",
      "2.2",
      "Not incorporated: Rejected",
      "https://www.rfc-editor.org/errata/eid7633",
    ),
    Erratum(
      "9112",
      "7744",
      "Verified",
      "1.2",
      "Incorporated: obs-text reference corrected to RFC9110 section5.5",
      "https://www.rfc-editor.org/errata/eid7744",
    ),
    Erratum(
      "9112",
      "8284",
      "Verified",
      "C.3",
      "Incorporated: editorial history of absolute-form Host precedence",
      "https://www.rfc-editor.org/errata/eid8284",
    ),
    Erratum(
      "9113",
      "7013",
      "Verified",
      "6.1",
      "Incorporated: zero-length DATA wording",
      "https://www.rfc-editor.org/errata/eid7013",
    ),
    Erratum(
      "9113",
      "9175",
      "Reported",
      "8.3.1",
      "Not incorporated: Reported",
      "https://www.rfc-editor.org/errata/eid9175",
    ),
  )
}

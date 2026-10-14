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

import munit.FunSuite

class CatalogSuite extends FunSuite {
  private val caseIdPattern = "h[12]\\.(client|server)\\.[a-z0-9-]+".r

  override def munitFlakyOK: Boolean = false

  test("catalog and registry integrity") {
    assertEquals(IgnoreRegistry.validate(Catalog.all), Vector.empty)
    Catalog.all.foreach { c =>
      assert(caseIdPattern.pattern.matcher(c.id).matches(), c.id)
      assert(c.id.startsWith(s"${c.protocol.id}.${c.role.id}."), c.id)
      assert(
        c.references.forall(r =>
          (r.number == 9110 || r.number == 9112 || r.number == 9113 || r.number == 7541) && r.section.nonEmpty
        ),
        c.id,
      )
    }
  }

  test("independent requirements inventory reconciles with executable discovery") {
    assertEquals(SpecInventory.problems, Vector.empty)
  }

  test("inventory rejects unsupported coverage and unexplained omissions") {
    val section = Vector("9112\t2.1\tMessage Format\tapplicable\twire framing")
    val row = "test\t9112\t2.1\tserver\tMUST\tA complete request\tcovered\tmissing-case\t"
    val errors = SpecInventory.validate(Vector(row), section, Vector.empty)
    assert(errors.exists(_.contains("missing case")))
    assert(
      SpecInventory.validate(Vector.empty, section, Vector.empty).exists(_.contains("explicit gap"))
    )
    assert(SpecInventory.parse("invalid").isLeft)
  }

  test("covered inventory roles require evidence for every claimed role") {
    val section = Vector("9112\t2.1\tMessage Format\tapplicable\twire framing")
    val client = Catalog.all.find(_.role == Role.Client).get
    val server = Catalog.all.find(_.role == Role.Server).get
    def validate(role: String, cases: Vector[WireCase]): Vector[String] =
      SpecInventory.validate(
        Vector(
          s"roles\t9112\t2.1\t$role\tMUST\tFraming\tcovered\t${cases.map(_.id).mkString(",")}\t"
        ),
        section,
        cases,
      )
    assertEquals(validate("client", Vector(client)), Vector.empty)
    assertEquals(validate("server", Vector(server)), Vector.empty)
    assertEquals(validate("both", Vector(client, server)), Vector.empty)
    // Duplicate IDs must retain all role evidence, even though registry validation rejects them.
    assertEquals(validate("both", Vector(client, server.copy(id = client.id))), Vector.empty)
    assert(validate("both", Vector(client)).exists(_.contains("server role")))
    assert(validate("both", Vector(server)).exists(_.contains("client role")))
    assert(validate("server", Vector(client)).exists(_.contains("server role")))
    assert(validate("client", Vector(server)).exists(_.contains("client role")))
  }

  test("registration preserves each definition's source location") {
    Catalog.all.foreach { c =>
      assert(c.location.path.endsWith("Cases.scala"), c.location.toString)
      assert(c.location.line > 20, c.location.toString)
      assert(!c.location.path.endsWith("WireSuite.scala"))
    }
  }

  test("report JSON escaping preserves control characters") {
    assertEquals(WireReport.quote("a\nb\\c\""), "\"a\\nb\\\\c\\\"\"")
  }
}

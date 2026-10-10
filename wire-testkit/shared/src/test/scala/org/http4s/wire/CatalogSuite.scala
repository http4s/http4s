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
  override def munitFlakyOK: Boolean = false

  test("catalog and registry integrity") {
    assertEquals(IgnoreRegistry.validate(Catalog.all), Vector.empty)
    Catalog.all.foreach { c =>
      assert(c.id.matches("h[12]\\.(client|server)\\.[a-z0-9-]+"), c.id)
      assert(c.id.startsWith(s"${c.protocol.id}.${c.role.id}."), c.id)
      assert(
        c.references.forall(r => Set(9110, 9112, 9113, 7541)(r.number) && r.section.nonEmpty),
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

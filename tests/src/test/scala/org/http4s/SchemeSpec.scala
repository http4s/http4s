package org.http4s

import cats.kernel.laws.OrderLaws
import org.http4s.util.StringWriter

class SchemeSpec extends Http4sSpec {
  "equals" should {
    "be consistent with equalsIgnoreCase of the values" in {
      prop { (a: Scheme, b: Scheme) =>
        (a == b) == a.value.equalsIgnoreCase(b.value)
      }
    }
  }

  "compareTo" should {
    "be consistent with compareToIgnoreCase" in {
      prop { (a: Scheme, b: Scheme) =>
        a.value.compareToIgnoreCase(b.value) == a.compareTo(b)
      }
    }
  }

  "hashCode" should {
    "be consistent with equality" in {
      prop { (a: Scheme, b: Scheme) =>
        (a == b) ==> (a.## == b.##)
      }
    }
  }

  "render" should {
    "return value" in {
      prop { s: Scheme =>
        val w = new StringWriter
        (w << s).result.toString must_== s.value
      }
    }
  }

  checkAll("order", OrderLaws[Scheme].order)
}

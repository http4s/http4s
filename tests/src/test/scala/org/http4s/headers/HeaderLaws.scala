package org.http4s
package headers

import org.scalacheck.Arbitrary
import org.typelevel.discipline.Laws

trait HeaderLaws extends Http4sSpec with Laws {
  def headerLaws(key: HeaderKey)(implicit arbHeader: Arbitrary[key.HeaderT]): RuleSet =
    new SimpleRuleSet(
      "header",
      """parse(a.value) == right(a)"""" -> prop { (a: key.HeaderT) =>
        key.parse(a.value) must beRight(a)
      },
      """renderString == "name: value"""" -> prop { (a: key.HeaderT) =>
        a.renderString must_== s"${key.name}: ${a.value}"
      },
      """matchHeader matches parsed values""" -> prop { (a: key.HeaderT) =>
        key.matchHeader(a) must beSome(a)
      },
      """matchHeader matches raw, valid values of same name""" -> prop { (a: key.HeaderT) =>
        key.matchHeader(a.toRaw) must beSome(a)
      },
      """matchHeader does not match other names""" -> prop { (header: Header) =>
        key.name != header.name ==> { key.matchHeader(header) must beNone }
      }
    )
}

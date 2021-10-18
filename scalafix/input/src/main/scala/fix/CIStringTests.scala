/*
rule = v0_22
*/

import org.http4s.util.CaseInsensitiveString

class CIStringTests {
  val s = "bar"
  val foo: CaseInsensitiveString = CaseInsensitiveString(s)
  val baz = foo.value
}

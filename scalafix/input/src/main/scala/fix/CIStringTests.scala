/*
rule = v0_22
*/

import org.http4s.util.CaseInsensitiveString
import org.http4s.syntax.all._

class CIStringTests {
  val s = "bar"
  val foo: CaseInsensitiveString = CaseInsensitiveString(s)
  foo.value
  "hi".ci
  s.ci
}

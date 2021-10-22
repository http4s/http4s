/*
rule = v0_22
*/

import org.http4s.{Header, Headers}
import org.http4s.headers.{AgentProduct, `User-Agent`}

object HeaderTests {
  val k = "k"
  val foo: Header = Header("key", "value")
  val bar = Header(k, "value")

  bar match {
    case Header(_, v) => v
  }

  Headers.of(foo, bar)

  `User-Agent`(AgentProduct("scalafix"))
}

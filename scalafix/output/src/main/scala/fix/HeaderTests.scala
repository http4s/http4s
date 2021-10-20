import org.http4s.{Header, Headers}
import org.typelevel.ci._

object HeaderTests {
  val k = "k"
  val foo: Header.Raw = Header.Raw(ci"key", "value")
  val bar = Header.Raw(CIString(k), "value")

  bar match {
    case Header.Raw(_, v) => v
  }

  Headers(foo, bar)
}

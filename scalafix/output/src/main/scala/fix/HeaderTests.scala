import org.http4s.{Header, Headers}
import org.http4s.headers.`User-Agent`
import org.http4s.ProductId
import org.typelevel.ci._

object HeaderTests {
  val k = "k"
  val foo: Header.Raw = Header.Raw(ci"key", "value")
  val bar = Header.Raw(CIString(k), "value")

  bar match {
    case Header.Raw(_, v) => v
  }

  Headers(foo, bar)

  `User-Agent`(ProductId("scalafix"))
}

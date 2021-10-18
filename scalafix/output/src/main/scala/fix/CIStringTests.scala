import org.http4s.syntax.all._
import org.typelevel.ci.{ CIString, _ }

class CIStringTests {
  val s = "bar"
  val foo: CIString = CIString(s)
  foo.toString
  ci"hi"
  CIString(s)
}

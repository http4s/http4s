import org.typelevel.ci.CIString

class CIStringTests {
  val s = "bar"
  val foo: CIString = CIString(s)
  val baz = foo.toString
}
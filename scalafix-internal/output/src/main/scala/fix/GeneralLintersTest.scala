case object Foo

case class Bar()
final case class Baz()

sealed trait Animal
class Dog extends Animal

sealed abstract class Plant
trait Tree extends Plant

package pkg {
  final case class Foo private[pkg](v: String)
}
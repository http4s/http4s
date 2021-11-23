/*
rule = Http4sGeneralLinters
*/

final case object Foo

case class Bar()// assert: Http4sGeneralLinters.noCaseClassWithoutAccessModifier
final case class Baz()

sealed trait Animal
class Dog extends Animal// assert: Http4sGeneralLinters.leakingSealedHierarchy

sealed abstract class Plant
trait Tree extends Plant// assert: Http4sGeneralLinters.leakingSealedHierarchy

package pkg {
  final case class Foo private[pkg](v: String)// assert: Http4sGeneralLinters.nonValidatingCopyConstructor
}
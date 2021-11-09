/*
rule = Http4sGeneralLinters
*/

final case object Foo

case class Bar()// assert: Http4sGeneralLinters.noCaseClassWithoutAccessModifier
final case class Baz()

sealed trait Animal
class Dog extends Animal// assert: Http4sGeneralLinters.leakingSealedHierachy

sealed abstract class Plant
trait Tree extends Plant// assert: Http4sGeneralLinters.leakingSealedHierachy
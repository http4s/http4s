/*
rule = Http4sGeneralLinters
 */

final case object Foo

case class Bar() // assert: Http4sGeneralLinters.noCaseClassWithoutAccessModifier
final case class Baz()
